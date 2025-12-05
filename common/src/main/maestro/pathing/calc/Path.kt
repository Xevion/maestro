package maestro.pathing.calc

import maestro.api.pathing.calc.IPath
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.movement.IMovement
import maestro.api.utils.MaestroLogger
import maestro.api.utils.PackedBlockPos
import maestro.pathing.PathBase
import maestro.pathing.movement.CalculationContext
import maestro.pathing.movement.Movement
import maestro.pathing.path.CutoffPath
import org.slf4j.Logger

/** A node based implementation of IPath */
internal class Path(
    realStart: PackedBlockPos,
    start: PathNode,
    end: PathNode,
    private val numNodes: Int,
    goal_: Goal,
    private val context: CalculationContext,
) : PathBase() {
    override val goal: Goal = goal_
    override val numNodesConsidered: Int get() = numNodes

    /** The start position of this path */
    private val start: PackedBlockPos

    /** The end position of this path */
    private val end: PackedBlockPos = PackedBlockPos(end.x, end.y, end.z)

    /**
     * The blocks on the path. Guaranteed that path.get(0) equals start and path.get(path.size()-1)
     * equals end
     */
    private val path: List<PackedBlockPos>

    private val movements: MutableList<Movement> = mutableListOf()

    private val nodes: List<PathNode>

    @Volatile
    private var verified: Boolean = false

    init {
        // Build path by walking backwards from end to start
        var current: PathNode? = end
        val tempPath = mutableListOf<PackedBlockPos>()
        val tempNodes = mutableListOf<PathNode>()

        while (current != null) {
            tempNodes.add(current)
            tempPath.add(PackedBlockPos(current.x, current.y, current.z))
            current = current.previous
        }

        // If the position the player is at is different from the position we told A* to start from,
        // and A* gave us no movements, then add a fake node that will allow a movement to be
        // created that gets us to the single position in the path.
        // See PathingBehavior#createPathfinder and https://github.com/cabaletta/baritone/pull/4519
        val startNodePos = PackedBlockPos(start.x, start.y, start.z)
        if (realStart != startNodePos && start == end) {
            this.start = realStart
            val fakeNode =
                PathNode(realStart.x, realStart.y, realStart.z, goal).apply {
                    cost = 0.0
                }
            tempNodes.add(fakeNode)
            tempPath.add(realStart)
        } else {
            this.start = startNodePos
        }

        // Nodes are traversed last to first so we need to reverse the list
        path = tempPath.reversed()
        nodes = tempNodes.reversed()
    }

    private fun assembleMovements(): Boolean {
        if (path.isEmpty() || movements.isNotEmpty()) {
            throw IllegalStateException("Path must not be empty")
        }

        for (i in 0 until path.size - 1) {
            val nextNode = nodes[i + 1]

            // Get movement from node - all paths should have previousMovement set
            val prevMovement = nextNode.previousMovement
            if (prevMovement == null) {
                // Movement not recorded - path became impossible
                log
                    .atDebug()
                    .addKeyValue("source", path[i])
                    .addKeyValue("dest", path[i + 1])
                    .log("Movement not recorded - path became impossible during calculation")
                return true
            }

            val move = prevMovement as Movement
            // Verify destination matches (sanity check)
            if (move.dest == path[i + 1]) {
                movements.add(move)
            } else {
                // Shouldn't happen - log and fail
                log
                    .atDebug()
                    .addKeyValue("expected_dest", path[i + 1])
                    .addKeyValue("actual_dest", move.dest)
                    .log("Stored movement destination mismatch - path became impossible")
                return true
            }
        }
        return false
    }

    override fun postProcess(): IPath {
        if (verified) {
            throw IllegalStateException("Path must not be verified twice")
        }
        verified = true
        val failed = assembleMovements()
        movements.forEach { it.checkLoadedChunk(context) }

        if (failed) { // at least one movement became impossible during calculation
            val res = CutoffPath(this, movements().size)
            if (res.movements().size != movements.size) {
                throw IllegalStateException("Path has wrong size after cutoff")
            }
            return res
        }
        // more post-processing here
        sanityCheck()
        return this
    }

    override fun movements(): List<IMovement> {
        if (!verified) {
            // edge case note: this is called during verification
            throw IllegalStateException("Path not yet verified")
        }
        return movements.toList()
    }

    override fun positions(): List<PackedBlockPos> = path

    override fun replaceMovement(
        index: Int,
        newMovement: IMovement,
    ) {
        if (!verified) {
            throw IllegalStateException("Cannot replace movement in unverified path")
        }
        if (index < 0 || index >= movements.size) {
            throw IndexOutOfBoundsException(
                "Index $index out of bounds for movements list of size ${movements.size}",
            )
        }
        movements[index] = newMovement as Movement
    }

    companion object {
        private val log: Logger = MaestroLogger.get("path")
    }
}
