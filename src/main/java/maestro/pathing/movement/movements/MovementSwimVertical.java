package maestro.pathing.movement.movements;

import static maestro.api.pathing.movement.ActionCosts.*;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import maestro.Agent;
import maestro.api.IAgent;
import maestro.api.pathing.movement.MovementStatus;
import maestro.api.utils.BetterBlockPos;
import maestro.api.utils.MaestroLogger;
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
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

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
    private static final Logger log = MaestroLogger.get("swim");

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
                log.atDebug()
                        .addKeyValue("x", x)
                        .addKeyValue("y", y)
                        .addKeyValue("z", z)
                        .addKeyValue("reason", "swimming_disabled")
                        .log("Vertical swim rejected");
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
                log.atDebug()
                        .addKeyValue("x", x)
                        .addKeyValue("src_y", y)
                        .addKeyValue("dest_y", destY)
                        .addKeyValue("z", z)
                        .addKeyValue("src_water", srcIsWater)
                        .addKeyValue("dest_water", destIsWater)
                        .addKeyValue("ascending", ascending)
                        .addKeyValue("reason", "not_water")
                        .log("Vertical swim rejected");
            }
            return COST_INF;
        }

        // Destination must be passable for swimming
        if (!MovementHelper.canSwimThrough(context, x, destY, z)) {
            if (Agent.settings().logSwimming.value) {
                log.atDebug()
                        .addKeyValue("x", x)
                        .addKeyValue("y", destY)
                        .addKeyValue("z", z)
                        .addKeyValue("reason", "blocked")
                        .log("Vertical swim rejected");
            }
            return COST_INF;
        }

        if (Agent.settings().logSwimming.value) {
            log.atDebug()
                    .addKeyValue("cost", baseCost)
                    .addKeyValue("x", x)
                    .addKeyValue("src_y", y)
                    .addKeyValue("dest_y", destY)
                    .addKeyValue("z", z)
                    .addKeyValue("ascending", ascending)
                    .log("Vertical swim cost calculated");
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

        // Calculate current position and target for continuous error correction
        Vec3 currentPos = ctx.player().position();
        Vec3 targetPos = Vec3.atCenterOf(dest);

        // Check if we've reached the destination (with tolerance for continuous movement)
        if (isCloseEnough(currentPos, targetPos)) {
            return state.setStatus(MovementStatus.SUCCESS);
        }

        // CRITICAL: Calculate full 3D error vector for proper diagonal movement
        double dx = targetPos.x - currentPos.x;
        double dy = targetPos.y - currentPos.y;
        double dz = targetPos.z - currentPos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // CRITICAL: Calculate yaw to face destination (not preserve current yaw)
        float targetYaw;
        if (horizontalDist > 0.1) {
            targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        } else {
            // Pure vertical movement (directly above/below), keep current yaw
            targetYaw = ctx.player().getYRot();
        }

        // CRITICAL: Dynamic pitch based on actual vertical error (not fixed ±30°)
        float targetPitch = calculateDynamicVerticalPitch(dy, horizontalDist);

        // Detect surface to prevent jumping out of water
        if (ascending
                && !MovementHelper.isWater(
                        ctx.world().getBlockState(new BlockPos(dest.x, dest.y + 1, dest.z)))) {
            // Destination is at surface, limit pitch to prevent breaching
            targetPitch = Math.max(targetPitch, -20.0f);
        }

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
     * Calculate dynamic pitch for vertical movement. Steeper angles when close to pure vertical,
     * shallower when horizontal component exists. Uses approach damping to prevent overshooting.
     *
     * @param verticalError Vertical distance to target (positive = need to go up)
     * @param horizontalDist Horizontal distance to target
     * @return Pitch angle (negative = up, positive = down)
     */
    private float calculateDynamicVerticalPitch(double verticalError, double horizontalDist) {
        // For pure vertical movement (minimal horizontal distance)
        if (horizontalDist < 0.1) {
            // Use steep angle but not quite 90° (swimming physics doesn't like pure vertical)
            return verticalError > 0 ? -60.0f : 60.0f;
        }

        // Calculate natural angle from error ratio
        double angle = Math.atan2(verticalError, horizontalDist);
        float pitch = (float) (-angle * 180.0 / Math.PI);

        // Apply approach damping
        double totalDist =
                Math.sqrt(verticalError * verticalError + horizontalDist * horizontalDist);
        if (totalDist < 2.0) {
            float dampingFactor = (float) (totalDist / 2.0);
            pitch *= dampingFactor;
        }

        // Clamp to wider range for vertical movement
        return Math.max(-70.0f, Math.min(70.0f, pitch));
    }

    /**
     * Check if position is close enough to destination. Uses tighter vertical tolerance (0.3
     * blocks) for more precise positioning.
     *
     * @param current Current position
     * @param target Target position
     * @return True if within tolerance
     */
    private boolean isCloseEnough(Vec3 current, Vec3 target) {
        return Math.abs(current.x - target.x) <= 0.5
                && Math.abs(current.y - target.y) <= 0.3 // Tighter vertical tolerance
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
