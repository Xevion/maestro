package maestro.pathing.calc

import maestro.api.pathing.goals.Goal
import maestro.api.pathing.movement.ActionCosts
import maestro.api.pathing.movement.IMovement
import maestro.api.utils.SettingsUtil

/** A node in the path, containing the cost and steps to get to it. */
class PathNode(
    /** The position of this node */
    @JvmField val x: Int,
    @JvmField val y: Int,
    @JvmField val z: Int,
    goal: Goal,
) {
    /** Cached, should always be equal to goal.heuristic(pos) */
    @JvmField
    val estimatedCostToGoal: Double = goal.heuristic(x, y, z)

    /** Total cost of getting from start to here. Mutable and changed by PathFinder */
    @JvmField
    var cost: Double = ActionCosts.COST_INF

    /** Should always be equal to estimatedCostToGoal + cost. Mutable and changed by PathFinder */
    @JvmField
    var combinedCost: Double = 0.0

    /**
     * In the graph search, what previous node contributed to the cost. Mutable and changed by
     * PathFinder
     */
    @JvmField
    var previous: PathNode? = null

    /**
     * Where is this node in the array flattenization of the binary heap? Needed for decrease-key
     * operations. Internal visibility for better encapsulation.
     */
    internal var heapPosition: Int = -1

    /**
     * The movement used to reach this node from previous node. Storing the movement directly
     * eliminates the need to reconstruct it later.
     */
    @JvmField
    var previousMovement: IMovement? = null

    init {
        require(!estimatedCostToGoal.isNaN()) {
            "$goal calculated implausible heuristic NaN at " +
                "${SettingsUtil.maybeCensor(x)} ${SettingsUtil.maybeCensor(y)} ${SettingsUtil.maybeCensor(z)}"
        }
    }

    @JvmName("isOpen")
    fun isOpen(): Boolean = heapPosition != -1

    /**
     * PathNode equality is based solely on position (x, y, z).
     * This is used by pathfinding algorithms to identify unique nodes.
     *
     * Note: PathNode is stored in Long2ObjectOpenHashMap keyed by packed position,
     * so this hashCode/equals is only used if PathNode is placed in standard collections.
     */
    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        result = 31 * result + z
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PathNode) return false

        return x == other.x && y == other.y && z == other.z
    }
}
