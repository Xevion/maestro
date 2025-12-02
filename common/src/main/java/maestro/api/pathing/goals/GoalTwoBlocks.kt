package maestro.api.pathing.goals

import maestro.api.utils.SettingsUtil
import maestro.api.utils.interfaces.IGoalRenderPos
import maestro.api.utils.pack
import net.minecraft.core.BlockPos

/**
 * Useful if the goal is just to mine a block. This goal will be satisfied if the specified [BlockPos] is at to or
 * above the specified position for this goal.
 */
open class GoalTwoBlocks(
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
    ): Boolean = x == this.x && (y == this.y || y == this.y - 1) && z == this.z

    override fun heuristic(
        x: Int,
        y: Int,
        z: Int,
    ): Double {
        val xDiff = x - this.x
        val yDiff = y - this.y
        val zDiff = z - this.z
        return GoalBlock.calculate(xDiff.toDouble(), if (yDiff < 0) yDiff + 1 else yDiff, zDiff.toDouble())
    }

    override fun getGoalPos(): BlockPos = BlockPos(x, y, z)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GoalTwoBlocks) return false
        return x == other.x && y == other.y && z == other.z
    }

    override fun hashCode(): Int = (pack(x, y, z).packed * 516508351).toInt()

    override fun toString(): String =
        "GoalTwoBlocks[${SettingsUtil.maybeCensor(x)},${SettingsUtil.maybeCensor(y)},${SettingsUtil.maybeCensor(z)}]"
}
