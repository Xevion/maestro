package maestro.pathing.recovery;

import java.util.List;
import java.util.Optional;
import maestro.Agent;
import maestro.api.pathing.calc.IPath;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.goals.GoalBlock;
import maestro.api.pathing.movement.ActionCosts;
import maestro.api.utils.BetterBlockPos;
import maestro.api.utils.MaestroLogger;
import maestro.pathing.calc.AStarPathFinder;
import maestro.pathing.movement.CalculationContext;
import maestro.pathing.movement.Movement;
import maestro.pathing.movement.movements.MovementAscend;
import maestro.pathing.movement.movements.MovementDescend;
import maestro.pathing.movement.movements.MovementTeleport;
import maestro.pathing.movement.movements.MovementTraverse;
import maestro.utils.pathing.Favoring;
import org.slf4j.Logger;

/**
 * Handles path reconnection when bot deviates from corridor.
 *
 * <p>When a bot deviates from its path (knockback, water currents), instead of immediately
 * canceling and recalculating the entire path, this system attempts to reconnect to an optimal
 * point in the existing path. This is more efficient than full recalculation when the deviation is
 * temporary and the path is still valid.
 */
public class PathReconnection {

    private static final Logger log = MaestroLogger.get("path");

    /**
     * Finds the best reconnection point in the existing path.
     *
     * <p>Searches within a window around the current corridor position, looking for positions that
     * are reachable and have good cost characteristics. Applies failure memory penalties when
     * estimating costs.
     *
     * @param currentPath The current path being executed
     * @param corridorPosition Current position in the path (segment index)
     * @param currentPosition Bot's actual current position
     * @param context Calculation context for cost estimation and failure memory
     * @return Optional containing the best reconnection candidate, or empty if none found
     */
    public Optional<ReconnectionCandidate> findReconnectionPoint(
            IPath currentPath,
            int corridorPosition,
            BetterBlockPos currentPosition,
            CalculationContext context) {
        int lookbehind = Agent.settings().pathReconnectionLookbehind.value;
        int lookahead = Agent.settings().pathReconnectionLookahead.value;

        int searchStart = Math.max(0, corridorPosition - lookbehind);
        int searchEnd = Math.min(currentPath.movements().size() - 1, corridorPosition + lookahead);

        log.atDebug()
                .addKeyValue("corridor_position", corridorPosition)
                .addKeyValue("search_start", searchStart)
                .addKeyValue("search_end", searchEnd)
                .addKeyValue("search_window_size", searchEnd - searchStart + 1)
                .addKeyValue("current_position", currentPosition)
                .log("Searching for reconnection point");

        ReconnectionCandidate bestCandidate = null;
        double bestCost = Double.MAX_VALUE;
        int candidatesEvaluated = 0;

        // Scan the search window for potential reconnection points
        for (int i = searchStart; i <= searchEnd; i++) {
            Movement movement = (Movement) currentPath.movements().get(i);
            BetterBlockPos destination = movement.getDest();

            // Estimate cost to reach this point
            double estimatedCost = estimateCostToReconnect(currentPosition, destination, context);

            candidatesEvaluated++;

            // Skip unreachable candidates
            if (estimatedCost >= ActionCosts.COST_INF) {
                continue;
            }

            // Track the best candidate
            if (estimatedCost < bestCost) {
                bestCost = estimatedCost;
                bestCandidate = new ReconnectionCandidate(i, destination, estimatedCost);

                log.atDebug()
                        .addKeyValue("candidate_index", i)
                        .addKeyValue("candidate_position", destination)
                        .addKeyValue("estimated_cost", estimatedCost)
                        .log("Found better reconnection candidate");
            }
        }

        log.atDebug()
                .addKeyValue("candidates_evaluated", candidatesEvaluated)
                .addKeyValue(
                        "best_candidate_index",
                        bestCandidate != null ? bestCandidate.pathIndex : -1)
                .addKeyValue(
                        "best_cost",
                        bestCandidate != null ? bestCandidate.estimatedCost : Double.MAX_VALUE)
                .log("Reconnection point search complete");

        return Optional.ofNullable(bestCandidate);
    }

