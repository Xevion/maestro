package maestro.api.event.events;

import java.util.List;
import maestro.api.utils.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockChangeEvent {

    private final ChunkPos chunk;
    private final List<Pair<BlockPos, BlockState>> blocks;

    public BlockChangeEvent(ChunkPos pos, List<Pair<BlockPos, BlockState>> blocks) {
        this.chunk = pos;
        this.blocks = blocks;
    }

    public ChunkPos getChunkPos() {
        return this.chunk;
    }

    public List<Pair<BlockPos, BlockState>> getBlocks() {
        return this.blocks;
    }
}
