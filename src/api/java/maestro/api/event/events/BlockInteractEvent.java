package maestro.api.event.events;

import net.minecraft.core.BlockPos;

/**
 * Called when the local player interacts with a block, can be either {@link Type#START_BREAK} or
 * {@link Type#USE}.
 */
public final class BlockInteractEvent {

    /** The position of the block interacted with */
    private final BlockPos pos;

    /** The type of interaction that occurred */
    private final Type type;

    public BlockInteractEvent(BlockPos pos, Type type) {
        this.pos = pos;
        this.type = type;
    }

    /**
     * @return The position of the block interacted with
     */
    public BlockPos getPos() {
        return this.pos;
    }

    /**
     * @return The type of interaction with the target block
     */
    public Type getType() {
        return this.type;
    }

    public enum Type {

        /** We're starting to break the target block. */
        START_BREAK,

        /** We're right clicking on the target block. Either placing or interacting with. */
        USE
    }
}
