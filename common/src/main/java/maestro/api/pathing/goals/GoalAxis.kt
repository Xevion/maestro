package maestro.api.pathing.goals

import maestro.api.MaestroAPI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class GoalAxis : Goal {
    override fun isInGoal(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean = y == MaestroAPI.getSettings().axisHeight.value && (x == 0 || z == 0 || abs(x) == abs(z))

    override fun heuristic(
        x0: Int,
        y: Int,
        z0: Int,
    ): Double {
        val x = abs(x0)
        val z = abs(z0)

        val shrt = min(x, z)
        val lng = max(x, z)
        val diff = lng - shrt

        val flatAxisDistance = min(x.toDouble(), min(z.toDouble(), diff * SQRT_2_OVER_2))

        return flatAxisDistance * MaestroAPI.getSettings().costHeuristic.value +
            GoalYLevel.calculate(MaestroAPI.getSettings().axisHeight.value, y)
    }

    override fun equals(other: Any?): Boolean = other is GoalAxis

    override fun hashCode(): Int = 201385781

    override fun toString(): String = "GoalAxis"

    companion object {
        private val SQRT_2_OVER_2 = sqrt(2.0) / 2
    }
}
