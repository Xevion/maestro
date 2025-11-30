package maestro.pathing.movement;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.pathing.movement.IMovement;
import maestro.api.utils.BetterBlockPos;
import maestro.pathing.movement.movements.MovementSwimHorizontal;
import maestro.pathing.movement.movements.MovementSwimVertical;

/**
 * Dynamic swimming movement provider with configurable angular precision.
 *
 * <p>Instead of hardcoded 6 directions (N/S/E/W/Up/Down), generates movements based on angular
 * precision settings.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>angularPrecision=4: 4 directions (N, E, S, W) + UP/DOWN
 *   <li>angularPrecision=8: 8 directions (N, NE, E, SE, S, SW, W, NW) + UP/DOWN
 *   <li>angularPrecision=16: 16 directions (every 22.5°) + UP/DOWN
 *   <li>angularPrecision=32: 32 directions (every 11.25°) + UP/DOWN
 * </ul>
 */
public class ContinuousSwimmingProvider implements IMovementProvider {
    private final int angularPrecision;

    public ContinuousSwimmingProvider() {
        this.angularPrecision = Agent.settings().swimAngularPrecision.value;
    }

    @Override
    public Stream<IMovement> generateMovements(CalculationContext context, BetterBlockPos from) {
        // Only generate if in water
        if (!MovementHelper.isWater(context.get(from.x, from.y, from.z))) {
            return Stream.empty();
        }

        List<IMovement> movements = new ArrayList<>();

        // Generate horizontal movements with configurable angles
        double angleStep = (2 * Math.PI) / angularPrecision;
        for (int i = 0; i < angularPrecision; i++) {
            double angle = i * angleStep;

            // Calculate offset (rounded to nearest block)
            int dx = (int) Math.round(Math.cos(angle));
            int dz = (int) Math.round(Math.sin(angle));

            // Skip if no movement (shouldn't happen with proper precision)
            if (dx == 0 && dz == 0) {
                continue;
            }

            BetterBlockPos dest = new BetterBlockPos(from.x + dx, from.y, from.z + dz);
            MovementSwimHorizontal movement =
                    new MovementSwimHorizontal(context.getMaestro(), from, dest);
            movement.override(movement.calculateCost(context));
            movements.add(movement);
        }

        // Generate vertical-diagonal movements (3D diagonals)
        for (int i = 0; i < angularPrecision; i++) {
            double angle = i * angleStep;
            int dx = (int) Math.round(Math.cos(angle));
            int dz = (int) Math.round(Math.sin(angle));

            if (dx == 0 && dz == 0) {
                continue;
            }

            // UP-diagonal variant
            BetterBlockPos upDest = new BetterBlockPos(from.x + dx, from.y + 1, from.z + dz);
            MovementSwimHorizontal upDiag =
                    new MovementSwimHorizontal(context.getMaestro(), from, upDest);
            upDiag.override(upDiag.calculateCost(context));
            movements.add(upDiag);

            // DOWN-diagonal variant
            BetterBlockPos downDest = new BetterBlockPos(from.x + dx, from.y - 1, from.z + dz);
            MovementSwimHorizontal downDiag =
                    new MovementSwimHorizontal(context.getMaestro(), from, downDest);
            downDiag.override(downDiag.calculateCost(context));
            movements.add(downDiag);
        }

        // Generate pure vertical movements (UP, DOWN)
        MovementSwimVertical upMovement =
                new MovementSwimVertical(context.getMaestro(), from, from.above());
        upMovement.override(upMovement.calculateCost(context));
        movements.add(upMovement);

        MovementSwimVertical downMovement =
                new MovementSwimVertical(context.getMaestro(), from, from.below());
        downMovement.override(downMovement.calculateCost(context));
        movements.add(downMovement);

        return movements.stream();
    }
}
