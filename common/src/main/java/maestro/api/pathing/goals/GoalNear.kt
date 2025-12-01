package maestro.api.pathing.goals

import it.unimi.dsi.fastutil.doubles.DoubleOpenHashSet
import maestro.api.utils.SettingsUtil
import maestro.api.utils.interfaces.IGoalRenderPos
import maestro.api.utils.pack
import net.minecraft.core.BlockPos
import kotlin.math.ceil
import kotlin.math.sqrt

data class GoalNear(
    private val x: Int,
    private val y: Int,
    private val z: Int,
    private val rangeSq: Int,
) : Goal,
    IGoalRenderPos {
    constructor(pos: BlockPos, range: Int) : this(pos.x, pos.y, pos.z, range * range)

    override fun isInGoal(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val xDiff = x - this.x
        val yDiff = y - this.y
        val zDiff = z - this.z
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff <= rangeSq
    }

    override fun heuristic(
        x: Int,
        y: Int,
        z: Int,
    ): Double {
        val xDiff = x - this.x
        val yDiff = y - this.y
        val zDiff = z - this.z
        return GoalBlock.calculate(xDiff.toDouble(), yDiff, zDiff.toDouble())
    }

    override fun heuristic(): Double {
        // TODO less hacky solution
        val range = ceil(sqrt(rangeSq.toDouble())).toInt()
        val maybeAlwaysInside = DoubleOpenHashSet() // see pull request #1978
        var minOutside = Double.POSITIVE_INFINITY

        for (dx in -range..range) {
            for (dy in -range..range) {
                for (dz in -range..range) {
                    val h = heuristic(x + dx, y + dy, z + dz)
                    if (h < minOutside && isInGoal(x + dx, y + dy, z + dz)) {
                        maybeAlwaysInside.add(h)
                    } else {
                        minOutside = minOf(minOutside, h)
                    }
                }
            }
        }

        var maxInside = Double.NEGATIVE_INFINITY
        val it = maybeAlwaysInside.iterator()
        while (it.hasNext()) {
            val inside = it.nextDouble()
            if (inside < minOutside) {
                maxInside = maxOf(maxInside, inside)
            }
        }
        return maxInside
    }

    override fun getGoalPos(): BlockPos = BlockPos(x, y, z)

    override fun hashCode(): Int = pack(x, y, z).packed.toInt() + rangeSq

    override fun toString(): String =
        "GoalNear{x=${SettingsUtil.maybeCensor(x)}, y=${SettingsUtil.maybeCensor(y)}, z=${SettingsUtil.maybeCensor(z)}, rangeSq=$rangeSq}"
}
