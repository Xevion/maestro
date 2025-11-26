package maestro.pathing.recovery;

import maestro.pathing.movement.Movement;

/**
 * Records a single movement failure for use in failure memory.
 *
 * <p>Tracks when a movement failed, why it failed, and how many consecutive failures occurred for
 * this specific source→destination movement.
 */
public class MovementFailureRecord {

    /** Movement key (source and destination positions) */
    public final MovementKey movementKey;

    /** Type of movement that failed */
    public final Class<? extends Movement> movementType;

    /** When the failure occurred (System.currentTimeMillis()) */
    public final long timestamp;

    /** Why the movement failed */
    public final FailureReason reason;

    /** Number of consecutive failures for this movement type at this position */
    public final int attemptCount;

    public MovementFailureRecord(
            MovementKey movementKey,
            Class<? extends Movement> movementType,
            long timestamp,
            FailureReason reason,
            int attemptCount) {
        this.movementKey = movementKey;
        this.movementType = movementType;
        this.timestamp = timestamp;
        this.reason = reason;
        this.attemptCount = attemptCount;
    }

    /**
     * Checks if this failure record has expired based on the given duration.
     *
     * @param currentTime current time in milliseconds
     * @param memoryDuration how long to remember failures in milliseconds
     * @return true if this record should be forgotten
     */
    public boolean isExpired(long currentTime, long memoryDuration) {
        return (currentTime - timestamp) > memoryDuration;
    }

    @Override
    public String toString() {
        return String.format(
                "MovementFailureRecord{movement=%s, type=%s, reason=%s, attempts=%d, age=%dms}",
                movementKey,
                movementType.getSimpleName(),
                reason,
                attemptCount,
                System.currentTimeMillis() - timestamp);
    }
}
