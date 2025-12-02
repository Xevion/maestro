package maestro.pathing.movement

import maestro.api.pathing.movement.ActionCosts
import maestro.api.pathing.movement.IMovement
import maestro.api.utils.PackedBlockPos
import java.util.stream.Stream

/**
 * Generates possible movements from a given position during pathfinding. Allows dynamic movement
 * generation based on context rather than static enum.
 *
 * This interface enables:
 * - Dynamic movement precision (e.g., 4/8/16/32 swimming directions)
 * - Context-aware movement filtering (e.g., only generate teleports at decision points)
 * - Custom movement sets without modifying core pathfinding
 *
 * Implementations should generate movements with costs already calculated to avoid redundant
 * computation during pathfinding.
 *
 * @see IMovement
 */
interface IMovementProvider {
    /**
     * Generate all possible movements from the given position. Called during A* node expansion for
     * each position.
     *
     * Returned movements should:
     * - Have costs already calculated via [IMovement.getCost]
     * - Be filtered to exclude impossible movements (cost >= [ActionCosts.COST_INF])
     * - Have valid source and destination positions
     *
     * @param context Calculation context with world state and settings
     * @param from Source position for movement generation
     * @return Stream of possible movements (empty if no valid movements)
     */
    fun generateMovements(
        context: CalculationContext,
        from: PackedBlockPos,
    ): Stream<IMovement>
}
