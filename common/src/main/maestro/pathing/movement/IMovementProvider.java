package maestro.pathing.movement;

import java.util.stream.Stream;
import maestro.api.pathing.movement.IMovement;
import maestro.api.utils.PackedBlockPos;

/**
 * Generates possible movements from a given position during pathfinding. Allows dynamic movement
 * generation based on context rather than static enum.
 *
 * <p>This interface enables:
 *
 * <ul>
 *   <li>Dynamic movement precision (e.g., 4/8/16/32 swimming directions)
 *   <li>Context-aware movement filtering (e.g., only generate teleports at decision points)
 *   <li>Custom movement sets without modifying core pathfinding
 * </ul>
 *
 * <p>Implementations should generate movements with costs already calculated to avoid redundant
 * computation during pathfinding.
 *
 * @see IMovement
 */
public interface IMovementProvider {

    /**
     * Generate all possible movements from the given position. Called during A* node expansion for
     * each position.
     *
     * <p>Returned movements should:
     *
     * <ul>
     *   <li>Have costs already calculated via {@link IMovement#getCost()}
     *   <li>Be filtered to exclude impossible movements (cost >= {@link
     *       maestro.api.pathing.movement.ActionCosts#COST_INF})
     *   <li>Have valid source and destination positions
     * </ul>
     *
     * @param context Calculation context with world state and settings
     * @param from Source position for movement generation
     * @return Stream of possible movements (empty if no valid movements)
     */
    Stream<IMovement> generateMovements(CalculationContext context, PackedBlockPos from);
}
