package maestro.debug.pathing

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import maestro.api.pathing.goals.Goal
import maestro.api.utils.PackedBlockPos
import maestro.pathing.calc.PathNode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Immutable snapshot of pathfinding state for post-hoc inspection.
 *
 * Captures all visited nodes with their costs and states, enabling detailed
 * analysis of the A* search after completion.
 */
data class PathfindingSnapshot(
    /** Starting position of the search */
    val startPos: PackedBlockPos,
    /** Goal used for this search */
    val goal: Goal,
    /** All visited nodes keyed by packed position */
    val nodes: Map<Long, SnapshotNode>,
    /** Positions still in open set at completion (frontier nodes) */
    val openSet: Set<Long>,
    /** Positions on the final path (null if no path found) */
    val finalPath: List<Long>?,
    /** Timing info for each search phase */
    val phases: List<PhaseInfo>,
    /** Total search duration in milliseconds */
    val totalDurationMs: Long,
    /** Total nodes explored during search */
    val nodesExplored: Int,
    /** Whether a path to the goal was found */
    val pathFound: Boolean,
) {
    /** Check if a position is on the final path */
    fun isOnPath(packedPos: Long): Boolean = finalPath?.contains(packedPos) == true

    /** Check if a position was in the open set at completion */
    fun isInOpenSet(packedPos: Long): Boolean = openSet.contains(packedPos)

    /** Check if a position was in the closed set (explored but not in open set) */
    fun isInClosedSet(packedPos: Long): Boolean = nodes.containsKey(packedPos) && !openSet.contains(packedPos)

    /**
     * Gets nodes physically near the specified node (within Manhattan distance).
     *
     * Useful for rendering labels only for nodes near the hovered node.
     *
     * @param node The center node
     * @param maxDistance Maximum Manhattan distance (1 = adjacent only, 2 = up to 2 blocks away)
     * @return List of nearby nodes including the center node
     */
    fun getNodesNear(
        node: SnapshotNode,
        maxDistance: Int = 2,
    ): List<SnapshotNode> =
        nodes.values.filter { other ->
            val dx = kotlin.math.abs(other.x - node.x)
            val dy = kotlin.math.abs(other.y - node.y)
            val dz = kotlin.math.abs(other.z - node.z)
            dx + dy + dz <= maxDistance
        }

    companion object {
        /**
         * Captures the current pathfinding state into an immutable snapshot.
         *
         * Called by AStarPathFinder at search completion to capture state
         * before the node map is cleared or garbage collected.
         *
         * @param nodeMap All nodes visited during the search
         * @param startNode The starting node of the search
         * @param goal The goal used for this search
         * @param phases Timing info for each search phase
         * @param totalDurationMs Total search duration
         * @param endNode The final node of the path (if found), or best node
         * @param pathFound Whether a complete path to goal was found
         */
        fun capture(
            nodeMap: Long2ObjectOpenHashMap<PathNode>,
            startNode: PathNode,
            goal: Goal,
            phases: List<PhaseInfo>,
            totalDurationMs: Long,
            endNode: PathNode?,
            pathFound: Boolean,
        ): PathfindingSnapshot {
            // Build set of open positions for quick lookup
            val openPositions = HashSet<Long>()
            for (entry in nodeMap.long2ObjectEntrySet()) {
                val node = entry.value
                if (node.isOpen()) {
                    openPositions.add(entry.longKey)
                }
            }

            // Convert all PathNodes to SnapshotNodes
            var discoveryOrder = 0
            val snapshotNodes = HashMap<Long, SnapshotNode>(nodeMap.size)

            for (entry in nodeMap.long2ObjectEntrySet()) {
                val packedPos = entry.longKey
                val node = entry.value

                val previousPackedPos =
                    node.previous?.let { prev ->
                        PackedBlockPos(prev.x, prev.y, prev.z).packed
                    }

                val movementType =
                    node.previousMovement?.let { movement ->
                        movement::class.java.simpleName
                    }

                snapshotNodes[packedPos] =
                    SnapshotNode(
                        x = node.x,
                        y = node.y,
                        z = node.z,
                        g = node.cost,
                        h = node.estimatedCostToGoal,
                        f = node.combinedCost,
                        previousPos = previousPackedPos,
                        movementType = movementType,
                        inOpenSet = openPositions.contains(packedPos),
                        discoveryOrder = discoveryOrder++,
                    )
            }

            // Build final path by walking backwards from end node
            val finalPath =
                if (endNode != null && pathFound) {
                    val path = ArrayList<Long>()
                    var current: PathNode? = endNode
                    while (current != null) {
                        path.add(PackedBlockPos(current.x, current.y, current.z).packed)
                        current = current.previous
                    }
                    path.reverse()
                    path
                } else {
                    null
                }

            return PathfindingSnapshot(
                startPos = PackedBlockPos(startNode.x, startNode.y, startNode.z),
                goal = goal,
                nodes = snapshotNodes,
                openSet = openPositions,
                finalPath = finalPath,
                phases = phases,
                totalDurationMs = totalDurationMs,
                nodesExplored = nodeMap.size,
                pathFound = pathFound,
            )
        }
    }
}

