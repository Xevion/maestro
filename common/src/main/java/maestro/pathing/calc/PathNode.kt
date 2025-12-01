package maestro.pathing.calc

import maestro.api.pathing.goals.Goal
import maestro.api.pathing.movement.ActionCosts
import maestro.api.pathing.movement.IMovement
import maestro.api.utils.BetterBlockPos
import maestro.api.utils.SettingsUtil
import maestro.pathing.movement.Moves

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
     * operations.
     */
    @JvmField
    var heapPosition: Int = -1

    /**
     * The movement used to reach this node from previous node. Storing the movement directly
     * eliminates the need to reconstruct it later via runBackwards(). If null, fallback to
     * movementOrdinal lookup for backward compatibility.
     */
    @JvmField
    var previousMovement: IMovement? = null

    /**
     * The movement enum ordinal used to reach this node from previous node. -1 indicates no
     * movement recorded (start node or legacy path). Kept for backward compatibility during
     * migration; prefer using previousMovement when available.
     *
     * @deprecated Use [previousMovement] instead for direct movement access
     */
    @Deprecated("Use previousMovement instead")
    @JvmField
    var movementOrdinal: Byte = -1

    init {
        require(!estimatedCostToGoal.isNaN()) {
            "$goal calculated implausible heuristic NaN at " +
                "${SettingsUtil.maybeCensor(x)} ${SettingsUtil.maybeCensor(y)} ${SettingsUtil.maybeCensor(z)}"
        }
    }

    @JvmName("isOpen")
    fun isOpen(): Boolean = heapPosition != -1

    /**
     * Get the Moves enum value that was used to reach this node from its previous node.
     *
     * @return The Moves enum, or null if not recorded
     */
    @JvmName("getMovement")
    fun getMovement(): Moves? = if (movementOrdinal < 0) null else Moves.entries[movementOrdinal.toInt()]

    /**
     * TODO: Possibly reimplement hashCode and equals. They are necessary for this class to function
     * but they could be done better
     *
     * @return The hash code value for this [PathNode]
     */
    override fun hashCode(): Int = BetterBlockPos.longHash(x, y, z).toInt()

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
