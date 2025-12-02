package maestro.api.pathing.goals

import maestro.api.utils.SettingsUtil
import maestro.api.utils.pack
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import kotlin.math.abs

/**
 * Dig a tunnel in a certain direction, but if you have to deviate from the path, go back to where you started
 */
data class GoalStrictDirection(
    @JvmField val x: Int,
    @JvmField val y: Int,
    @JvmField val z: Int,
    @JvmField val dx: Int,
    @JvmField val dz: Int,
) : Goal {
    constructor(origin: BlockPos, direction: Direction) : this(
        origin.x,
        origin.y,
        origin.z,
        direction.stepX,
        direction.stepZ,
    ) {
        require(dx != 0 || dz != 0) { direction.toString() }
    }

    override fun isInGoal(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean = false

    override fun heuristic(
        x: Int,
        y: Int,
        z: Int,
    ): Double {
        val distanceFromStartInDesiredDirection = (x - this.x) * dx + (z - this.z) * dz
        val distanceFromStartInIncorrectDirection = abs((x - this.x) * dz) + abs((z - this.z) * dx)
        val verticalDistanceFromStart = abs(y - this.y)

        // we want heuristic to decrease as desiredDirection increases
        var heuristic = -distanceFromStartInDesiredDirection * 100.0
        heuristic += distanceFromStartInIncorrectDirection * 1000
        heuristic += verticalDistanceFromStart * 1000
        return heuristic
    }

    override fun heuristic(): Double = Double.NEGATIVE_INFINITY

    override fun hashCode(): Int {
        var hash = pack(x, y, z).packed.toInt()
        hash = hash * 630627507 + dx
        hash = hash * -283028380 + dz
        return hash
    }

    override fun toString(): String =
        "GoalStrictDirection[${SettingsUtil.maybeCensor(x)},${SettingsUtil.maybeCensor(y)}," +
            "${SettingsUtil.maybeCensor(z)},${SettingsUtil.maybeCensor(dx)},${SettingsUtil.maybeCensor(dz)}]"
}
