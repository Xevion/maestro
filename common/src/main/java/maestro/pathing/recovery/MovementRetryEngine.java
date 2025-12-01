package maestro.pathing.recovery;

import java.util.Comparator;
import java.util.Optional;
import maestro.api.pathing.movement.ActionCosts;
import maestro.api.pathing.movement.IMovement;
import maestro.api.utils.MaestroLogger;
import maestro.api.utils.PackedBlockPos;
import maestro.behavior.PathingBehavior;
import maestro.pathing.movement.Movement;
import org.slf4j.Logger;

/**
 * Generates alternative movements when one fails.
 *
 * <p>When a movement fails (e.g., teleport rejected, world changed), this engine attempts to find
 * alternative movements from the current position to the same destination. It uses the movement
 * provider to generate all possible movements, then applies failure memory penalties to select the
 * best alternative.
 */
public class MovementRetryEngine {

    private static final Logger log = MaestroLogger.get("path");
    private final PathingBehavior behavior;

    public MovementRetryEngine(PathingBehavior behavior) {
        this.behavior = behavior;
    }

    /**
     * Tries to find alternative movements when one fails.
     *
     * <p>Generates all possible movements from the current position, filters out the exact failed
     * movement and movements that have failed too many times, applies failure memory penalties, and
     * returns the best alternative.
     *
     * @param failedMovement the movement that just failed
     * @param currentPosition current player position
     * @return alternative movement if found, empty otherwise
     */
    public Optional<IMovement> findAlternative(
            Movement failedMovement, PackedBlockPos currentPosition) {
        PackedBlockPos destination = failedMovement.getDest();
        Class<? extends Movement> failedType = failedMovement.getClass();

        log.atDebug()
                .addKeyValue("failed_type", failedType.getSimpleName())
                .addKeyValue("source", currentPosition)
                .addKeyValue("destination", destination)
                .log("Searching for alternative movement");

        // Get calculation context for movement initialization
        var context = behavior.secretInternalGetCalculationContext();

        // Generate all possible movements from current position
        return behavior.movementProvider
                .generateMovements(context, currentPosition)
                .map(m -> (Movement) m)
                // Filter to movements that reach the same destination
                .filter(m -> m.getDest().equals(destination))
                // Exclude the exact movement type that failed
                .filter(m -> !m.getClass().equals(failedType))
                // Exclude movements with excessive failures
                .filter(
                        m ->
                                !behavior.failureMemory.shouldFilter(
                                        m.getSrc(), m.getDest(), m.getClass()))
                // Filter out impossible movements
                .filter(m -> m.getCost() < ActionCosts.COST_INF)
                // Apply failure memory penalties to costs
                .map(
                        m -> {
                            double baseCost = m.getCost();
                            double penalty =
                                    behavior.failureMemory.getCostPenalty(
                                            m.getSrc(), m.getDest(), m.getClass());
                            double penalizedCost = baseCost * penalty;

                            log.atDebug()
                                    .addKeyValue("movement_type", m.getClass().getSimpleName())
                                    .addKeyValue("base_cost", baseCost)
                                    .addKeyValue("penalty", penalty)
                                    .addKeyValue("penalized_cost", penalizedCost)
                                    .log("Evaluating alternative movement");

                            return new PenalizedMovement(m, penalizedCost);
                        })
                // Sort by penalized cost (lowest cost = best alternative)
                .min(Comparator.comparingDouble(pm -> pm.penalizedCost))
                .map(
                        pm -> {
                            log.atInfo()
                                    .addKeyValue(
                                            "alternative_type",
                                            pm.movement.getClass().getSimpleName())
                                    .addKeyValue("penalized_cost", pm.penalizedCost)
                                    .log("Found alternative movement");

                            // Initialize movement fields that are normally set during path
                            // post-processing
                            pm.movement.checkLoadedChunk(context);

                            return (IMovement) pm.movement;
                        });
    }

    /** Helper class to pair a movement with its penalized cost for sorting */
    private static class PenalizedMovement {

        final Movement movement;
        final double penalizedCost;

        PenalizedMovement(Movement movement, double penalizedCost) {
            this.movement = movement;
            this.penalizedCost = penalizedCost;
        }
    }
}
