package maestro.pathing.movement;

import java.util.List;
import java.util.stream.Stream;
import maestro.api.pathing.movement.ActionCosts;
import maestro.api.pathing.movement.IMovement;
import maestro.api.utils.MaestroLogger;
import maestro.api.utils.PackedBlockPos;
import maestro.pathing.movement.movements.MovementTeleport;
import org.slf4j.Logger;

/**
 * Provides teleport movements with configurable sparsity for performance control.
 *
 * <p>Only generates teleport movements at a fraction of nodes (controlled by sparsity setting) to
 * avoid overwhelming the pathfinding algorithm with too many options.
 */
public class TeleportMovementProvider implements IMovementProvider {
    private static final Logger log = MaestroLogger.get("path");

    @Override
    public Stream<IMovement> generateMovements(CalculationContext context, PackedBlockPos from) {
        // Early exit: setting disabled
        if (!context.allowTeleport) {
            return Stream.empty();
        }

        // Early exit: sparsity check (only generate at 1/N nodes)
        int sparsity = context.teleportGenerationSparsity;
        if (sparsity > 1 && (from.hashCode() % sparsity) != 0) {
            return Stream.empty();
        }

        // Find valid destinations (arrival and landing positions)
        List<TeleportDestinationFinder.TeleportDestination> destinations =
                TeleportDestinationFinder.findDestinations(context, from);

        // Create movements for each valid destination
        return destinations.stream()
                .map(
                        dest ->
                                new MovementTeleport(
                                        context.getMaestro(), from, dest.arrival, dest.landing))
                .peek(move -> move.override(move.calculateCost(context)))
                .filter(move -> move.getCost() < ActionCosts.COST_INF)
                .map(move -> (IMovement) move);
    }
}
