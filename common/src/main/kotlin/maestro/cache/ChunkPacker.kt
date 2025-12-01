package maestro.cache

import maestro.api.utils.BlockUtils
import maestro.api.utils.MaestroLogger
import maestro.pathing.movement.MovementHelper
import maestro.utils.BlockStateInterface
import maestro.utils.pathing.PathingBlockType
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DoublePlantBlock
import net.minecraft.world.level.block.FlowerBlock
import net.minecraft.world.level.block.TallGrassBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import net.minecraft.world.level.dimension.DimensionType
import java.util.BitSet

private val log = MaestroLogger.get("cache")

object ChunkPacker {
    @JvmStatic
    fun pack(chunk: LevelChunk): CachedChunk {
        val specialBlocks = mutableMapOf<String, MutableList<BlockPos>>()
        val height = chunk.level.dimensionType().height()
        val bitSet = BitSet(CachedChunk.size(height))

        try {
            val chunkInternalStorageArray = chunk.sections
            for (y0 in 0..<height / 16) {
                val extendedBlockStorage = chunkInternalStorageArray[y0] ?: continue

                val bsc = extendedBlockStorage.states
                val yReal = y0 shl 4

                for (y1 in 0..<16) {
                    val y = y1 or yReal
                    for (z in 0..<16) {
                        for (x in 0..<16) {
                            val index = CachedChunk.getPositionIndex(x, y, z)
                            val state = bsc.get(x, y1, z)
                            val bits = getPathingBlockType(state, chunk, x, y, z).bitsArray
                            bitSet.set(index, bits[0])
                            bitSet.set(index + 1, bits[1])

                            val block = state.block
                            if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(block)) {
                                val name = BlockUtils.blockToString(block)
                                specialBlocks
                                    .computeIfAbsent(name) { mutableListOf() }
                                    .add(BlockPos(x, y + chunk.minY, z))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log
                .atError()
                .setCause(e)
                .addKeyValue("chunk_x", chunk.pos.x)
                .addKeyValue("chunk_z", chunk.pos.z)
                .log("Failed to pack chunk")
        }

        val blocks = Array(256) { Blocks.AIR.defaultBlockState() }

        for (z in 0..<16) {
            outer@ for (x in 0..<16) {
                for (y in height - 1 downTo 0) {
                    val index = CachedChunk.getPositionIndex(x, y, z)
                    if (bitSet.get(index) || bitSet.get(index + 1)) {
                        blocks[z shl 4 or x] = BlockStateInterface.getFromChunk(chunk, x, y, z)
                        continue@outer
                    }
                }
                blocks[z shl 4 or x] = Blocks.AIR.defaultBlockState()
            }
        }

        return CachedChunk(
            chunk.pos.x,
            chunk.pos.z,
            height,
            bitSet,
            blocks,
            specialBlocks,
            System.currentTimeMillis(),
        )
    }

    @JvmStatic
    private fun getPathingBlockType(
        state: BlockState,
        chunk: LevelChunk,
        x: Int,
        y: Int,
        z: Int,
    ): PathingBlockType {
        val block = state.block
        if (MovementHelper.isWater(state)) {
            if (MovementHelper.possiblyFlowing(state)) {
                return PathingBlockType.AVOID
            }

            val adjY = y - chunk.level.dimensionType().minY()
            if ((x != 15 && MovementHelper.possiblyFlowing(BlockStateInterface.getFromChunk(chunk, x + 1, adjY, z))) ||
                (x != 0 && MovementHelper.possiblyFlowing(BlockStateInterface.getFromChunk(chunk, x - 1, adjY, z))) ||
                (z != 15 && MovementHelper.possiblyFlowing(BlockStateInterface.getFromChunk(chunk, x, adjY, z + 1))) ||
                (z != 0 && MovementHelper.possiblyFlowing(BlockStateInterface.getFromChunk(chunk, x, adjY, z - 1)))
            ) {
                return PathingBlockType.AVOID
            }

            if (x == 0 || x == 15 || z == 0 || z == 15) {
                val flow =
                    state
                        .fluidState
                        .getFlow(
                            chunk.level,
                            BlockPos(
                                x + (chunk.pos.x shl 4),
                                y,
                                z + (chunk.pos.z shl 4),
                            ),
                        )
                if (flow.x != 0.0 || flow.z != 0.0) {
                    return PathingBlockType.WATER
                }
                return PathingBlockType.AVOID
            }
            return PathingBlockType.WATER
        }

        if (MovementHelper.avoidWalkingInto(state) || MovementHelper.isBottomSlab(state)) {
            return PathingBlockType.AVOID
        }

        if (block is AirBlock ||
            block is TallGrassBlock ||
            block is DoublePlantBlock ||
            block is FlowerBlock
        ) {
            return PathingBlockType.AIR
        }

        return PathingBlockType.SOLID
    }

    @JvmStatic
    fun pathingTypeToBlock(
        type: PathingBlockType,
        dimension: DimensionType,
    ): BlockState =
        when (type) {
            PathingBlockType.AIR -> Blocks.AIR.defaultBlockState()
            PathingBlockType.WATER -> Blocks.WATER.defaultBlockState()
            PathingBlockType.AVOID -> Blocks.LAVA.defaultBlockState()
            PathingBlockType.SOLID -> {
                when {
                    dimension.natural() -> Blocks.STONE.defaultBlockState()
                    dimension.ultraWarm() -> Blocks.NETHERRACK.defaultBlockState()
                    dimension.effectsLocation() == BuiltinDimensionTypes.END_EFFECTS ->
                        Blocks.END_STONE.defaultBlockState()
                    else -> Blocks.STONE.defaultBlockState()
                }
            }
        }
}
