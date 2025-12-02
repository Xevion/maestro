package maestro.pathing.calc

/**
 * Reasons why pathfinding calculation might fail to find a complete path.
 *
 * Distinct from [maestro.pathing.recovery.FailureReason] which tracks movement execution failures.
 */
enum class PathfindingFailureReason {
    /** Goal is completely unreachable from start position. */
    UNREACHABLE,

    /** Primary timeout expired before finding complete path. */
    PRIMARY_TIMEOUT,

    /** Failure timeout expired (absolute maximum search time). */
    FAILURE_TIMEOUT,

    /** Too many unloaded chunks blocked pathfinding. */
    CHUNK_LOAD_LIMIT,

    /** Calculation was cancelled by user or system request. */
    CANCELLED,
}
