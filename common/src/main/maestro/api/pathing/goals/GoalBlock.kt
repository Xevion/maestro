package maestro.api.pathing.goals

import maestro.Agent
import maestro.api.utils.SettingsUtil
import maestro.api.utils.pack
import maestro.rendering.IGoalRenderPos
import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.min

/** A specific BlockPos goal */
open class GoalBlock(
    @JvmField val x: Int,
    @JvmField val y: Int,
    @JvmField val z: Int,
) : Goal,
    IGoalRenderPos {
    constructor(pos: BlockPos) : this(pos.x, pos.y, pos.z)

    override fun isInGoal(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean = x == this.x && y == this.y && z == this.z

    override fun heuristic(
        x: Int,
        y: Int,
        z: Int,
    ): Double {
        val xDiff = x - this.x
        val yDiff = y - this.y
        val zDiff = z - this.z
        return calculate(xDiff.toDouble(), yDiff, zDiff.toDouble())
    }

    override fun getGoalPos(): BlockPos = BlockPos(x, y, z)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GoalBlock) return false
        return x == other.x && y == other.y && z == other.z
    }

    override fun hashCode(): Int = (pack(x, y, z).packed * 905165533).toInt()

    override fun toString(): String =
        "GoalBlock[${SettingsUtil.maybeCensor(x)},${SettingsUtil.maybeCensor(y)},${SettingsUtil.maybeCensor(z)}]"

    companion object {
        fun calculate(
            xDiff: Double,
            yDiff: Int,
            zDiff: Double,
        ): Double {
            var heuristic = 0.0

            // Vertical heuristic: Use swimming cost (3.5) as minimum estimate
            // This provides a better heuristic for underwater goals than terrestrial movement costs
            // Note: Both GoalYLevel.calculate() and swimmingVerticalHeuristic already include
            // costHeuristic multiplier, so we don't multiply again here (was causing 3.563^2 overestimate!)
            val verticalHeuristic = GoalYLevel.calculate(0, yDiff)
            val swimmingVerticalHeuristic =
                abs(yDiff) * 3.5 *
                    Agent
                        .getPrimaryAgent()
                        .getSettings()
                        .costHeuristic.value
            heuristic += min(verticalHeuristic, swimmingVerticalHeuristic)

            // use the pythagorean and manhattan mixture from GoalXZ
            heuristic += GoalXZ.calculate(xDiff, zDiff)
            return heuristic
        }
    }
}
