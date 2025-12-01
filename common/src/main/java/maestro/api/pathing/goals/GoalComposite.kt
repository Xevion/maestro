package maestro.api.pathing.goals

/**
 * A composite of many goals, any one of which satisfies the composite. For example, a GoalComposite of block goals
 * for every oak log in loaded chunks would result in it pathing to the easiest oak log to get to
 */
class GoalComposite(
    vararg goals: Goal,
) : Goal {
    private val goals: Array<out Goal> = goals

    override fun isInGoal(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean = goals.any { it.isInGoal(x, y, z) }

    override fun heuristic(
        x: Int,
        y: Int,
        z: Int,
    ): Double = goals.minOfOrNull { it.heuristic(x, y, z) } ?: Double.MAX_VALUE

    override fun heuristic(): Double = goals.minOfOrNull { it.heuristic() } ?: Double.MAX_VALUE

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GoalComposite) return false
        return goals.contentEquals(other.goals)
    }

    override fun hashCode(): Int = goals.contentHashCode()

    override fun toString(): String = "GoalComposite${goals.contentToString()}"

    fun goals(): Array<out Goal> = goals
}
