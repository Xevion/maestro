package maestro.behavior

/**
 * Describes why a pathfinding calculation was initiated.
 *
 * Used to control logging verbosity and distinguish between user-facing
 * path calculations and background optimization tasks.
 */
enum class PathfindingReason {
    /** Initial path calculation to reach a new goal. */
    INITIAL_PATH,

    /** Background plan-ahead calculation for next path segment. */
    PLAN_AHEAD,

    /** Recovery path calculation after execution failure. */
    RECOVERY,
}
