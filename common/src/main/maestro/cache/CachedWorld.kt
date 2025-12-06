package maestro.cache

import com.google.common.cache.CacheBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import maestro.Agent
import maestro.api.utils.Loggers
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.dimension.DimensionType
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = Loggers.Cache.get()

class CachedWorld internal constructor(
    directory: Path,
    private val dimension: DimensionType,
) {
    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO +
                CoroutineName("CachedWorld"),
        )

    private val cachedRegions = ConcurrentHashMap<Long, CachedRegion>()
    private val directory: String

    private val packingChannel = Channel<ChunkPos>(Channel.UNLIMITED)

    private val toPackMap: MutableMap<ChunkPos, LevelChunk> =
        CacheBuilder
            .newBuilder()
            .softValues()
            .build<ChunkPos, LevelChunk>()
            .asMap()

    private val pruningTrigger = MutableSharedFlow<Unit>()

    init {
        if (!directory.exists()) {
            directory.createDirectories()
        }
        this.directory = directory.toString()

        startPackerWorker()
        startAutosaveWorker()
        startPruningWorker()
    }

    private fun startPackerWorker() {
        scope.launch {
            packingChannel
                .consumeAsFlow()
                .collect { pos ->
                    try {
                        val chunk = toPackMap.remove(pos) ?: return@collect

                        if (toPackMap.size >
                            Agent
                                .getPrimaryAgent()
                                .settings.chunkPackerQueueMaxSize.value
                        ) {
                            return@collect
                        }

                        val cached = ChunkPacker.pack(chunk)
                        updateCachedChunk(cached)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (th: Throwable) {
                        log
                            .atError()
                            .setCause(th)
                            .addKeyValue("chunk_x", pos.x)
                            .addKeyValue("chunk_z", pos.z)
                            .log("Failed to pack chunk")
                    }
                }
        }
    }

    private fun startAutosaveWorker() {
        scope.launch {
            delay(30.seconds)
            while (isActive) {
                delay(10.minutes)
                save()
            }
        }
    }

    private fun startPruningWorker() {
        scope.launch {
            merge(
                pruningTrigger.asSharedFlow(),
                flow {
                    while (isActive) {
                        delay(2.minutes)
                        emit(Unit)
                    }
                },
            ).collect {
                prune()
            }
        }
    }

    fun queueForPacking(chunk: LevelChunk) {
        if (toPackMap.put(chunk.pos, chunk) == null) {
            packingChannel.trySend(chunk.pos)
        }
    }

    fun isCached(
        blockX: Int,
        blockZ: Int,
    ): Boolean {
        val region = getRegion(blockX shr 9, blockZ shr 9) ?: return false
        return region.isCached(blockX and 511, blockZ and 511)
    }

    fun regionLoaded(
        blockX: Int,
        blockZ: Int,
    ): Boolean = getRegion(blockX shr 9, blockZ shr 9) != null

    fun getLocationsOf(
        block: String,
        maximum: Int,
        centerX: Int,
        centerZ: Int,
        maxRegionDistanceSq: Int,
    ): ArrayList<BlockPos> {
        val res = ArrayList<BlockPos>()
        val centerRegionX = centerX shr 9
        val centerRegionZ = centerZ shr 9

        var searchRadius = 0
        while (searchRadius <= maxRegionDistanceSq) {
            for (xOff in -searchRadius..searchRadius) {
                for (zOff in -searchRadius..searchRadius) {
                    val distance = xOff * xOff + zOff * zOff
                    if (distance != searchRadius) {
                        continue
                    }
                    val regionX = xOff + centerRegionX
                    val regionZ = zOff + centerRegionZ
                    val region = getOrCreateRegion(regionX, regionZ)
                    if (region != null) {
                        res.addAll(region.getLocationsOf(block))
                    }
                }
            }
            if (res.size >= maximum) {
                return res
            }
            searchRadius++
        }
        return res
    }

    private fun updateCachedChunk(chunk: CachedChunk) {
        val region = getOrCreateRegion(chunk.x shr 5, chunk.z shr 5)
        region?.updateCachedChunk(chunk.x and 31, chunk.z and 31, chunk)
    }

    fun save() {
        if (!Agent
                .getPrimaryAgent()
                .settings.chunkCaching.value
        ) {
            allRegions().forEach { region ->
                region.removeExpired()
            }
            prune()
            return
        }

        System.nanoTime() / 1000000L
        allRegions()
            .parallelStream()
            .forEach { region ->
                region.save(directory)
            }
        System.nanoTime() / 1000000L

        prune()
        scope.launch {
            pruningTrigger.emit(Unit)
        }
    }

    private fun prune() {
        if (!Agent
                .getPrimaryAgent()
                .settings.pruneRegionsFromRAM.value
        ) {
            return
        }

        val pruneCenter = guessPosition()
        for (region in allRegions()) {
            val distX = ((region.x shl 9) + 256) - pruneCenter.x
            val distZ = ((region.z shl 9) + 256) - pruneCenter.z
            val dist = kotlin.math.sqrt((distX * distX + distZ * distZ).toDouble())
            if (dist > 1024) {
                log
                    .atDebug()
                    .addKeyValue("region_x", region.x)
                    .addKeyValue("region_z", region.z)
                    .addKeyValue("distance", dist.toInt())
                    .log("Region pruned from RAM")
                cachedRegions.remove(getRegionID(region.x, region.z))
            }
        }
    }

    private fun guessPosition(): BlockPos {
        for (maestro in Agent.getAllAgents()) {
            val data = maestro.worldProvider.currentWorld
            if (data?.getCachedWorld() == this && maestro.playerContext.player() != null) {
                return maestro.playerContext.playerFeet().toBlockPos()
            }
        }

        var mostRecentlyModified: CachedChunk? = null
        for (region in allRegions()) {
            val ch = region.mostRecentlyModified() ?: continue
            if (mostRecentlyModified == null ||
                mostRecentlyModified.cacheTimestamp < ch.cacheTimestamp
            ) {
                mostRecentlyModified = ch
            }
        }

        return mostRecentlyModified?.let {
            BlockPos((it.x shl 4) + 8, 0, (it.z shl 4) + 8)
        } ?: BlockPos(0, 0, 0)
    }

    private fun allRegions(): List<CachedRegion> = cachedRegions.values.toList()

    fun reloadAllFromDisk() {
        System.nanoTime() / 1000000L
        allRegions().forEach { region ->
            region.load(directory)
        }
        System.nanoTime() / 1000000L
    }

    fun getRegion(
        regionX: Int,
        regionZ: Int,
    ): CachedRegion? = cachedRegions[getRegionID(regionX, regionZ)]

    private fun getOrCreateRegion(
        regionX: Int,
        regionZ: Int,
    ): CachedRegion? {
        val regionId = getRegionID(regionX, regionZ)
        if (regionId == 0L) return null

        return cachedRegions.computeIfAbsent(regionId) { _ ->
            CachedRegion(regionX, regionZ, dimension).also { newRegion ->
                newRegion.load(directory)
            }
        }
    }

    fun tryLoadFromDisk(
        regionX: Int,
        regionZ: Int,
    ) {
        getOrCreateRegion(regionX, regionZ)
    }

    private fun getRegionID(
        regionX: Int,
        regionZ: Int,
    ): Long {
        if (!isRegionInWorld(regionX, regionZ)) {
            return 0
        }
        return regionX.toLong() and 0xFFFFFFFFL or
            ((regionZ.toLong() and 0xFFFFFFFFL) shl 32)
    }

    private fun isRegionInWorld(
        regionX: Int,
        regionZ: Int,
    ): Boolean = regionX in -REGION_MAX..REGION_MAX && regionZ in -REGION_MAX..REGION_MAX

    fun close() {
        scope.cancel()
    }

    companion object {
        private const val REGION_MAX = 30_000_000 / 512 + 1
    }
}
