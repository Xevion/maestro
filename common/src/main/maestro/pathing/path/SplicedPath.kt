package maestro.pathing.path

import maestro.api.pathing.calc.IPath
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.movement.IMovement
import maestro.api.utils.PackedBlockPos
import maestro.utils.pathing.PathBase

class SplicedPath
    private constructor(
        private val path: List<PackedBlockPos>,
        private val movements: MutableList<IMovement>,
        override val numNodesConsidered: Int,
        override val goal: Goal,
    ) : PathBase() {
        init {
            sanityCheck()
        }

        override fun movements(): List<IMovement> = movements

        override fun positions(): List<PackedBlockPos> = path

        override fun length(): Int = path.size

        override fun replaceMovement(
            index: Int,
            newMovement: IMovement,
        ) {
            require(index in movements.indices) {
                "Index $index out of bounds for movements list of size ${movements.size}"
            }
            movements[index] = newMovement
        }

        companion object {
            @JvmStatic
            fun trySplice(
                first: IPath?,
                second: IPath?,
                allowOverlapCutoff: Boolean,
            ): SplicedPath? {
                if (first == null || second == null) {
                    return null
                }
                if (first.dest != second.src) {
                    return null
                }

                val secondPos = second.positions().toSet()
                var firstPositionInSecond = -1
                for (i in 0 until first.length() - 1) {
                    if (first.positions()[i] in secondPos) {
                        firstPositionInSecond = i
                        break
                    }
                }

                if (firstPositionInSecond != -1) {
                    if (!allowOverlapCutoff) {
                        return null
                    }
                } else {
                    firstPositionInSecond = first.length() - 1
                }

                val positionInSecond = second.positions().indexOf(first.positions()[firstPositionInSecond])
                check(allowOverlapCutoff || positionInSecond == 0) {
                    "Paths to be spliced are overlapping incorrectly"
                }

                val positions =
                    buildList {
                        addAll(first.positions().subList(0, firstPositionInSecond + 1))
                        addAll(second.positions().subList(positionInSecond + 1, second.length()))
                    }

                val movements =
                    buildList {
                        addAll(first.movements().subList(0, firstPositionInSecond))
                        addAll(second.movements().subList(positionInSecond, second.length() - 1))
                    }.toMutableList()

                return SplicedPath(positions, movements, first.numNodesConsidered + second.numNodesConsidered, first.goal)
            }
        }
    }
