package maestro.api.pathing.goals

/**
 * Invert any goal.
 *
 * In the old chat control system, #invert just tried to pick a [GoalRunAway] that *effectively* inverted the current
 * goal. This goal just reverses the heuristic to act as a TRUE invert. Inverting a Y level? Maestro tries to get away
 * from that Y level. Inverting a GoalBlock? Maestro will try to make distance whether it's in the X, Y or Z
 * directions. And of course, you can always invert a GoalXZ.
 */
data class GoalInverted(
    @JvmField val origin: Goal,
) : Goal {
    override fun isInGoal(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean = false

    override fun heuristic(
        x: Int,
        y: Int,
        z: Int,
    ): Double = -origin.heuristic(x, y, z)

    override fun heuristic(): Double = Double.NEGATIVE_INFINITY

    override fun hashCode(): Int = origin.hashCode() * 495796690

    override fun toString(): String = "GoalInverted[$origin]"
}
