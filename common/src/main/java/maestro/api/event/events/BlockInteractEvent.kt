package maestro.api.event.events

import net.minecraft.core.BlockPos

/**
 * Called when the local player interacts with a block, can be either [Type.START_BREAK] or
 * [Type.USE].
 */
class BlockInteractEvent(
    /** The position of the block interacted with */
    @JvmField val pos: BlockPos,
    /** The type of interaction that occurred */
    @JvmField val type: Type,
) {
    enum class Type {
        /** We're starting to break the target block. */
        START_BREAK,

        /** We're right-clicking on the target block. Either placing or interacting with. */
        USE,
    }
}
