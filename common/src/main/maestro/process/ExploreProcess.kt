package maestro.process

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import maestro.Agent
import maestro.api.cache.ICachedWorld
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.goals.GoalComposite
import maestro.api.pathing.goals.GoalXZ
import maestro.api.pathing.goals.GoalYLevel
import maestro.api.process.IExploreProcess
import maestro.api.process.PathingCommand
import maestro.api.process.PathingCommandType
import maestro.api.utils.MaestroLogger
import maestro.api.utils.MyChunkPos
import maestro.cache.CachedWorld
import maestro.utils.MaestroProcessHelper
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import org.slf4j.Logger
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.min

class ExploreProcess(
    maestro: Agent,
) : MaestroProcessHelper(maestro),
    IExploreProcess {
    private var explorationOrigin: BlockPos? = null
    private var filter: IChunkFilter? = null
    private var distanceCompleted = 0

    override fun isActive(): Boolean = explorationOrigin != null

    override fun explore(
        centerX: Int,
        centerZ: Int,
    ) {
        explorationOrigin = BlockPos(centerX, 0, centerZ)
        distanceCompleted = 0
    }

    override fun applyJsonFilter(
        path: Path,
        invert: Boolean,
    ) {
        filter = JsonChunkFilter(path, invert)
    }

    internal fun calcFilter(): IChunkFilter {
        val currentFilter = filter
        return if (currentFilter != null) {
            EitherChunk(currentFilter, MaestroChunkCache())
        } else {
            MaestroChunkCache()
        }
    }

    override fun onTick(
        calcFailed: Boolean,
        isSafeToCancel: Boolean,
    ): PathingCommand? {
        if (calcFailed) {
            log.atWarn().addKeyValue("reason", "path_calculation_failed").log("Exploration failed")
            if (Agent.settings().notificationOnExploreFinished.value) {
                logNotification("Exploration failed", true)
            }
            onLostControl()
            return null
        }

        val filter = calcFilter()
        if (!Agent.settings().disableCompletionCheck.value && filter.countRemain() == 0) {
            log.atInfo().log("Exploration complete - all chunks explored")
            if (Agent.settings().notificationOnExploreFinished.value) {
                logNotification("Explored all chunks", false)
            }
            onLostControl()
            return null
        }

        val origin = explorationOrigin ?: return null
        val closestUncached =
            closestUncachedChunks(origin, filter) ?: run {
                log.atDebug().log("Awaiting region load from disk")
                return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
            }

        return PathingCommand(
            GoalComposite(*closestUncached),
            PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH,
        )
    }

    private fun closestUncachedChunks(
        center: BlockPos,
        filter: IChunkFilter,
    ): Array<Goal>? {
        val chunkX = center.x shr 4
        val chunkZ = center.z shr 4
        var count = min(filter.countRemain(), Agent.settings().exploreChunkSetMinimumSize.value)
        val centers = mutableListOf<BlockPos>()
        val renderDistance = Agent.settings().worldExploringChunkOffset.value

        var dist = distanceCompleted
        while (true) {
            for (dx in -dist..dist) {
                val zval = dist - abs(dx)
                for (mult in 0 until 2) {
                    val dz = (mult * 2 - 1) * zval // dz can be either -zval or zval
                    val trueDist = abs(dx) + abs(dz)

                    if (trueDist != dist) {
                        throw IllegalStateException(
                            "Offset $dx $dz has distance $trueDist, expected $dist",
                        )
                    }

                    when (filter.isAlreadyExplored(chunkX + dx, chunkZ + dz)) {
                        Status.UNKNOWN -> return null // Awaiting load
                        Status.NOT_EXPLORED -> {} // Note: this breaks the when not the for
                        Status.EXPLORED -> continue // Note: this continues the for
                    }

                    var centerX = ((chunkX + dx) shl 4) + 8
                    var centerZ = ((chunkZ + dz) shl 4) + 8
                    val offset = renderDistance shl 4

                    if (dx < 0) {
                        centerX -= offset
                    } else {
                        centerX += offset
                    }

                    if (dz < 0) {
                        centerZ -= offset
                    } else {
                        centerZ += offset
                    }

                    centers.add(BlockPos(centerX, 0, centerZ))
                }
            }

            if (dist % 10 == 0) {
                count = min(filter.countRemain(), Agent.settings().exploreChunkSetMinimumSize.value)
            }

            if (centers.size >= count) {
                return centers.map { pos -> createGoal(pos.x, pos.z) }.toTypedArray()
            }

            if (centers.isEmpty()) {
                // We have explored everything from 0 to dist inclusive
                // Next time we should start our check at dist+1
                distanceCompleted = dist + 1
            }

            dist++
        }
    }

    override fun onLostControl() {
        explorationOrigin = null
    }

    override fun displayName0(): String {
        val origin = explorationOrigin ?: return "Exploring (inactive)"
        val filter = calcFilter()
        val uncached = closestUncachedChunks(origin, filter)
        return "Exploring around $origin, distance completed $distanceCompleted, currently going to ${GoalComposite(
            *
                uncached ?: emptyArray(),
        )}"
    }

    // Sealed class for status
    enum class Status {
        EXPLORED,
        NOT_EXPLORED,
        UNKNOWN,
    }

    // Nested interface
    internal interface IChunkFilter {
        fun isAlreadyExplored(
            chunkX: Int,
            chunkZ: Int,
        ): Status

        fun countRemain(): Int
    }

    // Inner classes
    private inner class MaestroChunkCache : IChunkFilter {
        private val cache: ICachedWorld? = maestro.worldProvider.currentWorld?.cachedWorld

        override fun isAlreadyExplored(
            chunkX: Int,
            chunkZ: Int,
        ): Status {
            val centerX = chunkX shl 4
            val centerZ = chunkZ shl 4

            if (cache?.isCached(centerX, centerZ) == true) {
                return Status.EXPLORED
            }

            val cachedWorld = cache as? CachedWorld ?: return Status.UNKNOWN
            if (!cachedWorld.regionLoaded(centerX, centerZ)) {
                Agent.getExecutor().execute {
                    cachedWorld.tryLoadFromDisk(centerX shr 9, centerZ shr 9)
                }
                return Status.UNKNOWN // We still need to load regions from disk in order to decide properly
            }

            return Status.NOT_EXPLORED
        }

        override fun countRemain(): Int = Int.MAX_VALUE
    }

    private inner class JsonChunkFilter(
        path: Path,
        private val invert: Boolean,
    ) : IChunkFilter {
        // If true, the list is interpreted as a list of chunks that are NOT explored
        // If false, the list is interpreted as a list of chunks that ARE explored
        private val inFilter: LongOpenHashSet
        private val positions: Array<MyChunkPos>

        init {
            val gson: Gson = GsonBuilder().create()
            positions =
                gson.fromJson(
                    InputStreamReader(Files.newInputStream(path)),
                    Array<MyChunkPos>::class.java,
                )
            log
                .atInfo()
                .addKeyValue("position_count", positions.size)
                .log("Loaded exploration filter positions")

            inFilter = LongOpenHashSet()
            for (mcp in positions) {
                inFilter.add(ChunkPos.asLong(mcp.x, mcp.z))
            }
        }

        override fun isAlreadyExplored(
            chunkX: Int,
            chunkZ: Int,
        ): Status =
            if (inFilter.contains(ChunkPos.asLong(chunkX, chunkZ)) xor invert) {
                // Either it's on the list of explored chunks, or it's not on the list of unexplored chunks
                // Either way, we have it
                Status.EXPLORED
            } else {
                // Either it's not on the list of explored chunks, or it's on the list of unexplored chunks
                // Either way, it depends on if maestro has cached it so defer to that
                Status.UNKNOWN
            }

        override fun countRemain(): Int {
            if (!invert) {
                // If invert is false, anything not on the list is uncached
                return Int.MAX_VALUE
            }

            // But if invert is true, anything not on the list IS assumed cached
            // So we are done if everything on our list is cached!
            var countRemain = 0
            val bcc = MaestroChunkCache()

            for (pos in positions) {
                if (bcc.isAlreadyExplored(pos.x, pos.z) != Status.EXPLORED) {
                    // Either waiting for it or don't have it at all
                    countRemain++
                    if (countRemain >= Agent.settings().exploreChunkSetMinimumSize.value) {
                        return countRemain
                    }
                }
            }

            return countRemain
        }
    }

    // Nested (not inner) class
    private class EitherChunk(
        private val a: IChunkFilter,
        private val b: IChunkFilter,
    ) : IChunkFilter {
        override fun isAlreadyExplored(
            chunkX: Int,
            chunkZ: Int,
        ): Status {
            if (a.isAlreadyExplored(chunkX, chunkZ) == Status.EXPLORED) {
                return Status.EXPLORED
            }
            return b.isAlreadyExplored(chunkX, chunkZ)
        }

        override fun countRemain(): Int = min(a.countRemain(), b.countRemain())
    }

    companion object {
        private val log: Logger = MaestroLogger.get("path")

        private fun createGoal(
            x: Int,
            z: Int,
        ): Goal {
            val maintainY = Agent.settings().exploreMaintainY.value
            if (maintainY == -1) {
                return GoalXZ(x, z)
            }

            // Don't use a goalblock because we still want isInGoal to return true if X and Z are correct
            // We just want to try and maintain Y on the way there, not necessarily end at that specific Y
            return object : GoalXZ(x, z) {
                override fun heuristic(
                    x: Int,
                    y: Int,
                    z: Int,
                ): Double = super.heuristic(x, y, z) + GoalYLevel.calculate(maintainY, y)
            }
        }
    }
}
