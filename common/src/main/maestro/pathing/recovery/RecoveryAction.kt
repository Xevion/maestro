package maestro.pathing.recovery

import maestro.pathing.movement.Movement

/**
 * Sealed class representing possible recovery actions when path execution fails.
 *
 * Each variant contains only the data relevant to that specific action type,
 * eliminating the need for runtime type checks and IllegalStateExceptions.
 */
sealed class RecoveryAction {
    /** Cancel the current path and trigger recalculation */
    data object Cancel : RecoveryAction()

    /** Continue normal execution without intervention */
    data object Continue : RecoveryAction()

    /**
     * Retry with an alternative movement from the current position
     *
     * @param movement the alternative movement to attempt
     */
    data class Retry(
        val movement: Movement,
    ) : RecoveryAction()

    /**
     * Reconnect to an existing path at a different position
     *
     * @param pathIndex the index in the path to reconnect to
     */
    data class Reconnect(
        val pathIndex: Int,
    ) : RecoveryAction()

    companion object {
        /** Factory method for cancel action (for Java interop) */
        @JvmStatic
        fun cancelPath(): RecoveryAction = Cancel

        /** Factory method for continue action (for Java interop) */
        @JvmStatic
        fun continueExecution(): RecoveryAction = Continue

        /** Factory method for retry action (for Java interop) */
        @JvmStatic
        fun retryMovement(movement: Movement): RecoveryAction = Retry(movement)

        /** Factory method for reconnect action (for Java interop) */
        @JvmStatic
        fun reconnectPath(index: Int): RecoveryAction = Reconnect(index)
    }
}
