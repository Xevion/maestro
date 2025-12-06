package maestro.pathing.movement

import maestro.utils.Rotation
import java.util.Optional

/**
 * Represents the state of a movement during execution.
 * Contains only status and rotation targets - inputs are managed directly via InputOverrideHandler.
 */
class MovementState {
    private var status: MovementStatus? = null
    private var target: MovementTarget = MovementTarget()

    fun setStatus(status: MovementStatus): MovementState {
        this.status = status
        return this
    }

    fun getStatus(): MovementStatus? = status

    fun getTarget(): MovementTarget = target

    fun setTarget(target: MovementTarget): MovementState {
        this.target = target
        return this
    }

    class MovementTarget(
        /** Yaw and pitch angles that must be matched */
        @JvmField var rotation: Rotation? = null,
        /**
         * Whether this target must force rotations.
         *
         * `true` if we're trying to place or break blocks, `false` if we're trying
         * to look at the movement location
         */
        private val forceRotations: Boolean = false,
    ) {
        fun getRotation(): Optional<Rotation> = Optional.ofNullable(rotation)

        fun hasToForceRotations(): Boolean = forceRotations
    }
}
