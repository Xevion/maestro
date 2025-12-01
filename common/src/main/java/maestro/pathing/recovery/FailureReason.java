package maestro.pathing.recovery;

/**
 * Reasons why a movement execution might fail.
 *
 * <p>Used by {@link MovementFailureMemory} to categorize failures and apply appropriate recovery
 * strategies.
 */
public enum FailureReason {
    /** Server rejected the movement (e.g., teleport packet rejected) */
    SERVER_REJECTED,

    /** World state changed during movement execution (blocks placed/broken) */
    WORLD_CHANGED,

    /** Movement took too long to complete and timed out */
    TIMEOUT,

    /** Path became blocked by entities or unexpected obstacles */
    BLOCKED,

    /** Movement determined to be impossible to execute */
    UNREACHABLE,
}
