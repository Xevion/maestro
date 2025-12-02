package maestro.pathing.calc

import maestro.api.pathing.goals.Goal
import maestro.api.pathing.movement.ActionCosts
import maestro.api.pathing.movement.IMovement
import maestro.api.utils.SettingsUtil
import maestro.api.utils.pack

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
     * TODO: Possibly reimplement hashCode and equals. They are necessary for this class to function
     * but they could be done better
     *
     * @return The hash code value for this [PathNode]
     */
    override fun hashCode(): Int = pack(x, y, z).packed.toInt()

    override fun equals(other: Any?): Boolean {
        // GOTTA GO FAST
        // ALL THESE CHECKS ARE FOR PEOPLE WHO WANT SLOW CODE
        // SKRT SKRT
        // if (obj == null || !(obj instanceof PathNode)) {
        //    return false;
        // }

        val otherNode = other as PathNode
        // return Objects.equals(this.pos, other.pos) && Objects.equals(this.goal, other.goal);

        return x == otherNode.x && y == otherNode.y && z == otherNode.z
    }
}
