package maestro.api.pathing.calc

import maestro.api.Settings
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.movement.IMovement
import maestro.api.utils.PackedBlockPos

interface IPath {
    /**
     * Ordered list of movements to carry out. movements()[i].src should equal positions()[i]
     * movements()[i].dest should equal positions()[i+1] movements().size should equal
     * positions().size-1
     *
     * @return All the movements to carry out
     */
    fun movements(): List<IMovement>

    /**
     * All positions along the way. Should begin with the same as src and end with the same as dest
     *
     * @return All the positions along this path
     */
    fun positions(): List<PackedBlockPos>

    /**
     * This path is actually going to be executed in the world. Do whatever additional processing is
     * required. (as opposed to Path objects that are just constructed every frame for rendering)
     *
     * @return The result of path post-processing
     */
    fun postProcess(): IPath = throw UnsupportedOperationException()

    /**
     * Returns the number of positions in this path. Equivalent to `positions().size`.
     *
     * @return Number of positions in this path
     */
    fun length(): Int = positions().size

    /** The goal that this path was calculated towards */
    val goal: Goal

    /** The number of nodes that were considered during calculation before this path was found. */
    val numNodesConsidered: Int

    /**
     * The start position of this path. This is the first element in the [List] that is returned by
     * [positions].
     */
    val src: PackedBlockPos
        get() = positions().first()

    /**
     * The end position of this path. This is the last element in the [List] that is returned by
     * [positions].
     */
    val dest: PackedBlockPos
        get() = positions().last()

    /**
     * Returns the estimated number of ticks to complete the path from the given node index.
     *
     * @param pathPosition The index of the node we're calculating from
     * @return The estimated number of ticks remaining from the given position
     */
    fun ticksRemainingFrom(pathPosition: Int): Double {
        val movements = movements()
        return (pathPosition until movements.size).sumOf { movements[it].cost }
    }

    /**
     * Cuts off this path at the loaded chunk border, and returns the resulting path. Default
     * implementation just returns this path, without the intended functionality.
     *
     * The argument is supposed to be a BlockStateInterface
     *
     * @param bsi The block state lookup, highly cursed
     * @return The result of this cut-off operation
     */
    fun cutoffAtLoadedChunks(bsi: Any): IPath = throw UnsupportedOperationException()

    /**
     * Cuts off this path using the min length and cutoff factor settings, and returns the resulting
     * path. Default implementation just returns this path, without the intended functionality.
     *
     * @param destination The end goal of this path
     * @return The result of this cut-off operation
     * @see Settings.pathCutoffMinimumLength
     * @see Settings.pathCutoffFactor
     */
    fun staticCutoff(destination: Goal?): IPath = throw UnsupportedOperationException()

    /**
     * Replaces a movement at the specified index with a new movement. This is used by the path
     * executor to try alternative movements when the original fails.
     *
     * @param index The index of the movement to replace
     * @param newMovement The new movement to use
     * @throws UnsupportedOperationException if this path implementation does not support movement
     *   replacement
     */
    fun replaceMovement(
        index: Int,
        newMovement: IMovement,
    ): Unit = throw UnsupportedOperationException()

    /** Performs a series of checks to ensure that the assembly of the path went as expected. */
    fun sanityCheck() {
        val path = positions()
        val movements = movements()
        require(src == path.first()) { "Start node does not equal first path element" }
        require(dest == path.last()) { "End node does not equal last path element" }
        require(path.size == movements.size + 1) { "Size of path array is unexpected" }

        val seenSoFar = HashSet<PackedBlockPos>()
        for (i in 0 until path.size - 1) {
            val src = path[i]
            val dest = path[i + 1]
            val movement = movements[i]
            require(src == movement.src) { "Path source is not equal to the movement source" }
            require(dest == movement.dest) {
                "Path destination is not equal to the movement destination"
            }
            require(!seenSoFar.contains(src)) { "Path doubles back on itself, making a loop" }
            seenSoFar.add(src)
        }
    }
}
