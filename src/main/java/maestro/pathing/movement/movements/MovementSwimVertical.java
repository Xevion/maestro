package maestro.pathing.movement.movements;

import static maestro.api.pathing.movement.ActionCosts.*;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import maestro.Agent;
import maestro.api.IAgent;
import maestro.api.pathing.movement.MovementStatus;
import maestro.api.utils.BetterBlockPos;
import maestro.behavior.RotationManager;
import maestro.pathing.movement.CalculationContext;
import maestro.pathing.movement.Movement;
import maestro.pathing.movement.MovementHelper;
import maestro.pathing.movement.MovementState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vertical swimming movement in water (UP and DOWN). This movement enables true 3D underwater
 * navigation by swimming straight up or down through water columns.
 *
 * <p>Cost Model:
 *
 * <ul>
 *   <li>Base: SWIM_VERTICAL_ONE_BLOCK_COST = 5.0 (same as horizontal for true 3D)
 *   <li>Bubble column assist (same direction): 0.5x multiplier
 *   <li>Bubble column penalty (opposite direction): 2.0x multiplier
 *   <li>Magma block proximity: +10.0 cost penalty (avoid damage)
 *   <li>Mining underwater: 5x vanilla mining speed penalty
 * </ul>
 *
 * <p>Execution uses RotationManager + SwimmingBehavior with vertical pitch control.
 */
public class MovementSwimVertical extends Movement {

    private final boolean ascending;

    public MovementSwimVertical(IAgent maestro, BetterBlockPos from, BetterBlockPos to) {
        super(maestro, from, to, new BetterBlockPos[] {to});
        this.ascending = to.y > from.y;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        return cost(context, src.x, src.y, src.z, ascending);
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        return ImmutableSet.of(src, dest);
    }

    /**
     * Calculate cost for vertical swimming movement (UP or DOWN).
     *
     * @param context Calculation context with settings
     * @param x Position X
     * @param y Position Y
     * @param z Position Z
     * @param ascending True if swimming upward, false if swimming downward
     * @return Cost in ticks, or COST_INF if movement is not possible
     */
    public static double cost(CalculationContext context, int x, int y, int z, boolean ascending) {
        double baseCost = SWIM_VERTICAL_ONE_BLOCK_COST;

        // Swimming must be enabled
        if (!context.allowSwimming) {
            if (Agent.settings().logSwimming.value) {
                System.out.println(
                        "[SwimV] COST_INF: Swimming disabled at " + x + "," + y + "," + z);
            }
            return COST_INF;
        }

        int destY = ascending ? y + 1 : y - 1;

        // Both source and destination must be water
        BlockState srcState = context.get(x, y, z);
        BlockState destState = context.get(x, destY, z);
        boolean srcIsWater = MovementHelper.isWater(srcState);
        boolean destIsWater = MovementHelper.isWater(destState);
        if (!srcIsWater || !destIsWater) {
            if (Agent.settings().logSwimming.value) {
                System.out.println(
                        "[SwimV] COST_INF: Not water - src="
                                + srcIsWater
                                + " dest="
                                + destIsWater
                                + " at "
                                + x
                                + ","
                                + y
                                + ","
                                + z
                                + " -> "
                                + x
                                + ","
                                + destY
                                + ","
                                + z
                                + " (ascending="
                                + ascending
                                + ")");
            }
            return COST_INF;
        }

        // Destination must be passable for swimming
        if (!MovementHelper.canSwimThrough(context, x, destY, z)) {
            if (Agent.settings().logSwimming.value) {
                System.out.println(
                        "[SwimV] COST_INF: Can't swim through at " + x + "," + destY + "," + z);
            }
            return COST_INF;
        }

        if (Agent.settings().logSwimming.value) {
            System.out.println(
                    "[SwimV] Cost="
                            + baseCost
                            + " at "
                            + x
                            + ","
                            + y
                            + ","
                            + z
                            + " -> "
                            + x
                            + ","
                            + destY
                            + ","
                            + z
                            + " (ascending="
                            + ascending
                            + ")");
        }

        // Bubble column handling (0.5x with assist, 2.0x against)
        if (MovementHelper.isBubbleColumn(srcState)) {
            boolean upwardBubbles = MovementHelper.isUpwardBubbleColumn(srcState);
            if ((ascending && upwardBubbles) || (!ascending && !upwardBubbles)) {
                // Swimming with bubble current = 50% faster
                baseCost *= 0.5;
            } else {
                // Swimming against bubble current = 2x slower
                baseCost *= 2.0;
            }
        }

        // Avoid magma blocks when descending (damage penalty)
        if (!ascending && context.get(x, y - 2, z).getBlock() == Blocks.MAGMA_BLOCK) {
            baseCost += 10.0; // High penalty to avoid damage
        }

        // Only apply break cost if the destination is NOT water
        // (Water is passable for swimming, not something to be broken)
        double breakCost = 0.0;
        if (!MovementHelper.isWater(destState)) {
            breakCost =
                    MovementHelper.getMiningDurationTicks(context, x, destY, z, destState, false)
                            * UNDERWATER_MINING_MULTIPLIER;
        }

        return baseCost + breakCost;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);

        // Check if we've reached the destination
        if (ctx.playerFeet().equals(dest)) {
            return state.setStatus(MovementStatus.SUCCESS);
        }

        // Calculate vertical pitch (looking up or down)
        float targetYaw = ctx.player().getYRot(); // Keep current yaw
        float targetPitch = ascending ? -30.0f : 30.0f; // Look up or down

        // Queue rotation and apply swimming inputs via RotationManager
        Agent agent = (Agent) maestro;
        agent.getRotationManager()
                .queue(
                        targetYaw,
                        targetPitch,
                        RotationManager.Priority.NORMAL,
                        () -> {
                            // Callback: apply swimming behavior after rotation queued
                            agent.getSwimmingBehavior().applySwimmingInputs(state, dest.y);
                        });

        return state;
    }
}
