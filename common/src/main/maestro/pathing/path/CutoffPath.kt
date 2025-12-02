package maestro.pathing.path

import maestro.api.pathing.calc.IPath
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.movement.IMovement
import maestro.api.utils.PackedBlockPos
import maestro.utils.pathing.PathBase

class CutoffPath : PathBase {
    private val path: List<PackedBlockPos>
    private val movements: MutableList<IMovement>
    override val numNodesConsidered: Int
    override val goal: Goal

    constructor(prev: IPath, firstPositionToInclude: Int, lastPositionToInclude: Int) {
        path = prev.positions().subList(firstPositionToInclude, lastPositionToInclude + 1)
        movements =
            prev.movements().subList(firstPositionToInclude, lastPositionToInclude).toMutableList()
        numNodesConsidered = prev.numNodesConsidered
        goal = prev.goal
        sanityCheck()
    }

    constructor(prev: IPath, lastPositionToInclude: Int) : this(prev, 0, lastPositionToInclude)

    override fun movements(): List<IMovement> = movements

    override fun positions(): List<PackedBlockPos> = path

    override fun replaceMovement(
        index: Int,
        newMovement: IMovement,
    ) {
        require(index in movements.indices) {
            "Index $index out of bounds for movements list of size ${movements.size}"
        }
        movements[index] = newMovement
    }
}
