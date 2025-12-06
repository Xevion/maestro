package maestro.pathing.goals

import it.unimi.dsi.fastutil.doubles.DoubleOpenHashSet
import net.minecraft.core.BlockPos
import kotlin.math.ceil
import kotlin.math.sqrt

/** Useful for automated combat (retreating specifically) */
open class GoalRunAway(
    distance: Double,
    private val maintainY: Int? = null,
    vararg from: BlockPos,
) : Goal {
    private val from: Array<out BlockPos> = from
    private val distanceSq: Int = (distance * distance).toInt()

    init {
        require(from.isNotEmpty()) { "Positions to run away from must not be empty" }
    }

    constructor(distance: Double, vararg from: BlockPos) : this(distance, null, *from)

    override fun isInGoal(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        maintainY?.let { if (it != y) return false }

        return from.all { pos ->
            val diffX = x - pos.x
            val diffZ = z - pos.z
            val distSq = diffX * diffX + diffZ * diffZ
            distSq >= distanceSq
        }
    }

    override fun heuristic(
        x: Int,
        y: Int,
        z: Int,
    ): Double {
        var min =
            from.minOf { pos ->
                GoalXZ.calculate((pos.x - x).toDouble(), (pos.z - z).toDouble())
            }

        min = -min

        if (maintainY != null) {
            min = min * 0.6 + GoalYLevel.calculate(maintainY, y) * 1.5
        }

        return min
    }

    override fun heuristic(): Double {
        // TODO less hacky solution
        val distance = ceil(sqrt(distanceSq.toDouble())).toInt()
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE

        for (pos in from) {
            minX = minOf(minX, pos.x - distance)
            minY = minOf(minY, pos.y - distance)
            minZ = minOf(minZ, pos.z - distance)
            maxX = maxOf(maxX, pos.x + distance)
            maxY = maxOf(maxY, pos.y + distance)
            maxZ = maxOf(maxZ, pos.z + distance)
        }

        val maybeAlwaysInside = DoubleOpenHashSet() // see pull request #1978
        var minOutside = Double.POSITIVE_INFINITY

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val h = heuristic(x, y, z)
                    if (h < minOutside && isInGoal(x, y, z)) {
                        maybeAlwaysInside.add(h)
                    } else {
                        minOutside = minOf(minOutside, h)
                    }
                }
            }
        }

        var maxInside = Double.NEGATIVE_INFINITY
        val it = maybeAlwaysInside.iterator()
        while (it.hasNext()) {
            val inside = it.nextDouble()
            if (inside < minOutside) {
                maxInside = maxOf(maxInside, inside)
            }
        }
        return maxInside
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GoalRunAway) return false

        return distanceSq == other.distanceSq &&
            from.contentEquals(other.from) &&
            maintainY == other.maintainY
    }

    override fun hashCode(): Int {
        var hash = from.contentHashCode()
        hash = hash * 1196803141 + distanceSq
        hash = hash * -2053788840 + (maintainY ?: 0)
        return hash
    }

    override fun toString(): String =
        if (maintainY != null) {
            "GoalRunAwayFromMaintainY y=$maintainY, ${from.toList()}"
        } else {
            "GoalRunAwayFrom${from.toList()}"
        }
}
