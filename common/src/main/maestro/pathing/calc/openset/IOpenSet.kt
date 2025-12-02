package maestro.pathing.calc.openset

import maestro.pathing.calc.PathNode

/** An open set for A* or similar graph search algorithm */
interface IOpenSet {
    /**
     * Inserts the specified node into the heap
     *
     * @param node The node
     */
    fun insert(node: PathNode)

    /**
     * @return `true` if the heap has no elements; `false` otherwise.
     */
    fun isEmpty(): Boolean

    /**
     * Removes and returns the minimum element in the heap.
     *
     * @return The minimum element in the heap
     */
    fun removeLowest(): PathNode

    /**
     * A faster path has been found to this node, decreasing its cost. Perform a decrease-key
     * operation.
     *
     * @param node The node
     */
    fun update(node: PathNode)
}
