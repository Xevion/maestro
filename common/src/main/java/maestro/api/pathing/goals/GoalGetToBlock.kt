package maestro.api.pathing.goals

import maestro.api.utils.BetterBlockPos
import maestro.api.utils.SettingsUtil
import maestro.api.utils.interfaces.IGoalRenderPos
import net.minecraft.core.BlockPos
import kotlin.math.abs

/** Don't get into the block, but get directly adjacent to it. Useful for chests. */
open class GoalGetToBlock(
    @JvmField val x: Int,
    @JvmField val y: Int,
    @JvmField val z: Int,
) : Goal,
    IGoalRenderPos {
    constructor(pos: BlockPos) : this(pos.x, pos.y, pos.z)

    override fun getGoalPos(): BlockPos = BlockPos(x, y, z)

    override fun isInGoal(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val xDiff = x - this.x
        val yDiff = y - this.y
        val zDiff = z - this.z
        return abs(xDiff) + abs(if (yDiff < 0) yDiff + 1 else yDiff) + abs(zDiff) <= 1
    }

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GoalGetToBlock) return false
        return x == other.x && y == other.y && z == other.z
    }

    override fun hashCode(): Int = (BetterBlockPos.longHash(x, y, z) * -49639096).toInt()

    override fun toString(): String =
        "GoalGetToBlock{x=${SettingsUtil.maybeCensor(x)},y=${SettingsUtil.maybeCensor(y)},z=${SettingsUtil.maybeCensor(z)}}"
}
