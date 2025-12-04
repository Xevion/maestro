package maestro.pathing.recovery

import maestro.Agent
import maestro.api.pathing.calc.IPath
import maestro.api.pathing.goals.GoalBlock
import maestro.api.pathing.movement.ActionCosts
import maestro.api.utils.MaestroLogger
import maestro.api.utils.PackedBlockPos
import maestro.pathing.calc.AStarPathFinder
import maestro.pathing.movement.CalculationContext
import maestro.pathing.movement.Movement
import maestro.pathing.movement.movements.MovementAscend
import maestro.pathing.movement.movements.MovementDescend
import maestro.pathing.movement.movements.MovementTraverse
import maestro.utils.pathing.Favoring
import org.slf4j.Logger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// Disabled: MovementTeleport not yet ported to Kotlin
// import maestro.pathing.movement.movements.MovementTeleport

/**
 * Handles path reconnection when bot deviates from corridor.
 *
 * When a bot deviates from its path (knockback, water currents), instead of immediately
 * canceling and recalculating the entire path, this system attempts to reconnect to an optimal
 * point in the existing path. This is more efficient than full recalculation when the deviation is
 * temporary and the path is still valid.
 */
class PathReconnection {
    /**
     * Finds the best reconnection point in the existing path.
     *
     * Searches within a window around the current corridor position, looking for positions that
     * are reachable and have good cost characteristics. Applies failure memory penalties when
     * estimating costs.
     *
     * @param currentPath The current path being executed
     * @param corridorPosition Current position in the path (segment index)
     * @param currentPosition Bot's actual current position
     * @param context Calculation context for cost estimation and failure memory
     * @return The best reconnection candidate, or null if none found
     */
    fun findReconnectionPoint(
        currentPath: IPath,
        corridorPosition: Int,
        currentPosition: PackedBlockPos,
        context: CalculationContext,
    ): ReconnectionCandidate? {
        val lookbehind = Agent.settings().pathReconnectionLookbehind.value
        val lookahead = Agent.settings().pathReconnectionLookahead.value

        val searchStart = max(0, corridorPosition - lookbehind)
        val searchEnd = min(currentPath.movements().size - 1, corridorPosition + lookahead)

        log
            .atDebug()
            .addKeyValue("corridor_position", corridorPosition)
            .addKeyValue("search_start", searchStart)
            .addKeyValue("search_end", searchEnd)
            .addKeyValue("search_window_size", searchEnd - searchStart + 1)
            .addKeyValue("current_position", currentPosition)
            .log("Searching for reconnection point")

        // Scan the search window for potential reconnection points
        val bestCandidate =
            (searchStart..searchEnd)
                .asSequence()
                .map { i ->
                    val movement = currentPath.movements()[i] as Movement
                    val destination = movement.dest
                    val estimatedCost = estimateCostToReconnect(currentPosition, destination, context)

                    i to ReconnectionCandidate(i, destination, estimatedCost)
                }.filter { (_, candidate) -> candidate.estimatedCost < ActionCosts.COST_INF }
                .onEach { (i, candidate) ->
                    log
                        .atDebug()
                        .addKeyValue("candidate_index", i)
                        .addKeyValue("candidate_position", candidate.position)
                        .addKeyValue("estimated_cost", candidate.estimatedCost)
                        .log("Found better reconnection candidate")
                }.minByOrNull { (_, candidate) -> candidate.estimatedCost }
                ?.second

        log
            .atDebug()
            .addKeyValue("candidates_evaluated", searchEnd - searchStart + 1)
            .addKeyValue("best_candidate_index", bestCandidate?.pathIndex ?: -1)
            .addKeyValue("best_cost", bestCandidate?.estimatedCost ?: Double.MAX_VALUE)
            .log("Reconnection point search complete")

        return bestCandidate
    }

