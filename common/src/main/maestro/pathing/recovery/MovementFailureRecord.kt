package maestro.pathing.recovery

import maestro.pathing.movement.Movement

/**
 * Records a single movement failure for use in failure memory.
 *
 * Tracks when a movement failed, why it failed, and how many consecutive failures occurred for
 * this specific source→destination movement.
 */
data class MovementFailureRecord(
    /** Movement key (source and destination positions) */
    val movementKey: MovementKey,
    /** Type of movement that failed */
    val movementType: Class<out Movement>,
    /** When the failure occurred (System.currentTimeMillis()) */
    val timestamp: Long,
    /** Why the movement failed */
    val reason: FailureReason,
    /** Number of consecutive failures for this movement type at this position */
    val attemptCount: Int,
) {
    /**
     * Checks if this failure record has expired based on the given duration.
     *
     * @param currentTime current time in milliseconds
     * @param memoryDuration how long to remember failures in milliseconds
     * @return true if this record should be forgotten
     */
    fun isExpired(
        currentTime: Long,
        memoryDuration: Long,
    ): Boolean = (currentTime - timestamp) > memoryDuration

    override fun toString(): String =
        "MovementFailureRecord{movement=$movementKey, type=${movementType.simpleName}, " +
            "reason=$reason, attempts=$attemptCount, age=${System.currentTimeMillis() - timestamp}ms}"
}
