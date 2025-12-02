package maestro.pathing.path

import maestro.api.pathing.calc.IPath
import maestro.api.utils.PackedBlockPos
import maestro.pathing.movement.Movement
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Defines a spatial corridor around path segments to tolerate minor deviations.
 *
 * Instead of requiring exact position on Movement.getValidPositions(), allows agent to be within 1
 * block of any valid position. Reduces unnecessary replanning caused by water currents, combat
 * knockback, or imprecise movement.
 */
class PathCorridor(
    private val path: IPath,
    currentSegment: Int,
) {
    private var currentSegment: Int = currentSegment
    private var corridorStart: Int = 0
    private var corridorEnd: Int = 0
    private var corridorPositions: Set<PackedBlockPos> = emptySet()

    // Cache for nearest position lookup
    private var cachedClosest: PackedBlockPos? = null
    private var cachedClosestDist: Double = 0.0
    private var cachedClosestSegment: Int = 0

    init {
        rebuildCorridor()
    }

    /**
     * Updates the current segment and rebuilds corridor if window changed.
     *
     * @param newSegment New segment index
     */
    fun updateSegment(newSegment: Int) {
        if (newSegment == currentSegment) {
            return
        }

        val oldStart = corridorStart
        val oldEnd = corridorEnd
        currentSegment = newSegment

        corridorStart = maxOf(0, currentSegment - SEGMENT_WINDOW)
        corridorEnd = minOf(path.movements().size - 1, currentSegment + SEGMENT_WINDOW)

        if (corridorStart != oldStart || corridorEnd != oldEnd) {
            rebuildCorridor()
        }
    }

    /**
     * Gets the current segment index.
     *
     * @return Current segment index in path
     */
    fun currentSegment(): Int = currentSegment

    /**
     * Checks if position is within corridor tolerance.
     *
     * @param pos Position to check
     * @return True if within corridor, false otherwise
     */
    fun isWithinCorridor(pos: PackedBlockPos): Boolean = pos in corridorPositions

    /**
     * Finds the nearest valid position to the given position.
     *
     * @param from Position to find nearest valid position from
     * @return Nearest position, or null if none found
     */
    fun findNearestValidPosition(from: PackedBlockPos): PackedBlockPos? {
        // Use cached result if still valid
        cachedClosest?.let { cached ->
            if (cachedClosestSegment == currentSegment) {
                val cachedDist = from.distanceSq(cached)
                if (abs(cachedDist - cachedClosestDist) < 4) {
                    return cached
                }
            }
        }

        // Scan corridor window for nearest valid position
        var nearest: PackedBlockPos? = null
        var nearestDistSq = Double.MAX_VALUE

        for (i in corridorStart..minOf(corridorEnd, path.movements().size - 1)) {
            val movement = path.movements()[i] as? Movement ?: continue
            for (validPos in movement.validPositions) {
                val distSq = from.distanceSq(validPos)
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq
                    nearest = validPos
                }
            }
        }

        nearest?.let {
            cachedClosest = it
            cachedClosestDist = nearestDistSq
            cachedClosestSegment = currentSegment
        }

        return nearest
    }

    /**
     * Calculates distance from position to nearest point on path.
     *
     * @param from Position to calculate distance from
     * @return Distance to path, or Double.MAX_VALUE if no valid positions
     */
    fun distanceToPath(from: PackedBlockPos): Double = findNearestValidPosition(from)?.let { sqrt(from.distanceSq(it)) } ?: Double.MAX_VALUE

    /** Rebuilds corridor positions for current window. */
    private fun rebuildCorridor() {
        val positions = mutableSetOf<PackedBlockPos>()

        for (i in corridorStart..minOf(corridorEnd, path.movements().size - 1)) {
            val movement = path.movements()[i] as? Movement ?: continue
            val validPos = movement.validPositions

            positions.addAll(validPos)

            for (pos in validPos) {
                addBuffer(pos, positions)
            }
        }

        corridorPositions = positions
        cachedClosest = null
    }

    /**
     * Adds buffer positions around a center position.
     *
     * @param center Center position to add buffer around
     * @param positions Set to add buffer positions to
     */
    private fun addBuffer(
        center: PackedBlockPos,
        positions: MutableSet<PackedBlockPos>,
    ) {
        // Add 26 surrounding blocks (3x3x3 cube minus center)
        for (dx in -CORRIDOR_BUFFER..CORRIDOR_BUFFER) {
            for (dy in -CORRIDOR_BUFFER..CORRIDOR_BUFFER) {
                for (dz in -CORRIDOR_BUFFER..CORRIDOR_BUFFER) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue
                    }
                    positions.add(PackedBlockPos(center.x + dx, center.y + dy, center.z + dz))
                }
            }
        }
    }

    companion object {
        private const val CORRIDOR_BUFFER = 1 // Blocks of tolerance
        private const val SEGMENT_WINDOW = 2 // Segments ahead/behind to include
    }
}