    /**
     * Calculates a partial path from current position to reconnection point.
     *
     * Uses A* pathfinding with limited node budget and timeout to find a short reconnection
     * path. This is intentionally lightweight to avoid spending too much time on reconnection
     * calculation.
     *
     * @param from Starting position (bot's current position)
     * @param to Reconnection point (position in existing path)
     * @param context Calculation context for pathfinding
     * @return The reconnection path, or null if calculation failed
     */
    fun calculateReconnectionPath(
        from: PackedBlockPos,
        to: PackedBlockPos,
        context: CalculationContext,
    ): IPath? {
        val reconnectionGoal = GoalBlock(to.toBlockPos())

        log
            .atDebug()
            .addKeyValue("from", from)
            .addKeyValue("to", to)
            .log("Calculating reconnection path")

        // Create A* pathfinder for partial path calculation
        val pathfinder =
            AStarPathFinder(
                from,
                from.x,
                from.y,
                from.z,
                reconnectionGoal,
                Favoring(null, context), // No previous path for favoring
                context,
            )

        // Use limited timeout and node budget for reconnection
        // Scale node budget based on distance - longer reconnections need more nodes
        val baseNodes = Agent.settings().pathReconnectionMaxPartialNodes.value
        val distance = ceil(reconnectionGoal.heuristic(from.x, from.y, from.z)).toInt()
        val scaledNodes =
            min(
                2000, // Cap at reasonable maximum to prevent runaway calculations
                baseNodes + max(0, (distance - 5) * 50), // Add 50 nodes per block beyond 5
            )
        val timeout = Agent.settings().pathReconnectionTimeoutMs.value

        log
            .atDebug()
            .addKeyValue("base_nodes", baseNodes)
            .addKeyValue("distance", distance)
            .addKeyValue("scaled_nodes", scaledNodes)
            .log("Calculated dynamic node budget")

        // Calculate partial path (primary timeout = failure timeout for quick calculation)
        val pathResult = pathfinder.calculate(timeout.toLong(), scaledNodes.toLong()).getPath().orElse(null)

        if (pathResult != null) {
            log
                .atInfo()
                .addKeyValue("path_length", pathResult.positions().size)
                .addKeyValue("from", from)
                .addKeyValue("to", to)
                .log("Reconnection path calculated successfully")
        } else {
            log
                .atDebug()
                .addKeyValue("from", from)
                .addKeyValue("to", to)
                .log("Failed to calculate reconnection path")
        }

        return pathResult
    }

    /**
     * Determines if reconnection should be used instead of full recalculation.
     *
     * Compares the estimated cost of reconnection to the estimated cost of full path
     * recalculation. Reconnection is preferred when it's significantly cheaper (by the threshold
     * factor).
     *
     * @param reconnectionCost Estimated cost to reconnect to existing path
     * @param fullRecalcCost Estimated cost of full path recalculation
     * @param threshold Cost threshold multiplier (reconnection cost must be < threshold * fullRecalcCost)
     * @return True if reconnection should be used, false if full recalc is better
     */
    fun shouldUseReconnection(
        reconnectionCost: Double,
        fullRecalcCost: Double,
        threshold: Double,
    ): Boolean {
        val shouldUse = reconnectionCost < threshold * fullRecalcCost

        log
            .atDebug()
            .addKeyValue("reconnection_cost", reconnectionCost)
            .addKeyValue("full_recalc_cost", fullRecalcCost)
            .addKeyValue("threshold", threshold)
            .addKeyValue("threshold_cost", threshold * fullRecalcCost)
            .addKeyValue("decision", if (shouldUse) "reconnect" else "full_recalc")
            .log("Evaluating reconnection vs full recalculation")

        return shouldUse
    }

    /**
     * Estimates the cost to reconnect from current position to target.
     *
     * Combines straight-line distance heuristic with failure memory penalties. Since we don't
     * know the exact path or movement types that will be used, we sample common movement types and
     * apply the worst-case penalty found. This prevents selecting reconnection points that appear
     * close but are actually unreachable due to previous failures.
     *
     * @param from Current position
     * @param to Target reconnection position
     * @param context Calculation context for failure memory lookup
     * @return Estimated cost including distance and failure penalties
     */
    private fun estimateCostToReconnect(
        from: PackedBlockPos,
        to: PackedBlockPos,
        context: CalculationContext,
    ): Double {
        // Base distance heuristic
        val goal = GoalBlock(to.toBlockPos())
        val baseHeuristic = goal.heuristic(from.x, from.y, from.z)

        // Apply worst-case penalty for reaching this destination
        // We don't know exact movement type, so check common ones
        val maxPenalty =
            COMMON_MOVEMENT_TYPES
                .asSequence()
                .map { type -> context.failureMemory.getCostPenalty(from, to, type) }
                .maxOrNull() ?: 1.0

        return baseHeuristic * maxPenalty
    }

    /** Data class representing a potential reconnection point. */
    data class ReconnectionCandidate(
        /** Index in the path's movement list */
        val pathIndex: Int,
        /** Position to reconnect to */
        val position: PackedBlockPos,
        /** Estimated cost to reach this position */
        val estimatedCost: Double,
    ) {
        override fun toString(): String =
            "ReconnectionCandidate{index=$pathIndex, position=$position, cost=${"%.2f".format(estimatedCost)}}"
    }

    companion object {
        private val log: Logger = MaestroLogger.get("path")

        /**
         * Common movement types to sample for failure memory penalties.
         *
         * These represent the most frequently used movement types during pathfinding. Sampling this
         * provides a reasonable estimate of failure memory penalties without needing to know the exact
         * path.
         */
        private val COMMON_MOVEMENT_TYPES: List<Class<out Movement>> =
            listOf(
//                MovementTeleport::class.java,
                MovementAscend::class.java,
                MovementTraverse::class.java,
                MovementDescend::class.java,
            )
    }
}
