package maestro.pathing.recovery

import maestro.api.pathing.movement.ActionCosts
import maestro.api.pathing.movement.IMovement
import maestro.api.utils.Loggers
import maestro.api.utils.PackedBlockPos
import maestro.behavior.PathingBehavior
import maestro.pathing.movement.Movement
import org.slf4j.Logger

/**
 * Generates alternative movements when one fails.
 *
 * When a movement fails (e.g., teleport rejected, world changed), this engine attempts to find
 * alternative movements from the current position to the same destination. It uses the movement
 * provider to generate all possible movements, then applies failure memory penalties to select the
 * best alternative.
 */
class MovementRetryEngine(
    private val behavior: PathingBehavior,
) {
    /**
     * Tries to find alternative movements when one fails.
     *
     * Generates all possible movements from the current position, filters out the exact failed
     * movement and movements that have failed too many times, applies failure memory penalties, and
     * returns the best alternative.
     *
     * @param failedMovement the movement that just failed
     * @param currentPosition current player position
     * @return alternative movement if found, null otherwise
     */
    fun findAlternative(
        failedMovement: Movement,
        currentPosition: PackedBlockPos,
    ): IMovement? {
        val destination = failedMovement.dest
        val failedType = failedMovement::class.java

        log
            .atDebug()
            .addKeyValue("failed_type", failedType.simpleName)
            .addKeyValue("source", currentPosition)
            .addKeyValue("destination", destination)
            .log("Searching for alternative movement")

        // Get calculation context for movement initialization
        val context = behavior.secretInternalGetCalculationContext()

        // Generate all possible movements from current position
        val result =
            behavior.movementProvider
                .generateMovements(context, currentPosition)
                .map { it as Movement }
                // Filter to movements that reach the same destination
                .filter { m -> m.dest == destination }
                // Exclude the exact movement type that failed
                .filter { m -> m.javaClass != failedType }
                // Exclude movements with excessive failures
                .filter { m -> !behavior.failureMemory.shouldFilter(m.src, m.dest, m.javaClass) }
                // Filter out impossible movements
                .filter { m -> m.cost < ActionCosts.COST_INF }
                // Apply failure memory penalties to costs
                .map { movement ->
                    val baseCost = movement.cost
                    val penalty =
                        behavior.failureMemory.getCostPenalty(
                            movement.src,
                            movement.dest,
                            movement.javaClass,
                        )
                    val penalizedCost = baseCost * penalty

                    log
                        .atDebug()
                        .addKeyValue("movement_type", movement.javaClass.simpleName)
                        .addKeyValue("base_cost", baseCost)
                        .addKeyValue("penalty", penalty)
                        .addKeyValue("penalized_cost", penalizedCost)
                        .log("Evaluating alternative movement")

                    PenalizedMovement(movement, penalizedCost)
                }
                // Sort by penalized cost (lowest cost = best alternative)
                .min(Comparator.comparingDouble { pm -> pm.penalizedCost })
                .orElse(null)

        return result?.let { pm ->
            log
                .atInfo()
                .addKeyValue("alternative_type", pm.movement.javaClass.simpleName)
                .addKeyValue("penalized_cost", pm.penalizedCost)
                .log("Found alternative movement")

            // Initialize movement fields that are normally set during path post-processing
            pm.movement.checkLoadedChunk(context)

            pm.movement
        }
    }

    /** Helper class to pair a movement with its penalized cost for sorting */
    private data class PenalizedMovement(
        val movement: Movement,
        val penalizedCost: Double,
    )

    companion object {
        private val log: Logger = Loggers.Path.get()
    }
}
