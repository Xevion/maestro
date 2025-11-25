package maestro.pathing.movement.movements;

import static maestro.api.pathing.movement.ActionCosts.*;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import maestro.Agent;
import maestro.api.IAgent;
import maestro.api.pathing.movement.MovementStatus;
import maestro.api.utils.BetterBlockPos;
import maestro.api.utils.Rotation;
import maestro.api.utils.RotationUtils;
import maestro.api.utils.VecUtils;
import maestro.api.utils.input.Input;
import maestro.behavior.RotationManager;
import maestro.pathing.movement.CalculationContext;
import maestro.pathing.movement.Movement;
import maestro.pathing.movement.MovementHelper;
import maestro.pathing.movement.MovementState;
import maestro.utils.BlockStateInterface;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

        // Calculate current position and target for continuous error correction
        Vec3 currentPos = ctx.player().position();
        Vec3 targetPos = Vec3.atCenterOf(dest);

        // Check if we've reached the destination (with tolerance for continuous movement)
        if (isCloseEnough(currentPos, targetPos)) {
            return state.setStatus(MovementStatus.SUCCESS);
        }

        // CRITICAL: Calculate rotation from CURRENT position, not stale src
        // This enables continuous error correction for drift from water currents
        double dx = targetPos.x - currentPos.x;
        double dy = targetPos.y - currentPos.y;
        double dz = targetPos.z - currentPos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Calculate yaw to face destination (updated every tick)
        float targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;

        // CRITICAL: Dynamic pitch based on vertical error (not hardcoded 5.0°)
        float targetPitch = calculateDynamicPitch(dy, horizontalDist);

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
     * Calculate dynamic pitch angle based on vertical and horizontal error. Uses arctangent of
     * vertical/horizontal ratio for natural angle calculation, with approach damping to prevent
     * overshooting when close to target.
     *
     * @param verticalError Vertical distance to target (positive = need to go up)
     * @param horizontalDist Horizontal distance to target
     * @return Pitch angle (negative = up, positive = down)
     */
    private float calculateDynamicPitch(double verticalError, double horizontalDist) {
        // Avoid division by zero
        if (horizontalDist < 0.01) {
            // Pure vertical movement, use steep angle
            return verticalError > 0 ? -45.0f : 45.0f;
        }

        // Calculate angle from error ratio: atan(vertical/horizontal)
        double angle = Math.atan2(verticalError, horizontalDist);
        float pitch = (float) (-angle * 180.0 / Math.PI); // Negative because pitch is inverted

        // Apply approach damping: reduce angle when very close to prevent overshooting
        double totalDist =
                Math.sqrt(verticalError * verticalError + horizontalDist * horizontalDist);
        if (totalDist < 2.0) {
            // Within 2 blocks: scale pitch down
            // At 2 blocks: 100% pitch, at 1 block: 50% pitch, at 0.5 blocks: 25% pitch
            float dampingFactor = (float) (totalDist / 2.0);
            pitch *= dampingFactor;
        }

        // Clamp to reasonable range (-60° to +60°)
        return Math.max(-60.0f, Math.min(60.0f, pitch));
    }

    /**
     * Check if position is close enough to destination. Uses 0.5 block tolerance for continuous
     * movement to allow smooth arrival without overshooting.
     *
     * @param current Current position
     * @param target Target position
     * @return True if within tolerance
     */
    private boolean isCloseEnough(Vec3 current, Vec3 target) {
        return Math.abs(current.x - target.x) <= 0.5
                && Math.abs(current.y - target.y) <= 0.5
                && Math.abs(current.z - target.z) <= 0.5;
    }

    @Override
    protected boolean prepared(MovementState state) {
        if (state.getStatus() == MovementStatus.WAITING) {
            return true;
        }

        boolean somethingInTheWay = false;
        for (BetterBlockPos blockPos : positionsToBreak) {
            // Check for falling blocks (same as base implementation)
            if (!ctx.world()
                            .getEntitiesOfClass(
                                    FallingBlockEntity.class,
                                    new AABB(0, 0, 0, 1, 1.1, 1).move(blockPos))
                            .isEmpty()
                    && Agent.settings().pauseMiningForFallingBlocks.value) {
                return false;
            }

            // CRITICAL: Use canSwimThrough instead of canWalkThrough
            // This prevents breaking water, water plants, bubble columns
            if (!MovementHelper.canSwimThrough(ctx, blockPos)) {
                somethingInTheWay = true;
                MovementHelper.switchToBestToolFor(ctx, BlockStateInterface.get(ctx, blockPos));
                Optional<Rotation> reachable =
                        RotationUtils.reachable(
                                ctx, blockPos, ctx.playerController().getBlockReachDistance());
                if (reachable.isPresent()) {
                    Rotation rotTowardsBlock = reachable.get();
                    state.setTarget(new MovementState.MovementTarget(rotTowardsBlock, true));
                    if (ctx.isLookingAt(blockPos)
                            || ctx.playerRotations().isReallyCloseTo(rotTowardsBlock)) {
                        state.setInput(Input.CLICK_LEFT, true);
                    }
                    return false;
                }
                // Fallback when can't see block directly
                state.setTarget(
                        new MovementState.MovementTarget(
                                RotationUtils.calcRotationFromVec3d(
                                        ctx.playerHead(),
                                        VecUtils.getBlockPosCenter(blockPos),
                                        ctx.playerRotations()),
                                true));
                state.setInput(Input.CLICK_LEFT, true);
                return false;
            }
        }
        if (somethingInTheWay) {
            state.setStatus(MovementStatus.UNREACHABLE);
            return true;
        }
        return true;
    }
}
