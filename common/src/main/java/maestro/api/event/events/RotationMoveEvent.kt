package maestro.api.event.events

import maestro.api.utils.Rotation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

/**
 * Event fired when player rotation is updated during movement or jumping.
 *
 * This event allows modification of yaw and pitch rotations before they are applied.
 */
class RotationMoveEvent(
    /** The type of event */
    val type: Type,
    /** The yaw rotation (can be modified) */
    var yaw: Float,
    /** The pitch rotation (can be modified) */
    var pitch: Float,
) {
    /** Original rotation snapshot (immutable) */
    val original: Rotation = Rotation(yaw, pitch)

    /**
     * Event type indicating when the rotation update occurs.
     */
    enum class Type {
        /**
         * Called when the player's motion is updated.
         *
         * @see Entity.moveRelative
         */
        MOTION_UPDATE,

        /**
         * Called when the player jumps.
         *
         * @see LivingEntity
         */
        JUMP,
    }
}
