package maestro.pathing.calc.openset

import maestro.pathing.calc.PathNode

/**
 * A linked list implementation of an open set. This is the original implementation from MineBot. It
 * has incredibly fast insert performance, at the cost of O(n) removeLowest. It sucks.
 * BinaryHeapOpenSet results in more than 10x more nodes considered in 4 seconds.
 */
internal class LinkedListOpenSet : IOpenSet {
    private var first: Node? = null

    override fun isEmpty(): Boolean = first == null

    override fun insert(node: PathNode) {
        val listNode = Node(node, first)
        first = listNode
    }

    override fun update(node: PathNode) {
        // No-op for linked list implementation
    }

    override fun removeLowest(): PathNode {
        check(first != null) { "Cannot remove from empty list" }
        val currentFirst = first!!

        var current = currentFirst.nextOpen
        if (current == null) {
            first = null
            return currentFirst.`val`
        }

        var previous = currentFirst
        var bestValue = currentFirst.`val`.combinedCost
        var bestNode = currentFirst
        var beforeBest: Node? = null

        while (current != null) {
            val comp = current.`val`.combinedCost
            if (comp < bestValue) {
                bestValue = comp
                bestNode = current
                beforeBest = previous
            }
            previous = current
            current = current.nextOpen
        }

        if (beforeBest == null) {
            first = first?.nextOpen
            bestNode.nextOpen = null
            return bestNode.`val`
        }

        beforeBest.nextOpen = bestNode.nextOpen
        bestNode.nextOpen = null
        return bestNode.`val`
    }

    /** Wrapper node with next pointer */
    private data class Node(
        val `val`: PathNode,
        var nextOpen: Node? = null,
    )
}
