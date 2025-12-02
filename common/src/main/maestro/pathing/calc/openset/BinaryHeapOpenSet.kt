package maestro.pathing.calc.openset

import maestro.pathing.calc.PathNode

/** A binary heap implementation of an open set. This is the one used in the AStarPathFinder. */
class BinaryHeapOpenSet(
    initialSize: Int = INITIAL_CAPACITY,
) : IOpenSet {
    /** The array backing the heap */
    private var array: Array<PathNode?> = arrayOfNulls(initialSize)

    /** The size of the heap */
    private var size: Int = 0

    fun size(): Int = size

    override fun insert(node: PathNode) {
        if (size >= array.size - 1) {
            array = array.copyOf(array.size shl 1)
        }
        size++
        node.heapPosition = size
        array[size] = node
        update(node)
    }

    override fun update(node: PathNode) {
        var index = node.heapPosition
        var parentInd = index shr 1
        val cost = node.combinedCost
        while (index > 1) {
            val parentNode = array[parentInd]!!
            if (parentNode.combinedCost <= cost) break

            array[index] = parentNode
            array[parentInd] = node
            node.heapPosition = parentInd
            parentNode.heapPosition = index
            index = parentInd
            parentInd = index shr 1
        }
    }

    override fun isEmpty(): Boolean = size == 0

    override fun removeLowest(): PathNode {
        check(size != 0) { "Cannot remove from empty heap" }

        val result = array[1]!!
        val value = array[size]!!
        array[1] = value
        value.heapPosition = 1
        array[size] = null
        size--
        result.heapPosition = -1

        if (size < 2) {
            return result
        }

        var index = 1
        var smallerChild = 2
        val cost = value.combinedCost

        do {
            var smallerChildNode = array[smallerChild]!!
            var smallerChildCost = smallerChildNode.combinedCost

            if (smallerChild < size) {
                val rightChildNode = array[smallerChild + 1]!!
                val rightChildCost = rightChildNode.combinedCost
                if (smallerChildCost > rightChildCost) {
                    smallerChild++
                    smallerChildCost = rightChildCost
                    smallerChildNode = rightChildNode
                }
            }

            if (cost <= smallerChildCost) {
                break
            }

            array[index] = smallerChildNode
            array[smallerChild] = value
            value.heapPosition = smallerChild
            smallerChildNode.heapPosition = index
            index = smallerChild
            smallerChild = smallerChild shl 1
        } while (smallerChild <= size)

        return result
    }

    /**
     * Rebuild heap with new epsilon coefficient.
     * Recalculates all combinedCosts and re-heapifies in O(n) time.
     */
    fun rebuildWithEpsilon(epsilon: Double) {
        // Recalculate f-values for all nodes
        for (i in 1..size) {
            val node = array[i]!!
            node.combinedCost = node.cost + node.estimatedCostToGoal * epsilon
        }

        // Re-heapify from bottom up
        for (i in (size / 2) downTo 1) {
            siftDown(i)
        }
    }

    /**
     * Sift node down to restore min-heap property.
     */
    private fun siftDown(startIndex: Int) {
        val value = array[startIndex]!!
        var index = startIndex
        var smallerChild = index shl 1
        val cost = value.combinedCost

        while (smallerChild <= size) {
            var smallerChildNode = array[smallerChild]!!
            var smallerChildCost = smallerChildNode.combinedCost

            if (smallerChild < size) {
                val rightChildNode = array[smallerChild + 1]!!
                val rightChildCost = rightChildNode.combinedCost
                if (smallerChildCost > rightChildCost) {
                    smallerChild++
                    smallerChildCost = rightChildCost
                    smallerChildNode = rightChildNode
                }
            }

            if (cost <= smallerChildCost) {
                break
            }

            array[index] = smallerChildNode
            array[smallerChild] = value
            value.heapPosition = smallerChild
            smallerChildNode.heapPosition = index

            index = smallerChild
            smallerChild = index shl 1
        }
    }

    companion object {
        /** The initial capacity of the heap (2^10) */
        private const val INITIAL_CAPACITY = 1024
    }
}
