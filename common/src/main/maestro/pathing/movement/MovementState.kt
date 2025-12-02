package maestro.pathing.movement

import maestro.api.pathing.movement.MovementStatus
import maestro.api.utils.Rotation
import maestro.api.utils.input.Input
import java.util.Optional

class MovementState {
    private var status: MovementStatus? = null
    private var target: MovementTarget = MovementTarget()
    private val inputState: MutableMap<Input, Boolean> = mutableMapOf()

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

    fun setInput(
        input: Input,
        forced: Boolean,
    ): MovementState {
        inputState[input] = forced
        return this
    }

    fun getInputStates(): Map<Input, Boolean> = inputState

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
