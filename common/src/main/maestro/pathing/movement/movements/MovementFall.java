package maestro.pathing.movement.movements;

import java.util.HashSet;
import java.util.Set;
import maestro.api.IAgent;
import maestro.api.pathing.movement.ActionCosts;
import maestro.api.pathing.movement.MovementStatus;
import maestro.api.utils.PackedBlockPos;
import maestro.api.utils.Rotation;
import maestro.api.utils.RotationUtils;
import maestro.api.utils.VecUtils;
import maestro.api.utils.input.Input;
import maestro.pathing.movement.CalculationContext;
import maestro.pathing.movement.Movement;
import maestro.pathing.movement.MovementHelper;
import maestro.pathing.movement.MovementState;
import maestro.pathing.movement.MovementState.MovementTarget;
import maestro.utils.pathing.MutableMoveResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.Vec3;

public class MovementFall extends Movement {

    private static final ItemStack STACK_BUCKET_WATER = new ItemStack(Items.WATER_BUCKET);
    private static final ItemStack STACK_BUCKET_EMPTY = new ItemStack(Items.BUCKET);

    public MovementFall(IAgent maestro, PackedBlockPos src, PackedBlockPos dest) {
        super(maestro, src, dest, MovementFall.buildPositionsToBreak(src, dest));
    }

    @Override
    public double calculateCost(CalculationContext context) {
        MutableMoveResult result = new MutableMoveResult();
        MovementDescend.cost(
                context, src.getX(), src.getY(), src.getZ(), dest.getX(), dest.getZ(), result);
        if (result.y != dest.getY()) {
            return ActionCosts
                    .COST_INF; // doesn't apply to us, this position is a descent not a fall
        }
        return result.cost;
    }

    @Override
    protected Set<PackedBlockPos> calculateValidPositions() {
        Set<PackedBlockPos> set = new HashSet<>();
        set.add(src);
        for (int y = src.getY() - dest.getY(); y >= 0; y--) {
            set.add(dest.above(y));
        }
        return set;
    }

