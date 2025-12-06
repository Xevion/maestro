package maestro.pathing

import maestro.pathing.movement.ActionCosts

/**
 * The result of a calculated movement, with destination x, y, z, and the cost of performing the
 * movement
 */
class MutableMoveResult {
    @JvmField var x: Int = 0

    @JvmField var y: Int = 0

    @JvmField var z: Int = 0

    @JvmField var cost: Double = ActionCosts.COST_INF

    init {
        reset()
    }

    fun reset() {
        x = 0
        y = 0
        z = 0
        cost = ActionCosts.COST_INF
    }
}
