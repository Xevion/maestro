package maestro.cache

import com.google.common.collect.ImmutableSet
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import maestro.api.utils.BlockUtils
import maestro.utils.pathing.PathingBlockType
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.dimension.DimensionType
import java.util.BitSet

class CachedChunk internal constructor(
    /** The chunk x coordinate */
    @JvmField val x: Int,
    /** The chunk z coordinate */
    @JvmField val z: Int,
    @JvmField val height: Int,
    private val data: BitSet,
    private val overview: Array<BlockState>,
    private val specialBlockLocations: Map<String, List<BlockPos>>,
    @JvmField val cacheTimestamp: Long,
) {
    /**
     * The size of the chunk data in bits. Equal to 16 KiB.
     *
     * Chunks are 16x16xH, each block requires 2 bits.
     */
    @JvmField val size: Int = size(height)

    /** The size of the chunk data in bytes. Equal to 16 KiB for 256 height. */
    @JvmField val sizeInBytes: Int = sizeInBytes(size)

    private val special: Int2ObjectOpenHashMap<String>?
    private val heightMap: IntArray = IntArray(256)

    init {
        validateSize(data)
        special =
            if (specialBlockLocations.isEmpty()) {
                null
            } else {
                Int2ObjectOpenHashMap<String>().also { map ->
                    specialBlockLocations.forEach { (blockName, positions) ->
                        positions.forEach { pos ->
                            map[getPositionIndex(pos.x, pos.y, pos.z)] = blockName
                        }
                    }
                }
            }
        calculateHeightMap()
    }

    fun getBlock(
        x: Int,
        y: Int,
        z: Int,
        dimension: DimensionType,
    ): BlockState {
        val index = getPositionIndex(x, y, z)
        val type = getType(index)
        val internalPos = z shl 4 or x

        if (heightMap[internalPos] == y && type != PathingBlockType.AVOID) {
            // if the top block in a column is water, we cache it as AVOID, but we don't want to
            // just return default state water (which is not flowing) because then it would try to
            // path through it
            //
            // we have this exact block, it's a surface block
            return overview[internalPos]
        }

        special?.get(index)?.let { blockName ->
            return BlockUtils.stringToBlockRequired(blockName).defaultBlockState()
        }

        if (type == PathingBlockType.SOLID) {
            if (y == dimension.logicalHeight() - 1 && dimension.hasCeiling()) {
                // nether roof is always unbreakable
                return Blocks.BEDROCK.defaultBlockState()
            }
            if (y < -59 && dimension.natural()) {
                // solid blocks below 5 are commonly bedrock
                // however, returning bedrock always would be a little yikes
                // discourage paths that include breaking blocks below 5 a little more heavily just
                // so that it takes paths breaking what's known to be stone (at 5 or above) instead
                // of what could maybe be bedrock (below 5)
                return Blocks.OBSIDIAN.defaultBlockState()
            }
        }

        return ChunkPacker.pathingTypeToBlock(type, dimension)
    }

    private fun getType(index: Int): PathingBlockType = PathingBlockType.fromBits(data[index], data[index + 1])

    private fun calculateHeightMap() {
        for (z in 0..<16) {
            for (x in 0..<16) {
                val index = z shl 4 or x
                heightMap[index] = 0
                for (y in height downTo 0) {
                    val i = getPositionIndex(x, y, z)
                    if (data[i] || data[i + 1]) {
                        heightMap[index] = y
                        break
                    }
                }
            }
        }
    }

    fun getOverview(): Array<BlockState> = overview

    fun getRelativeBlocks(): Map<String, List<BlockPos>> = specialBlockLocations

    fun getAbsoluteBlocks(blockType: String): ArrayList<BlockPos>? {
        val blocks = specialBlockLocations[blockType] ?: return null
        return ArrayList(
            blocks.map { pos ->
                BlockPos(pos.x + x * 16, pos.y, pos.z + z * 16)
            },
        )
    }

    /** @return Returns the raw packed chunk data as a byte array */
    fun toByteArray(): ByteArray = data.toByteArray()

    /**
     * Validates the size of an input [BitSet] containing the raw packed chunk data. Sizes that
     * exceed [size] are considered invalid, and thus, an exception will be thrown.
     *
     * @param data The raw data
     * @throws IllegalArgumentException if the bitset size exceeds the maximum size
     */
    private fun validateSize(data: BitSet) {
        require(data.size() <= size) { "BitSet of invalid length provided" }
    }

    companion object {
        @JvmField
        val BLOCKS_TO_KEEP_TRACK_OF: ImmutableSet<Block> =
            ImmutableSet.of(
                Blocks.ENDER_CHEST,
                Blocks.FURNACE,
                Blocks.CHEST,
                Blocks.TRAPPED_CHEST,
                Blocks.END_PORTAL,
                Blocks.END_PORTAL_FRAME,
                Blocks.SPAWNER,
                Blocks.BARRIER,
                Blocks.OBSERVER,
                Blocks.WHITE_SHULKER_BOX,
                Blocks.ORANGE_SHULKER_BOX,
                Blocks.MAGENTA_SHULKER_BOX,
                Blocks.LIGHT_BLUE_SHULKER_BOX,
                Blocks.YELLOW_SHULKER_BOX,
                Blocks.LIME_SHULKER_BOX,
                Blocks.PINK_SHULKER_BOX,
                Blocks.GRAY_SHULKER_BOX,
                Blocks.LIGHT_GRAY_SHULKER_BOX,
                Blocks.CYAN_SHULKER_BOX,
                Blocks.PURPLE_SHULKER_BOX,
                Blocks.BLUE_SHULKER_BOX,
                Blocks.BROWN_SHULKER_BOX,
                Blocks.GREEN_SHULKER_BOX,
                Blocks.RED_SHULKER_BOX,
                Blocks.BLACK_SHULKER_BOX,
                Blocks.NETHER_PORTAL,
                Blocks.HOPPER,
                Blocks.BEACON,
                Blocks.BREWING_STAND,
                // TODO: Maybe add a predicate for blocks to keep track of?
                // This should really not need to happen
                Blocks.CREEPER_HEAD,
                Blocks.CREEPER_WALL_HEAD,
                Blocks.DRAGON_HEAD,
                Blocks.DRAGON_WALL_HEAD,
                Blocks.PLAYER_HEAD,
                Blocks.PLAYER_WALL_HEAD,
                Blocks.ZOMBIE_HEAD,
                Blocks.ZOMBIE_WALL_HEAD,
                Blocks.SKELETON_SKULL,
                Blocks.SKELETON_WALL_SKULL,
                Blocks.WITHER_SKELETON_SKULL,
                Blocks.WITHER_SKELETON_WALL_SKULL,
                Blocks.ENCHANTING_TABLE,
                Blocks.ANVIL,
                Blocks.WHITE_BED,
                Blocks.ORANGE_BED,
                Blocks.MAGENTA_BED,
                Blocks.LIGHT_BLUE_BED,
                Blocks.YELLOW_BED,
                Blocks.LIME_BED,
                Blocks.PINK_BED,
                Blocks.GRAY_BED,
                Blocks.LIGHT_GRAY_BED,
                Blocks.CYAN_BED,
                Blocks.PURPLE_BED,
                Blocks.BLUE_BED,
                Blocks.BROWN_BED,
                Blocks.GREEN_BED,
                Blocks.RED_BED,
                Blocks.BLACK_BED,
                Blocks.DRAGON_EGG,
                Blocks.JUKEBOX,
                Blocks.END_GATEWAY,
                Blocks.COBWEB,
                Blocks.NETHER_WART,
                Blocks.LADDER,
                Blocks.VINE,
            )

        /**
         * Returns the raw bit index of the specified position
         *
         * @param x The x position
         * @param y The y position
         * @param z The z position
         * @return The bit index
         */
        @JvmStatic
        fun getPositionIndex(
            x: Int,
            y: Int,
            z: Int,
        ): Int = (x shl 1) or (z shl 5) or (y shl 9)

        @JvmStatic
        fun size(dimensionHeight: Int): Int = 2 * 16 * 16 * dimensionHeight

        @JvmStatic
        fun sizeInBytes(size: Int): Int = size / 8
    }
}