/**
 * Snapshot of a single pathfinding node's state.
 */
data class SnapshotNode(
    /** Block X coordinate */
    val x: Int,
    /** Block Y coordinate */
    val y: Int,
    /** Block Z coordinate */
    val z: Int,
    /** Actual cost from start (g-value) */
    val g: Double,
    /** Heuristic estimate to goal (h-value) */
    val h: Double,
    /** Combined cost f = g + h*epsilon */
    val f: Double,
    /** Packed position of parent node (null for start node) */
    val previousPos: Long?,
    /** Movement type used to reach this node (e.g., "MovementTraverse") */
    val movementType: String?,
    /** Whether this node was in the open set at search completion */
    val inOpenSet: Boolean,
    /** Order in which this node was first discovered (lower = earlier) */
    val discoveryOrder: Int,
) {
    /** Get packed position for this node */
    val packedPos: Long
        get() = PackedBlockPos(x, y, z).packed
}

/**
 * Timing and statistics for a search phase.
 *
 * A* uses progressive epsilon search with multiple phases:
 * - Phase 1: Standard A* (epsilon=1.0)
 * - Phase 2: Modest goal bias (epsilon=3.0)
 * - Phase 3: Greedy (epsilon=10.0)
 * - Phase 4: Very greedy (epsilon=30.0)
 * - Phase 5: Extremely greedy (epsilon=100.0)
 */
data class PhaseInfo(
    /** Phase index (0-based) */
    val index: Int,
    /** Epsilon multiplier for heuristic (1.0 = standard A*) */
    val epsilon: Double,
    /** Duration of this phase in milliseconds */
    val durationMs: Long,
    /** Nodes explored during this phase */
    val nodesExplored: Int,
)

/**
 * Thread-safe store for pathfinding snapshots.
 *
 * Stores the most recent snapshots for post-hoc inspection.
 * The store is accessed from both the pathfinding thread (writing)
 * and the render thread (reading).
 */
object PathfindingSnapshotStore {
    /** Maximum number of snapshots to retain */
    private const val MAX_SNAPSHOTS = 3

    /** Thread-safe list of snapshots, most recent first */
    private val snapshots = CopyOnWriteArrayList<PathfindingSnapshot>()

    /** Current/most recent snapshot for quick access */
    private val current = AtomicReference<PathfindingSnapshot?>(null)

    /**
     * The most recent snapshot, or null if none captured.
     */
    val currentSnapshot: PathfindingSnapshot?
        get() = current.get()

    /**
     * All stored snapshots (most recent first), up to [MAX_SNAPSHOTS].
     */
    val allSnapshots: List<PathfindingSnapshot>
        get() = snapshots.toList()

    /**
     * Number of snapshots currently stored.
     */
    val size: Int
        get() = snapshots.size

    /**
     * Stores a new snapshot, making it the current one.
     *
     * If [MAX_SNAPSHOTS] would be exceeded, the oldest snapshot is removed.
     * This method is thread-safe.
     */
    fun store(snapshot: PathfindingSnapshot) {
        // Add to front (most recent first)
        snapshots.add(0, snapshot)

        // Trim to max size
        while (snapshots.size > MAX_SNAPSHOTS) {
            snapshots.removeAt(snapshots.size - 1)
        }

        // Update current reference
        current.set(snapshot)
    }

    /**
     * Clears all stored snapshots.
     */
    fun clear() {
        snapshots.clear()
        current.set(null)
    }

    /**
     * Gets a snapshot by index (0 = most recent).
     *
     * @return The snapshot at the given index, or null if out of bounds.
     */
    fun getSnapshot(index: Int): PathfindingSnapshot? = snapshots.getOrNull(index)

    /**
     * Checks if any snapshots are available.
     */
    fun hasSnapshots(): Boolean = snapshots.isNotEmpty()
}
