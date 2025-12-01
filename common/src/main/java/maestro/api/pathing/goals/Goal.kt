package maestro.api.pathing.goals

import net.minecraft.core.BlockPos

/** An abstract Goal for pathing, can be anything from a specific block to just a Y coordinate. */
interface Goal {
    /**
     * Returns whether the specified position meets the requirement for this goal based.
     *
     * @param x The goal X position
     * @param y The goal Y position
     * @param z The goal Z position
     * @return Whether it satisfies this goal
     */
    fun isInGoal(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean

    /**
     * Estimate the number of ticks it will take to get to the goal
     *
     * @param x The goal X position
     * @param y The goal Y position
     * @param z The goal Z position
     * @return The estimate number of ticks to satisfy the goal
     */
    fun heuristic(
        x: Int,
        y: Int,
        z: Int,
    ): Double

    fun isInGoal(pos: BlockPos): Boolean = isInGoal(pos.x, pos.y, pos.z)

    fun heuristic(pos: BlockPos): Double = heuristic(pos.x, pos.y, pos.z)

    /**
     * Returns the heuristic at the goal. i.e. `heuristic() == heuristic(x,y,z)` when `isInGoal(x,y,z) == true`
     * This is needed by `PathingBehavior#estimatedTicksToGoal` because some Goals actually do not have a heuristic
     * of 0 when that condition is met
     *
     * @return The estimate number of ticks to satisfy the goal when the goal is already satisfied
     */
    fun heuristic(): Double = 0.0
}
