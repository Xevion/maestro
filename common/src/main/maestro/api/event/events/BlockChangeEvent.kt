package maestro.api.event.events

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState

/** Event fired when blocks in a chunk are changed */
class BlockChangeEvent(
    val chunkPos: ChunkPos,
    @JvmField val blocks: List<Pair<BlockPos, BlockState>>,
)
