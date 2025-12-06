package maestro.api.pathing.goals

import maestro.Agent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class GoalAxis : Goal {
    override fun isInGoal(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean =
        y ==
            Agent
                .getPrimaryAgent()
                .settings.axisHeight.value &&
            (x == 0 || z == 0 || abs(x) == abs(z))

    override fun heuristic(
        x: Int,
        y: Int,
        z: Int,
    ): Double {
        val absX = abs(x)
        val absZ = abs(z)

        val shrt = min(absX, absZ)
        val lng = max(absX, absZ)
        val diff = lng - shrt

        val flatAxisDistance = min(absX.toDouble(), min(absZ.toDouble(), diff * SQRT_2_OVER_2))

        return flatAxisDistance *
            Agent
                .getPrimaryAgent()
                .settings.costHeuristic.value +
            GoalYLevel.calculate(
                Agent
                    .getPrimaryAgent()
                    .settings.axisHeight.value,
                y,
            )
    }

    override fun equals(other: Any?): Boolean = other is GoalAxis

    override fun hashCode(): Int = 201385781

    override fun toString(): String = "GoalAxis"

    companion object {
        private val SQRT_2_OVER_2 = sqrt(2.0) / 2
    }
}
