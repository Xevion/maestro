package maestro.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maestro.Agent
import maestro.api.utils.BlockUtils
import maestro.api.utils.Loggers
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.dimension.DimensionType
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.BitSet
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists

private val log = Loggers.get("cache")

class CachedRegion(
    /** The region x coordinate */
    internal val x: Int,
    /** The region z coordinate */
    internal val z: Int,
    private val dimension: DimensionType,
) {
    /** All the chunks in this region: A 32x32 array of them. */
    private val chunks: Array<Array<CachedChunk?>> = Array(32) { arrayOfNulls(32) }

    /** Has this region been modified since its most recent load or save */
    @Volatile
    private var hasUnsavedChanges: Boolean = false

    private val saveMutex = Mutex()

    fun getBlock(
        x: Int,
        y: Int,
        z: Int,
    ): BlockState? {
        val adjY = y - dimension.minY()
        val chunk = chunks[x shr 4][z shr 4]
        return chunk?.getBlock(x and 15, adjY, z and 15, dimension)
    }

    fun isCached(
        x: Int,
        z: Int,
    ): Boolean = chunks[x shr 4][z shr 4] != null

    fun getLocationsOf(block: String): ArrayList<BlockPos> {
        val res = ArrayList<BlockPos>()
        for (chunkX in 0..<32) {
            for (chunkZ in 0..<32) {
                val chunk = chunks[chunkX][chunkZ] ?: continue
                val locations = chunk.getAbsoluteBlocks(block)
                if (locations != null) {
                    res.addAll(locations)
                }
            }
        }
        return res
    }

    fun updateCachedChunk(
        chunkX: Int,
        chunkZ: Int,
        chunk: CachedChunk,
    ) {
        chunks[chunkX][chunkZ] = chunk
        hasUnsavedChanges = true
    }

    fun save(directory: String) {
        if (!hasUnsavedChanges) return

        // Use runBlocking with mutex for Java interop
        kotlinx.coroutines.runBlocking {
            saveMutex.withLock {
                saveInternal(directory)
            }
        }
    }

    private fun saveInternal(directory: String) {
        try {
            removeExpired()

            val path = Paths.get(directory)
            if (!path.exists()) {
                path.createDirectories()
            }

            val regionFile = getRegionFile(path, x, z)
            if (!regionFile.exists()) {
                regionFile.createFile()
            }

            FileOutputStream(regionFile.toFile()).use { fileOut ->
                GZIPOutputStream(fileOut, 16384).use { gzipOut ->
                    DataOutputStream(gzipOut).use { out ->
                        out.writeInt(CACHED_REGION_MAGIC)

                        // Write chunk data
                        for (x in 0..<32) {
                            for (z in 0..<32) {
                                val chunk = chunks[x][z]
                                if (chunk == null) {
                                    out.write(CHUNK_NOT_PRESENT.toInt())
                                } else {
                                    out.write(CHUNK_PRESENT.toInt())
                                    val chunkBytes = chunk.toByteArray()
                                    out.write(chunkBytes)
                                    // Fill trailing zeros to match chunk size
                                    out.write(ByteArray(chunk.sizeInBytes - chunkBytes.size))
                                }
                            }
                        }

                        // Write overview data
                        for (x in 0..<32) {
                            for (z in 0..<32) {
                                val chunk = chunks[x][z]
                                if (chunk != null) {
                                    for (i in 0..<256) {
                                        out.writeUTF(BlockUtils.blockToString(chunk.getOverview()[i].block))
                                    }
                                }
                            }
                        }

                        // Write special block locations
                        for (x in 0..<32) {
                            for (z in 0..<32) {
                                val chunk = chunks[x][z]
                                if (chunk != null) {
                                    val locations = chunk.getRelativeBlocks()
                                    out.writeShort(locations.size)
                                    for ((blockName, positions) in locations) {
                                        out.writeUTF(blockName)
                                        out.writeShort(positions.size)
                                        for (pos in positions) {
                                            out.writeByte((pos.z shl 4 or pos.x).toByte().toInt())
                                            out.writeInt(pos.y - dimension.minY())
                                        }
                                    }
                                }
                            }
                        }

                        // Write cache timestamps
                        for (x in 0..<32) {
                            for (z in 0..<32) {
                                val chunk = chunks[x][z]
                                if (chunk != null) {
                                    out.writeLong(chunk.cacheTimestamp)
                                }
                            }
                        }
                    }
                }
            }

            hasUnsavedChanges = false
        } catch (ex: Exception) {
            log
                .atError()
                .setCause(ex)
                .addKeyValue("region_x", x)
                .addKeyValue("region_z", z)
                .log("Failed to save region")
        }
    }

    fun load(directory: String) {
        // Use runBlocking with mutex for Java interop
        kotlinx.coroutines.runBlocking {
            saveMutex.withLock {
                loadInternal(directory)
            }
        }
    }

    private fun loadInternal(directory: String) {
        try {
            val path = Paths.get(directory)
            if (!path.exists()) {
                path.createDirectories()
            }

            val regionFile = getRegionFile(path, x, z)
            if (!regionFile.exists()) {
                return
            }

            val start = System.nanoTime() / 1000000L

            FileInputStream(regionFile.toFile()).use { fileIn ->
                GZIPInputStream(fileIn, 32768).use { gzipIn ->
                    DataInputStream(gzipIn).use { input ->
                        val magic = input.readInt()
                        if (magic != CACHED_REGION_MAGIC) {
                            throw java.io.IOException("Bad magic value $magic")
                        }

                        val present = Array(32) { BooleanArray(32) }
                        val bitSets = Array(32) { arrayOfNulls<BitSet>(32) }
                        val location =
                            Array(32) { arrayOfNulls<MutableMap<String, List<BlockPos>>>(32) }
                        val overview = Array(32) { arrayOfNulls<Array<BlockState>>(32) }
                        val cacheTimestamp = Array(32) { LongArray(32) }

                        // Read chunk presence and data
                        for (x in 0..<32) {
                            for (z in 0..<32) {
                                when (input.read()) {
                                    CHUNK_PRESENT.toInt() -> {
                                        val bytes =
                                            ByteArray(
                                                CachedChunk.sizeInBytes(
                                                    CachedChunk.size(dimension.height()),
                                                ),
                                            )
                                        input.readFully(bytes)
                                        bitSets[x][z] = BitSet.valueOf(bytes)
                                        location[x][z] = mutableMapOf()
                                        @Suppress("UNCHECKED_CAST")
                                        overview[x][z] = arrayOfNulls<BlockState>(256) as Array<BlockState>
                                        present[x][z] = true
                                    }
                                    CHUNK_NOT_PRESENT.toInt() -> {}
                                    else -> throw java.io.IOException("Malformed stream")
                                }
                            }
                        }

                        // Read overview data
                        for (x in 0..<32) {
                            for (z in 0..<32) {
                                if (present[x][z]) {
                                    val overviewArray = overview[x][z]!!
                                    for (i in 0..<256) {
                                        overviewArray[i] =
                                            BlockUtils
                                                .stringToBlockRequired(input.readUTF())
                                                .defaultBlockState()
                                    }
                                }
                            }
                        }

                        // Read special block locations
                        for (x in 0..<32) {
                            for (z in 0..<32) {
                                if (present[x][z]) {
                                    val numSpecialBlockTypes = input.readShort().toInt() and 0xffff
                                    for (i in 0..<numSpecialBlockTypes) {
                                        val blockName = input.readUTF()
                                        BlockUtils.stringToBlockRequired(blockName)
                                        val locations = mutableListOf<BlockPos>()
                                        location[x][z]!![blockName] = locations
                                        var numLocations = input.readShort().toInt() and 0xffff
                                        if (numLocations == 0) {
                                            // entire chunk full of air can happen in the end
                                            numLocations = 65536
                                        }
                                        for (j in 0..<numLocations) {
                                            val xzByte = input.readByte()
                                            val posX = xzByte.toInt() and 0x0f
                                            val posZ = (xzByte.toInt() ushr 4) and 0x0f
                                            val posY = input.readInt()
                                            locations.add(BlockPos(posX, posY + dimension.minY(), posZ))
                                        }
                                    }
                                }
                            }
                        }

                        // Read cache timestamps
                        for (x in 0..<32) {
                            for (z in 0..<32) {
                                if (present[x][z]) {
                                    cacheTimestamp[x][z] = input.readLong()
                                }
                            }
                        }

                        // Only if the entire file was uncorrupted do we actually set the chunks
                        for (x in 0..<32) {
                            for (z in 0..<32) {
                                if (present[x][z]) {
                                    val chunkX = x + 32 * this.x
                                    val chunkZ = z + 32 * this.z
                                    chunks[x][z] =
                                        CachedChunk(
                                            chunkX,
                                            chunkZ,
                                            dimension.height(),
                                            bitSets[x][z]!!,
                                            overview[x][z]!!,
                                            location[x][z]!!,
                                            cacheTimestamp[x][z],
                                        )
                                }
                            }
                        }
                    }
                }
            }

            removeExpired()
            hasUnsavedChanges = false

            val end = System.nanoTime() / 1000000L
            log
                .atDebug()
                .addKeyValue("region_x", x)
                .addKeyValue("region_z", z)
                .addKeyValue("duration_ms", end - start)
                .log("Region loaded")
        } catch (ex: Exception) {
            log
                .atError()
                .setCause(ex)
                .addKeyValue("region_x", x)
                .addKeyValue("region_z", z)
                .log("Failed to load region")
        }
    }

    fun removeExpired() {
        val expiry = Agent.settings().cachedChunksExpirySeconds.value
        if (expiry < 0) return

        val now = System.currentTimeMillis()
        val oldestAcceptableAge = now - expiry * 1000L

        for (x in 0..<32) {
            for (z in 0..<32) {
                val chunk = chunks[x][z]
                if (chunk != null && chunk.cacheTimestamp < oldestAcceptableAge) {
                    log
                        .atDebug()
                        .addKeyValue("chunk_x", x + 32 * this.x)
                        .addKeyValue("chunk_z", z + 32 * this.z)
                        .addKeyValue("age_seconds", (now - chunk.cacheTimestamp) / 1000L)
                        .addKeyValue("max_age_seconds", expiry)
                        .log("Chunk expired and removed")
                    chunks[x][z] = null
                }
            }
        }
    }

    fun mostRecentlyModified(): CachedChunk? {
        var recent: CachedChunk? = null
        for (x in 0..<32) {
            for (z in 0..<32) {
                val chunk = chunks[x][z] ?: continue
                if (recent == null || chunk.cacheTimestamp > recent.cacheTimestamp) {
                    recent = chunk
                }
            }
        }
        return recent
    }

    fun getX(): Int = x

    fun getZ(): Int = z

    companion object {
        private const val CHUNK_NOT_PRESENT: Byte = 0
        private const val CHUNK_PRESENT: Byte = 1

        /**
         * Magic value to detect invalid cache files, or incompatible cache files saved in an old
         * version of Maestro
         */
        private const val CACHED_REGION_MAGIC = 456022911

        private fun getRegionFile(
            cacheDir: Path,
            regionX: Int,
            regionZ: Int,
        ): Path = Paths.get(cacheDir.toString(), "r.$regionX.$regionZ.bcr")
    }
}
