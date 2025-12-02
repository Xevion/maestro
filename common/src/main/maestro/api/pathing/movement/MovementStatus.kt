package maestro.api.pathing.movement

enum class MovementStatus(
    /** Whether this status indicates a complete movement. */
    val isComplete: Boolean,
) {
    /**
     * We are preparing the movement to be executed. This is when any blocks obstructing the
     * destination are broken.
     */
    PREPPING(false),

    /** We are waiting for the movement to begin, after [PREPPING]. */
    WAITING(false),

    /** The movement is currently in progress, after [WAITING] */
    RUNNING(false),

    /** The movement has been completed, and we are at our destination */
    SUCCESS(true),

    /**
     * There was a change in state between calculation and actual movement execution, and the
     * movement has now become impossible.
     */
    UNREACHABLE(true),

    /** Unused */
    FAILED(true),

    /** "Unused" */
    CANCELED(true),
}