    /**
     * Calculates a partial path from current position to reconnection point.
     *
     * <p>Uses A* pathfinding with limited node budget and timeout to find a short reconnection
     * path. This is intentionally lightweight to avoid spending too much time on reconnection
     * calculation.
     *
     * @param from Starting position (bot's current position)
     * @param to Reconnection point (position in existing path)
     * @param context Calculation context for pathfinding
     * @return Optional containing the reconnection path, or empty if calculation failed
     */
    public Optional<IPath> calculateReconnectionPath(
            BetterBlockPos from, BetterBlockPos to, CalculationContext context) {
        Goal reconnectionGoal = new GoalBlock(to);

        log.atDebug()
                .addKeyValue("from", from)
                .addKeyValue("to", to)
                .log("Calculating reconnection path");

        // Create A* pathfinder for partial path calculation
        AStarPathFinder pathfinder =
                new AStarPathFinder(
                        from,
                        from.x,
                        from.y,
                        from.z,
                        reconnectionGoal,
                        new Favoring(null, context), // No previous path for favoring
                        context);

        // Use limited timeout and node budget for reconnection
        // Scale node budget based on distance - longer reconnections need more nodes
        int baseNodes = Agent.settings().pathReconnectionMaxPartialNodes.value;
        int distance = (int) Math.ceil(reconnectionGoal.heuristic(from));
        int scaledNodes =
                Math.min(
                        2000, // Cap at reasonable maximum to prevent runaway calculations
                        baseNodes
                                + Math.max(
                                        0, (distance - 5) * 50) // Add 50 nodes per block beyond 5
                        );
        long timeout = Agent.settings().pathReconnectionTimeoutMs.value;

        log.atDebug()
                .addKeyValue("base_nodes", baseNodes)
                .addKeyValue("distance", distance)
                .addKeyValue("scaled_nodes", scaledNodes)
                .log("Calculated dynamic node budget");

        // Calculate partial path (primary timeout = failure timeout for quick calculation)
        Optional<IPath> pathResult = pathfinder.calculate(timeout, scaledNodes).getPath();

        if (pathResult.isPresent()) {
            log.atInfo()
                    .addKeyValue("path_length", pathResult.get().positions().size())
                    .addKeyValue("from", from)
                    .addKeyValue("to", to)
                    .log("Reconnection path calculated successfully");
        } else {
            log.atDebug()
                    .addKeyValue("from", from)
                    .addKeyValue("to", to)
                    .log("Failed to calculate reconnection path");
        }

        return pathResult;
    }

    /**
     * Determines if reconnection should be used instead of full recalculation.
     *
     * <p>Compares the estimated cost of reconnection to the estimated cost of full path
     * recalculation. Reconnection is preferred when it's significantly cheaper (by the threshold
     * factor).
     *
     * @param reconnectionCost Estimated cost to reconnect to existing path
     * @param fullRecalcCost Estimated cost of full path recalculation
     * @param threshold Cost threshold multiplier (reconnection cost must be < threshold *
     *     fullRecalcCost)
     * @return True if reconnection should be used, false if full recalc is better
     */
    public boolean shouldUseReconnection(
            double reconnectionCost, double fullRecalcCost, double threshold) {
        boolean shouldUse = reconnectionCost < threshold * fullRecalcCost;

        log.atDebug()
                .addKeyValue("reconnection_cost", reconnectionCost)
                .addKeyValue("full_recalc_cost", fullRecalcCost)
                .addKeyValue("threshold", threshold)
                .addKeyValue("threshold_cost", threshold * fullRecalcCost)
                .addKeyValue("decision", shouldUse ? "reconnect" : "full_recalc")
                .log("Evaluating reconnection vs full recalculation");

        return shouldUse;
    }

    /**
     * Estimates the cost to reconnect from current position to target.
     *
     * <p>Combines straight-line distance heuristic with failure memory penalties. Since we don't
     * know the exact path or movement types that will be used, we sample common movement types and
     * apply the worst-case penalty found. This prevents selecting reconnection points that appear
     * close but are actually unreachable due to previous failures.
     *
     * @param from Current position
     * @param to Target reconnection position
     * @param context Calculation context for failure memory lookup
     * @return Estimated cost including distance and failure penalties
     */
    private double estimateCostToReconnect(
            BetterBlockPos from, BetterBlockPos to, CalculationContext context) {
        // Base distance heuristic
        GoalBlock goal = new GoalBlock(to);
        double baseHeuristic = goal.heuristic(from);

        // Apply worst-case penalty for reaching this destination
        // We don't know exact movement type, so check common ones
        double maxPenalty = 1.0;
        for (Class<? extends Movement> type : getCommonMovementTypes()) {
            double penalty = context.failureMemory.getCostPenalty(from, to, type);
            maxPenalty = Math.max(maxPenalty, penalty);
        }

        return baseHeuristic * maxPenalty;
    }

    /**
     * Returns common movement types to sample for failure memory penalties.
     *
     * <p>These represent the most frequently used movement types during pathfinding. Sampling these
     * provides a reasonable estimate of failure memory penalties without needing to know the exact
     * path.
     */
    private static final List<Class<? extends Movement>> COMMON_MOVEMENT_TYPES =
            List.of(
                    MovementTeleport.class,
                    MovementAscend.class,
                    MovementTraverse.class,
                    MovementDescend.class);

    private List<Class<? extends Movement>> getCommonMovementTypes() {
        return COMMON_MOVEMENT_TYPES;
    }

    /** Data class representing a potential reconnection point. */
    public static class ReconnectionCandidate {
        /** Index in the path's movement list */
        public final int pathIndex;

        /** Position to reconnect to */
        public final BetterBlockPos position;

        /** Estimated cost to reach this position */
        public final double estimatedCost;

        public ReconnectionCandidate(int pathIndex, BetterBlockPos position, double estimatedCost) {
            this.pathIndex = pathIndex;
            this.position = position;
            this.estimatedCost = estimatedCost;
        }

        @Override
        public String toString() {
            return String.format(
                    "ReconnectionCandidate{index=%d, position=%s, cost=%.2f}",
                    pathIndex, position, estimatedCost);
        }
    }
}
