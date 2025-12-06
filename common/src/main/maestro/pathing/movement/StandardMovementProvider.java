package maestro.pathing.movement;

import java.util.Arrays;
import java.util.stream.Stream;
import maestro.pathing.MutableMoveResult;
import maestro.utils.PackedBlockPos;

/**
 * Wraps the existing {@link Moves} enum to implement {@link IMovementProvider}. Provides backward
 * compatibility while enabling the provider pattern.
 *
 * <p>This implementation:
 *
 * <ul>
 *   <li>Iterates through all {@link Moves} enum values
 *   <li>Filters out impossible movements (cost >= {@link ActionCosts#COST_INF})
 *   <li>Creates {@link Movement} instances with pre-calculated costs
 *   <li>Reuses {@link MutableMoveResult} for efficiency during cost checking
 * </ul>
 */
public class StandardMovementProvider implements IMovementProvider {

    @Override
    public Stream<IMovement> generateMovements(CalculationContext context, PackedBlockPos from) {
        // Pre-allocate result object (reused for cost checking to avoid allocations)
        MutableMoveResult res = new MutableMoveResult();

        return Arrays.stream(Moves.values())
                .filter(
                        move -> {
                            // Quick cost check using MutableMoveResult
                            res.reset();
                            move.apply(context, from.getX(), from.getY(), from.getZ(), res);
                            // Filter out impossible movements
                            return res.cost < ActionCosts.COST_INF;
                        })
                .map(
                        move -> {
                            // Create actual Movement instance with calculated destination
                            res.reset();
                            move.apply(context, from.getX(), from.getY(), from.getZ(), res);
                            Movement movement = move.apply0(context, from);
                            // Store pre-calculated cost to avoid recalculation later
                            movement.override(res.cost);
                            return (IMovement) movement;
                        });
    }
}
