package maestro.api.pathing.goals

import maestro.api.utils.SettingsUtil
import maestro.api.utils.interfaces.IGoalRenderPos
import maestro.api.utils.pack
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
        "GoalBlock{x=${SettingsUtil.maybeCensor(x)},y=${SettingsUtil.maybeCensor(y)},z=${SettingsUtil.maybeCensor(z)}}"

    companion object {
        @JvmStatic
        fun calculate(
            xDiff: Double,
            yDiff: Int,
            zDiff: Double,
        ): Double {
            var heuristic = 0.0

            // Vertical heuristic: Use swimming cost (3.5) as minimum estimate
            // This provides a better heuristic for underwater goals than terrestrial movement costs
            // TODO: Ideally this would detect if we're actually in water, but using the minimum
            // of swimming vs terrestrial costs ensures the heuristic is admissible (never overestimates)
            val verticalHeuristic = GoalYLevel.calculate(0, yDiff)
            val swimmingVerticalHeuristic = abs(yDiff) * 3.5 // SWIM_UP/DOWN cost
            heuristic += min(verticalHeuristic, swimmingVerticalHeuristic)

            // use the pythagorean and manhattan mixture from GoalXZ
            heuristic += GoalXZ.calculate(xDiff, zDiff)
            return heuristic
        }
    }
}
