package maestro.pathing.movement.movements;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import maestro.Agent;
import maestro.api.IAgent;
import maestro.api.pathing.movement.ActionCosts;
import maestro.api.pathing.movement.MovementStatus;
import maestro.api.utils.MaestroLogger;
import maestro.api.utils.PackedBlockPos;
import maestro.api.utils.Rotation;
import maestro.api.utils.RotationUtils;
import maestro.api.utils.VecUtils;
import maestro.api.utils.input.Input;
import maestro.pathing.movement.CalculationContext;
import maestro.pathing.movement.Movement;
import maestro.pathing.movement.MovementHelper;
import maestro.pathing.movement.MovementState;
import maestro.utils.BlockStateInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class MovementPillar extends Movement {
    private static final Logger log = MaestroLogger.get("move");

    public MovementPillar(IAgent maestro, PackedBlockPos start, PackedBlockPos end) {
        super(maestro, start, end, new PackedBlockPos[] {start.above(2)}, start);
    }

    @Override
    public double calculateCost(CalculationContext context) {
        return cost(context, src.getX(), src.getY(), src.getZ());
    }

    @Override
    protected Set<PackedBlockPos> calculateValidPositions() {
        return ImmutableSet.of(src, dest);
    }

    public static double cost(CalculationContext context, int x, int y, int z) {
        BlockState fromState = context.get(x, y, z);
        Block from = fromState.getBlock();
        boolean ladder = from == Blocks.LADDER || from == Blocks.VINE;
        BlockState fromDown = context.get(x, y - 1, z);
        if (!ladder) {
            if (fromDown.getBlock() == Blocks.LADDER || fromDown.getBlock() == Blocks.VINE) {
                return ActionCosts
                        .COST_INF; // can't pillar from a ladder or vine onto something that isn't
                // also climbable
            }
            if (fromDown.getBlock() instanceof SlabBlock
                    && fromDown.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
                return ActionCosts.COST_INF; // can't pillar up from a bottom slab onto a non ladder
            }
        }
        if (from == Blocks.VINE
                && !hasAgainst(
                        context, x, y,
                        z)) { // TODO this vine can't be climbed, but we could place a pillar still
            // since vines are replacable, no? perhaps the pillar jump would be
            // impossible because of the slowdown actually.
            return ActionCosts.COST_INF;
        }
        BlockState toBreak = context.get(x, y + 2, z);
        Block toBreakBlock = toBreak.getBlock();
        if (toBreakBlock instanceof FenceGateBlock) { // see issue #172
            return ActionCosts.COST_INF;
        }
        BlockState srcUp = null;
        if (MovementHelper.isWater(toBreak)
                && MovementHelper.isWater(
                        fromState)) { // TODO should this also be allowed if toBreakBlock is air?
            srcUp = context.get(x, y + 1, z);
            if (MovementHelper.isWater(srcUp)) {
                return ActionCosts
                        .LADDER_UP_ONE_COST; // allow ascending pillars of water, but only if we're
                // already in one
            }
        }
        double placeCost = 0;
        if (!ladder) {
            // we need to place a block where we started to jump on it
            placeCost = context.costOfPlacingAt(x, y, z, fromState);
            if (placeCost >= ActionCosts.COST_INF) {
                return ActionCosts.COST_INF;
            }
            if (fromDown.getBlock() instanceof AirBlock) {
                placeCost += 0.1; // slightly (1/200th of a second) penalize pillaring on what's
                // currently air
            }
        }
        if ((MovementHelper.isLiquid(fromState)
                        && !MovementHelper.canPlaceAgainst(context.bsi, x, y - 1, z, fromDown))
                || (MovementHelper.isLiquid(fromDown) && context.assumeWalkOnWater)) {
            // otherwise, if we're standing in water, we cannot pillar
            // if we're standing on water and assumeWalkOnWater is true, we cannot pillar
            // if we're standing on water and assumeWalkOnWater is false, we must have ascended to
            // here, or sneak backplaced, so it is possible to pillar again
            return ActionCosts.COST_INF;
        }
        if ((from == Blocks.LILY_PAD || from instanceof CarpetBlock)
                && !fromDown.getFluidState().isEmpty()) {
            // to ascend here we'd have to break the block we are standing on
            return ActionCosts.COST_INF;
        }
        double hardness =
                MovementHelper.getMiningDurationTicks(context, x, y + 2, z, toBreak, true);
        if (hardness >= ActionCosts.COST_INF) {
            return ActionCosts.COST_INF;
        }
        if (hardness != 0) {
            if (toBreakBlock == Blocks.LADDER || toBreakBlock == Blocks.VINE) {
                hardness =
                        0; // we won't actually need to break the ladder / vine because we're going
                // to use it
            } else {
                BlockState check =
                        context.get(
                                x, y + 3,
                                z); // the block on top of the one we're going to break, could it
                // fall on us?
                if (check.getBlock() instanceof FallingBlock) {
                    // see MovementAscend's identical check for breaking a falling block above our
                    // head
                    if (srcUp == null) {
                        srcUp = context.get(x, y + 1, z);
                    }
                    if (!(toBreakBlock instanceof FallingBlock)
                            || !(srcUp.getBlock() instanceof FallingBlock)) {
                        return ActionCosts.COST_INF;
                    }
                }
                // this is commented because it may have had a purpose, but it's very unclear what
                // it was. it's from the minebot era.
                // if (!MovementHelper.canWalkOn(context, chkPos, check) ||
                // MovementHelper.canWalkThrough(context, chkPos, check)) {//if the block above
                // where we want to break is not a full block, don't do it
                // TODO why does canWalkThrough mean this action is ActionCosts.COST_INF?
                // FallingBlock makes sense, and !canWalkOn deals with weird cases like if it were
                // lava
                // but I don't understand why canWalkThrough makes it impossible
                //    return ActionCosts.COST_INF;
                // }
            }
        }
        if (ladder) {
            return ActionCosts.LADDER_UP_ONE_COST + hardness * 5;
        } else {
            return ActionCosts.JUMP_ONE_BLOCK_COST + placeCost + context.jumpPenalty + hardness;
        }
    }

    public static boolean hasAgainst(CalculationContext context, int x, int y, int z) {
        return MovementHelper.isBlockNormalCube(context.get(x + 1, y, z))
                || MovementHelper.isBlockNormalCube(context.get(x - 1, y, z))
                || MovementHelper.isBlockNormalCube(context.get(x, y, z + 1))
                || MovementHelper.isBlockNormalCube(context.get(x, y, z - 1));
    }

    public static BlockPos getAgainst(CalculationContext context, PackedBlockPos vine) {
        PackedBlockPos north = vine.north();
        if (MovementHelper.isBlockNormalCube(context.get(north.toBlockPos()))) {
            return north.toBlockPos();
        }
        PackedBlockPos south = vine.south();
        if (MovementHelper.isBlockNormalCube(context.get(south.toBlockPos()))) {
            return south.toBlockPos();
        }
        PackedBlockPos east = vine.east();
        if (MovementHelper.isBlockNormalCube(context.get(east.toBlockPos()))) {
            return east.toBlockPos();
        }
        PackedBlockPos west = vine.west();
        if (MovementHelper.isBlockNormalCube(context.get(west.toBlockPos()))) {
            return west.toBlockPos();
        }
        return null;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        if (ctx.playerFeet().getY() < src.getY()) {
            return state.setStatus(MovementStatus.UNREACHABLE);
        }

        BlockState fromDown = BlockStateInterface.get(ctx, src.toBlockPos());
        if (MovementHelper.isWater(fromDown) && MovementHelper.isWater(ctx, dest.toBlockPos())) {
            // stay centered while swimming up a water column
            state.setTarget(
                    new MovementState.MovementTarget(
                            RotationUtils.calcRotationFromVec3d(
                                    ctx.playerHead(),
                                    VecUtils.getBlockPosCenter(dest.toBlockPos()),
                                    ctx.playerRotations()),
                            false));
            Vec3 destCenter = VecUtils.getBlockPosCenter(dest.toBlockPos());
            if (Math.abs(ctx.player().position().x - destCenter.x) > 0.2
                    || Math.abs(ctx.player().position().z - destCenter.z) > 0.2) {
                state.setInput(Input.MOVE_FORWARD, true);
            }
            if (ctx.playerFeet().toBlockPos().equals(dest.toBlockPos())) {
                return state.setStatus(MovementStatus.SUCCESS);
            }
            return state;
        }
        boolean ladder = fromDown.getBlock() == Blocks.LADDER || fromDown.getBlock() == Blocks.VINE;
        boolean vine = fromDown.getBlock() == Blocks.VINE;
        Rotation rotation =
                RotationUtils.calcRotationFromVec3d(
                        ctx.playerHead(),
                        VecUtils.getBlockPosCenter(src.toBlockPos()),
                        ctx.playerRotations());
        if (!ladder) {
            state.setTarget(
                    new MovementState.MovementTarget(
                            ctx.playerRotations().withPitch(rotation.getPitch()), true));
        }

        boolean blockIsThere = MovementHelper.canWalkOn(ctx, src) || ladder;
        if (ladder) {
            BlockPos against =
                    vine
                            ? getAgainst(new CalculationContext(maestro), src)
                            : src.toBlockPos()
                                    .relative(fromDown.getValue(LadderBlock.FACING).getOpposite());
            if (against == null) {
                log.atError()
                        .addKeyValue("vine_position_x", src.getX())
                        .addKeyValue("vine_position_y", src.getY())
                        .addKeyValue("vine_position_z", src.getZ())
                        .log("Cannot climb vines - no adjacent support blocks found");
                return state.setStatus(MovementStatus.UNREACHABLE);
            }

            if (ctx.playerFeet().toBlockPos().equals(against.above())
                    || ctx.playerFeet().toBlockPos().equals(dest.toBlockPos())) {
                return state.setStatus(MovementStatus.SUCCESS);
            }
            if (MovementHelper.isBottomSlab(
                    BlockStateInterface.get(ctx, src.toBlockPos().below()))) {
                state.setInput(Input.JUMP, true);
            }
            /*
            if (thePlayer.getPosition0().getX() != from.getX() || thePlayer.getPosition0().getZ() != from.getZ()) {
                Maestro.moveTowardsBlock(from);
            }
             */

            MovementHelper.moveTowards(ctx, state, against);
            return state;
        } else {
            // Get ready to place a throwaway block
            if (!((Agent) maestro)
                    .getInventoryBehavior()
                    .selectThrowawayForLocation(true, src.getX(), src.getY(), src.getZ())) {
                return state.setStatus(MovementStatus.UNREACHABLE);
            }

            state.setInput(
                    Input.SNEAK,
                    ctx.player().position().y > dest.getY()
                            || ctx.player().position().y
                                    < src.getY() + 0.2D); // delay placement by 1 tick for ncp
            // compatibility
            // since (lower down) we only right-click once player.isSneaking, and that happens the
            // tick after we request to sneak

            double diffX = ctx.player().position().x - (dest.getX() + 0.5);
            double diffZ = ctx.player().position().z - (dest.getZ() + 0.5);
            double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
            double flatMotion =
                    Math.sqrt(
                            ctx.player().getDeltaMovement().x * ctx.player().getDeltaMovement().x
                                    + ctx.player().getDeltaMovement().z
                                            * ctx.player().getDeltaMovement().z);
            if (dist > 0.17) { // why 0.17? because it seemed like a good number, that's why
                // needs to be less than 0.2 because of the 0.3 sneak limit
                // and 0.17 is reasonably less than 0.2

                // If it's been more than forty ticks of trying to jump, and we aren't done yet, go
                // forward, maybe we are stuck
                state.setInput(Input.MOVE_FORWARD, true);

                // revise our target to both yaw and pitch if we're going to be moving forward
                state.setTarget(new MovementState.MovementTarget(rotation, true));
            } else if (flatMotion < 0.05) {
                // If our Y coordinate is above our goal, stop jumping
                state.setInput(Input.JUMP, ctx.player().position().y < dest.getY());
            }

            if (!blockIsThere) {
                BlockState frState = BlockStateInterface.get(ctx, src.toBlockPos());
                Block fr = frState.getBlock();
                // TODO: Evaluate usage of getMaterial().isReplaceable()
                if (!(fr instanceof AirBlock || frState.canBeReplaced())) {
                    RotationUtils.reachable(
                                    ctx,
                                    src.toBlockPos(),
                                    ctx.playerController().getBlockReachDistance())
                            .map(rot -> new MovementState.MovementTarget(rot, true))
                            .ifPresent(state::setTarget);
                    state.setInput(
                            Input.JUMP, false); // breaking is like 5x slower when you're jumping
                    state.setInput(Input.CLICK_LEFT, true);
                    blockIsThere = false;
                } else if (ctx.player().isCrouching()
                        && (ctx.isLookingAt(src.toBlockPos().below())
                                || ctx.isLookingAt(src.toBlockPos()))
                        && ctx.player().position().y > dest.getY() + 0.1) {
                    state.setInput(Input.CLICK_RIGHT, true);
                }
            }
        }

        // If we are at our goal and the block below us is placed
        if (ctx.playerFeet().toBlockPos().equals(dest.toBlockPos()) && blockIsThere) {
            return state.setStatus(MovementStatus.SUCCESS);
        }

        return state;
    }

    @Override
    protected boolean prepared(MovementState state) {
        if (ctx.playerFeet().toBlockPos().equals(src.toBlockPos())
                || ctx.playerFeet().toBlockPos().equals(src.toBlockPos().below())) {
            Block block = BlockStateInterface.getBlock(ctx, src.toBlockPos().below());
            if (block == Blocks.LADDER || block == Blocks.VINE) {
                state.setInput(Input.SNEAK, true);
            }
        }
        if (MovementHelper.isWater(ctx, dest.toBlockPos().above())) {
            return true;
        }
        return super.prepared(state);
    }
}