    private boolean willPlaceBucket() {
        CalculationContext context = new CalculationContext(maestro);
        MutableMoveResult result = new MutableMoveResult();
        return MovementDescend.dynamicFallCost(
                context,
                src.getX(),
                src.getY(),
                src.getZ(),
                dest.getX(),
                dest.getZ(),
                0,
                context.get(dest.getX(), src.getY() - 2, dest.getZ()),
                result);
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        BlockPos playerFeet = ctx.playerFeetBlockPos();
        Rotation toDest =
                RotationUtils.calcRotationFromVec3d(
                        ctx.playerHead(),
                        VecUtils.getBlockPosCenter(dest.toBlockPos()),
                        ctx.playerRotations());
        Rotation targetRotation = null;
        BlockState destState = ctx.world().getBlockState(dest.toBlockPos());
        Block destBlock = destState.getBlock();
        boolean isWater = destState.getFluidState().getType() instanceof WaterFluid;
        if (!isWater && willPlaceBucket() && !playerFeet.equals(dest.toBlockPos())) {
            if (!Inventory.isHotbarSlot(
                            ctx.player().getInventory().findSlotMatchingItem(STACK_BUCKET_WATER))
                    || ctx.world().dimension() == Level.NETHER) {
                return state.setStatus(MovementStatus.UNREACHABLE);
            }

            if (ctx.player().position().y - dest.getY()
                            < ctx.playerController().getBlockReachDistance()
                    && !ctx.player().onGround()) {
                ctx.player().getInventory().selected =
                        ctx.player().getInventory().findSlotMatchingItem(STACK_BUCKET_WATER);

                targetRotation = new Rotation(toDest.getYaw(), 90.0F);

                if (ctx.isLookingAt(dest.toBlockPos())
                        || ctx.isLookingAt(dest.below().toBlockPos())) {
                    state.setInput(Input.CLICK_RIGHT, true);
                }
            }
        }
        if (targetRotation != null) {
            state.setTarget(new MovementTarget(targetRotation, true));
        } else {
            state.setTarget(new MovementTarget(toDest, false));
        }
        if (playerFeet.equals(dest.toBlockPos())
                && (ctx.player().position().y - playerFeet.getY() < 0.094
                        || isWater)) { // 0.094 because lilypads
            if (isWater) { // only match water, not flowing water (which we cannot pick up with a
                // bucket)
                if (Inventory.isHotbarSlot(
                        ctx.player().getInventory().findSlotMatchingItem(STACK_BUCKET_EMPTY))) {
                    ctx.player().getInventory().selected =
                            ctx.player().getInventory().findSlotMatchingItem(STACK_BUCKET_EMPTY);
                    if (ctx.player().getDeltaMovement().y >= 0) {
                        return state.setInput(Input.CLICK_RIGHT, true);
                    } else {
                        return state;
                    }
                } else {
                    if (ctx.player().getDeltaMovement().y >= 0) {
                        return state.setStatus(MovementStatus.SUCCESS);
                    } // don't else return state; we need to stay centered because this water might
                    // be flowing under the surface
                }
            } else {
                return state.setStatus(MovementStatus.SUCCESS);
            }
        }
        Vec3 destCenter =
                VecUtils.getBlockPosCenter(
                        dest.toBlockPos()); // we are moving to the 0.5 center not the edge
        // (like if we were
        // falling on a ladder)
        if (Math.abs(ctx.player().position().x + ctx.player().getDeltaMovement().x - destCenter.x)
                        > 0.1
                || Math.abs(
                                ctx.player().position().z
                                        + ctx.player().getDeltaMovement().z
                                        - destCenter.z)
                        > 0.1) {
            if (!ctx.player().onGround() && Math.abs(ctx.player().getDeltaMovement().y) > 0.4) {
                state.setInput(Input.SNEAK, true);
            }
            state.setInput(Input.MOVE_FORWARD, true);
        }
        Direction avoidDir = avoid();
        Vec3i avoid;
        if (avoidDir == null) {
            avoid =
                    new Vec3i(
                            src.getX() - dest.getX(),
                            src.getY() - dest.getY(),
                            src.getZ() - dest.getZ());
        } else {
            avoid = new Vec3i(avoidDir.getStepX(), avoidDir.getStepY(), avoidDir.getStepZ());
            double dist =
                    Math.abs(
                                    avoid.getX()
                                            * (destCenter.x
                                                    - avoid.getX() / 2.0
                                                    - ctx.player().position().x))
                            + Math.abs(
                                    avoid.getZ()
                                            * (destCenter.z
                                                    - avoid.getZ() / 2.0
                                                    - ctx.player().position().z));
            if (dist < 0.6) {
                state.setInput(Input.MOVE_FORWARD, true);
            } else if (!ctx.player().onGround()) {
                state.setInput(Input.SNEAK, false);
            }
        }
        if (targetRotation == null) {
            Vec3 destCenterOffset =
                    new Vec3(
                            destCenter.x + 0.125 * avoid.getX(),
                            destCenter.y,
                            destCenter.z + 0.125 * avoid.getZ());
            state.setTarget(
                    new MovementTarget(
                            RotationUtils.calcRotationFromVec3d(
                                    ctx.playerHead(), destCenterOffset, ctx.playerRotations()),
                            false));
        }
        return state;
    }

    private Direction avoid() {
        for (int i = 0; i < 15; i++) {
            BlockState state = ctx.world().getBlockState(ctx.playerFeet().below(i).toBlockPos());
            if (state.getBlock() == Blocks.LADDER) {
                return state.getValue(LadderBlock.FACING);
            }
        }
        return null;
    }

    @Override
    public boolean safeToCancel(MovementState state) {
        // if we haven't started walking off the edge yet, or if we're in the process of breaking
        // blocks before doing the fall
        // then it's safe to cancel this
        return ctx.playerFeet().toBlockPos().equals(src.toBlockPos())
                || state.getStatus() != MovementStatus.RUNNING;
    }

    private static PackedBlockPos[] buildPositionsToBreak(PackedBlockPos src, PackedBlockPos dest) {
        PackedBlockPos[] toBreak;
        int diffX = src.getX() - dest.getX();
        int diffZ = src.getZ() - dest.getZ();
        int diffY = Math.abs(src.getY() - dest.getY());
        toBreak = new PackedBlockPos[diffY + 2];
        for (int i = 0; i < toBreak.length; i++) {
            toBreak[i] =
                    new PackedBlockPos(src.getX() - diffX, src.getY() + 1 - i, src.getZ() - diffZ);
        }
        return toBreak;
    }

    @Override
    protected boolean prepared(MovementState state) {
        if (state.getStatus() == MovementStatus.WAITING) {
            return true;
        }
        // only break if one of the first three needs to be broken
        // specifically ignore the last one which might be water
        for (int i = 0; i < 4 && i < positionsToBreak.length; i++) {
            if (!MovementHelper.canWalkThrough(ctx, positionsToBreak[i])) {
                return super.prepared(state);
            }
        }
        return true;
    }
}
