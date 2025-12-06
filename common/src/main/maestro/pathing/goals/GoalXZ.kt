package maestro.pathing.goals

import maestro.Agent
import maestro.utils.PackedBlockPos
import maestro.utils.SettingsUtil
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/** Useful for long-range goals that don't have a specific Y level. */
open class GoalXZ(
    @JvmField val x: Int,
    @JvmField val z: Int,
) : Goal {
    constructor(pos: PackedBlockPos) : this(pos.x, pos.z)

    override fun isInGoal(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean = x == this.x && z == this.z

    override fun heuristic(
        x: Int,
        y: Int,
        z: Int,
    ): Double {
        val xDiff = x - this.x
        val zDiff = z - this.z
        return calculate(xDiff.toDouble(), zDiff.toDouble())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GoalXZ) return false
        return x == other.x && z == other.z
    }

    override fun hashCode(): Int {
        var hash = 1791873246
        hash = hash * 222601791 + x
        hash = hash * -1331679453 + z
        return hash
    }

    override fun toString(): String = "GoalXZ[${SettingsUtil.maybeCensor(x)},${SettingsUtil.maybeCensor(z)}]"

    @JvmName("getX")
    fun getX(): Int = x

    @JvmName("getZ")
    fun getZ(): Int = z

    companion object {
        private val SQRT_2 = sqrt(2.0)

        fun calculate(
            xDiff: Double,
            zDiff: Double,
        ): Double {
            // This is a combination of pythagorean and manhattan distance
            // It takes into account the fact that pathing can either walk diagonally or forwards

            // It's not possible to walk forward 1 and right 2 in sqrt(5) time
            // It's really 1+sqrt(2) because it'll walk forward 1 then diagonally 1
            val x = abs(xDiff)
            val z = abs(zDiff)
            val (straight, diagonal) =
                if (x < z) {
                    (z - x) to x
                } else {
                    (x - z) to z
                }
            return (diagonal * SQRT_2 + straight) *
                Agent
                    .getPrimaryAgent()
                    .settings.costHeuristic.value
        }

        @JvmStatic
        fun fromDirection(
            origin: Vec3,
            yaw: Float,
            distance: Double,
        ): GoalXZ {
            val theta = Math.toRadians(yaw.toDouble()).toFloat()
            val x = origin.x - Mth.sin(theta) * distance
            val z = origin.z + Mth.cos(theta) * distance
            return GoalXZ(Mth.floor(x), Mth.floor(z))
        }
    }
}
