package maestro.pathing.goals

import maestro.Agent
import maestro.pathing.movement.ActionCosts
import maestro.utils.SettingsUtil

/** Useful for mining (getting to diamond / iron level) */
data class GoalYLevel(
    @JvmField val level: Int,
) : Goal {
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
        fun calculate(
            goalY: Int,
            currentY: Int,
        ): Double {
            val baseCost =
                when {
                    // Use realistic descent cost: WALK_OFF_BLOCK + FALL[1] ≈ 4.025 ticks per block
                    // Previously used FALL_N_BLOCKS_COST[2] / 2 ≈ 0.314 ticks, which was 13x underestimate
                    currentY > goalY -> ActionCosts.WALK_OFF_BLOCK_COST * (currentY - goalY)
                    currentY < goalY -> (goalY - currentY) * ActionCosts.JUMP_ONE_BLOCK_COST // need to ascend
                    else -> 0.0
                }
            // Apply costHeuristic multiplier to match horizontal weighting
            // This ensures vertical progress is valued equally with horizontal progress in A* priority
            return baseCost *
                Agent
                    .getPrimaryAgent()
                    .getSettings()
                    .costHeuristic.value
        }
    }
}
