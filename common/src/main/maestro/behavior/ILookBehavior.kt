package maestro.behavior

import maestro.behavior.look.IAimProcessor
import maestro.utils.Rotation

interface ILookBehavior : IBehavior {
    /**
     * Updates the current [ILookBehavior] target to target the specified rotations on the next
     * tick. If any sort of block interaction is required, [blockInteract] should be `true`. It is
     * not guaranteed that the rotations set by the caller will be the exact rotations expressed by
     * the client (This is due to settings like randomLooking). If the rotations produced by this
     * behavior are required, then the [aimProcessor][getAimProcessor] should be used.
     *
     * @param rotation The target rotations
     * @param blockInteract Whether the target rotations are needed for a block interaction
     */
    fun updateTarget(
        rotation: Rotation,
        blockInteract: Boolean,
    )

    /**
     * The aim processor instance for this [ILookBehavior], which is responsible for applying
     * additional, deterministic transformations to the target rotation set by [updateTarget].
     *
     * @return The aim processor
     * @see IAimProcessor.fork
     */
    fun getAimProcessor(): IAimProcessor
}
