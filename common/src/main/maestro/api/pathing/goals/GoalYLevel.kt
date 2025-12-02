package maestro.api.pathing.goals

import maestro.api.pathing.movement.ActionCosts
import maestro.api.utils.SettingsUtil

/** Useful for mining (getting to diamond / iron level) */
data class GoalYLevel(
    @JvmField val level: Int,
) : Goal,
    ActionCosts {
    override fun isInGoal(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean = y == level

    override fun heuristic(
        x: Int,
        y: Int,
        z: Int,
    ): Double = calculate(level, y)

    override fun hashCode(): Int = level * 1271009915

    override fun toString(): String = "GoalYLevel[${SettingsUtil.maybeCensor(level)}]"

    companion object {
        @JvmStatic
        fun calculate(
            goalY: Int,
            currentY: Int,
        ): Double =
            when {
                currentY > goalY -> ActionCosts.FALL_N_BLOCKS_COST[2] / 2 * (currentY - goalY) // need to descend
                currentY < goalY -> (goalY - currentY) * ActionCosts.JUMP_ONE_BLOCK_COST // need to ascend
                else -> 0.0
            }
    }
}
