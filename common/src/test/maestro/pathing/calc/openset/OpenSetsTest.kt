package maestro.pathing.calc.openset

import maestro.api.pathing.goals.Goal
import maestro.pathing.calc.PathNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.random.Random

class OpenSetsTest {
    @ParameterizedTest
    @MethodSource("testSizes")
    fun testSize(size: Int) {
        println("Testing size $size")
        // Include LinkedListOpenSet even though it's not performant because I absolutely trust that
        // it behaves properly
        // I'm really testing the heap implementations against it as the ground truth
        val test = arrayOf<IOpenSet>(BinaryHeapOpenSet(), LinkedListOpenSet())
        for (set in test) {
            assertTrue(set.isEmpty())
        }

        // generate the pathnodes that we'll be testing the sets on
        val toInsert =
            Array(size) {
                // can't use an existing goal
                // because they use Maestro.getPrimaryAgent().getSettings()
                // and we can't do that because Minecraft itself isn't initted
                PathNode(
                    0,
                    0,
                    0,
                    object : Goal {
                        override fun isInGoal(
                            x: Int,
                            y: Int,
                            z: Int,
                        ): Boolean = false

                        override fun heuristic(
                            x: Int,
                            y: Int,
                            z: Int,
                        ): Double = 0.0
                    },
                ).apply {
                    combinedCost = Random.nextDouble()
                }
            }

        // create a list of what the first removals should be
        val copy = toInsert.toMutableList()
        copy.sortBy { it.combinedCost }
        val lowestQuarter = copy.subList(0, size / 4).toSet()

        // all opensets should be empty; nothing has been inserted yet
        for (set in test) {
            assertTrue(set.isEmpty())
        }

        println("Insertion")
        for (set in test) {
            val before = System.nanoTime() / 1000000L
            for (i in 0 until size) {
                set.insert(toInsert[i])
            }
            println("${set.javaClass} ${System.nanoTime() / 1000000L - before}")
            // all three take either 0 or 1ms to insert up to 10,000 nodes
            // linkedlist takes 0ms most often (because there's no array resizing or allocation
            // there, just pointer shuffling)
        }

        // all opensets should now be full
        for (set in test) {
            assertFalse(set.isEmpty())
        }

        println("Removal round 1")
        // remove a quarter of the nodes and verify that they are indeed the size/4 lowest ones
        removeAndTest(size / 4, test, lowestQuarter)

        // none of them should be empty (sanity check)
        for (set in test) {
            assertFalse(set.isEmpty())
        }

        var cnt = 0
        var i = 0
        while (cnt < size / 2 && i < size) {
            if (lowestQuarter.contains(toInsert[i])) { // these were already removed and can't be updated to test
                i++
                continue
            }
            toInsert[i].combinedCost *= Random.nextDouble()
            // multiplying it by a random number between 0 and 1 is guaranteed to decrease it
            for (set in test) {
                // it's difficult to benchmark these individually because if you modify all at once
                // then update
                // it breaks the internal consistency of the heaps.
                // you have to call update every time you modify a node.
                set.update(toInsert[i])
            }
            cnt++
            i++
        }

        // still shouldn't be empty
        for (set in test) {
            assertFalse(set.isEmpty())
        }

        println("Removal round 2")
        // remove the remaining 3/4
        removeAndTest(size - size / 4, test, null)

        // every set should now be empty
        for (set in test) {
            assertTrue(set.isEmpty())
        }
    }

    private fun removeAndTest(
        amount: Int,
        test: Array<IOpenSet>,
        mustContain: Set<PathNode>?,
    ) {
        val results = Array(test.size) { DoubleArray(amount) }
        for (i in test.indices) {
            val before = System.nanoTime() / 1000000L
            for (j in 0 until amount) {
                val pn = test[i].removeLowest()
                if (mustContain != null && !mustContain.contains(pn)) {
                    throw IllegalStateException(
                        "PathNode not in mustContain set: pos=(${pn.x},${pn.y},${pn.z}) cost=${pn.combinedCost}",
                    )
                }
                results[i][j] = pn.combinedCost
            }
            println("${test[i].javaClass} ${System.nanoTime() / 1000000L - before}")
        }

        for (j in 0 until amount) {
            for (i in 1 until test.size) {
                assertEquals(results[i][j], results[0][j], 0.0)
            }
        }

        for (i in 0 until amount - 1) {
            assertTrue(results[0][i] < results[0][i + 1])
        }
    }

    companion object {
        @JvmStatic
        fun testSizes(): Stream<Int> {
            val sizes = mutableListOf<Int>()
            for (size in 1 until 20) {
                sizes.add(size)
            }
            for (size in 100..1000 step 100) {
                sizes.add(size)
            }
            sizes.add(5000)
            sizes.add(10000)
            return sizes.stream()
        }
    }
}
