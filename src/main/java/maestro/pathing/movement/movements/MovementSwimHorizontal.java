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
import net.minecraft.world.level.block.state.BlockState;

/**
 * Horizontal swimming movement in water (cardinal directions: N, S, E, W). This movement enables
 * true underwater navigation by swimming in cardinal horizontal directions at the same Y level.
 *
 * <p>Cost Model:
 *
 * <ul>
 *   <li>Base: SWIM_ONE_BLOCK_COST = 5.0 (faster than walking in water)
 *   <li>Flowing water: 1.3x multiplier (30% slower against current)
 *   <li>Water plants: 1.2x multiplier (20% slower through kelp/seagrass)
 *   <li>Mining underwater: 5x vanilla mining speed penalty
 * </ul>
 *
 * <p>Execution uses RotationManager + SwimmingBehavior for smooth movement.
 */
public class MovementSwimHorizontal extends Movement {

    public MovementSwimHorizontal(IAgent maestro, BetterBlockPos from, BetterBlockPos to) {
        super(maestro, from, to, new BetterBlockPos[] {to});
    }

    @Override
    public double calculateCost(CalculationContext context) {
        return cost(context, src.x, src.y, src.z, dest.x, dest.y, dest.z);
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        return ImmutableSet.of(src, dest);
    }

    /**
     * Calculate cost for horizontal swimming movement.
     *
     * @param context Calculation context with settings
     * @param x Source X
     * @param y Source Y
     * @param z Source Z
     * @param destX Destination X
     * @param destY Destination Y
     * @param destZ Destination Z
     * @return Cost in ticks, or COST_INF if movement is not possible
     */
    public static double cost(
            CalculationContext context, int x, int y, int z, int destX, int destY, int destZ) {
        double baseCost = SWIM_ONE_BLOCK_COST;

        // Swimming must be enabled
        if (!context.allowSwimming) {
            if (Agent.settings().logSwimming.value) {
                System.out.println(
                        "[SwimH] COST_INF: Swimming disabled at " + x + "," + y + "," + z);
            }
            return COST_INF;
        }

        // Both source and destination must be water
        BlockState srcState = context.get(x, y, z);
        BlockState destState = context.get(destX, destY, destZ);
        boolean srcIsWater = MovementHelper.isWater(srcState);
        boolean destIsWater = MovementHelper.isWater(destState);
        if (!srcIsWater || !destIsWater) {
            if (Agent.settings().logSwimming.value) {
                System.out.println(
                        "[SwimH] COST_INF: Not water - src="
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
                                + destX
                                + ","
                                + destY
                                + ","
                                + destZ);
            }
            return COST_INF;
        }

        // Destination must be passable for swimming
        if (!MovementHelper.canSwimThrough(context, destX, destY, destZ)) {
            if (Agent.settings().logSwimming.value) {
                System.out.println(
                        "[SwimH] COST_INF: Can't swim through at "
                                + destX
                                + ","
                                + destY
                                + ","
                                + destZ);
            }
            return COST_INF;
        }

        if (Agent.settings().logSwimming.value) {
            System.out.println(
                    "[SwimH] Cost="
                            + baseCost
                            + " at "
                            + x
                            + ","
                            + y
                            + ","
                            + z
                            + " -> "
                            + destX
                            + ","
                            + destY
                            + ","
                            + destZ);
        }

        // Apply flowing water penalty (30% slower)
        if (MovementHelper.isFlowingWater(destState)) {
            baseCost *= SWIM_FLOWING_WATER_MULTIPLIER;
        }

        // Apply water plant penalty (20% slower through kelp/seagrass)
        BlockState above = context.get(destX, destY + 1, destZ);
        if (MovementHelper.isWaterPlant(above)) {
            baseCost *= SWIM_THROUGH_PLANTS_MULTIPLIER;
        }

        // Only apply break cost if the destination is NOT water
        // (Water is passable for swimming, not something to be broken)
        double breakCost = 0.0;
        if (!MovementHelper.isWater(destState)) {
            breakCost =
                    MovementHelper.getMiningDurationTicks(
                                    context, destX, destY, destZ, destState, false)
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

        // Calculate yaw to face destination
        float targetYaw = calculateYaw(src, dest);
        float targetPitch = 5.0f; // Slight downward to stay submerged

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

    /**
     * Calculate yaw angle to face from source to destination (horizontal only).
     *
     * @param src Source position
     * @param dest Destination position
     * @return Yaw angle in degrees
     */
    private float calculateYaw(BetterBlockPos src, BetterBlockPos dest) {
        double dx = dest.x - src.x;
        double dz = dest.z - src.z;
        return (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
    }
}
