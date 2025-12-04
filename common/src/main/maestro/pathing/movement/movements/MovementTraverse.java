package maestro.pathing.movement.movements;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
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
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class MovementTraverse extends Movement {
    private static final Logger log = MaestroLogger.get("move");

    /** Did we have to place a bridge block or was it always there */
    private boolean wasTheBridgeBlockAlwaysThere = true;

    public MovementTraverse(IAgent maestro, PackedBlockPos from, PackedBlockPos to) {
        super(maestro, from, to, new PackedBlockPos[] {to.above(), to}, to.below());
    }

    @Override
    public void reset() {
        super.reset();
        wasTheBridgeBlockAlwaysThere = true;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        return cost(context, src.getX(), src.getY(), src.getZ(), dest.getX(), dest.getZ());
    }

    @Override
    protected Set<PackedBlockPos> calculateValidPositions() {
        return ImmutableSet.of(
                src, dest); // src.above means that we don't get caught in an infinite loop in water
    }

    public static double cost(
            CalculationContext context, int x, int y, int z, int destX, int destZ) {
        BlockState pb0 = context.get(destX, y + 1, destZ);
        BlockState pb1 = context.get(destX, y, destZ);
        BlockState destOn = context.get(destX, y - 1, destZ);
        BlockState srcDown = context.get(x, y - 1, z);
        Block srcDownBlock = srcDown.getBlock();
        boolean standingOnABlock =
                MovementHelper.mustBeSolidToWalkOn(context, x, y - 1, z, srcDown);
        boolean frostWalker =
                standingOnABlock
                        && !context.assumeWalkOnWater
                        && MovementHelper.canUseFrostWalker(context, destOn);
        if (frostWalker
                || MovementHelper.canWalkOn(
                        context, destX, y - 1, destZ, destOn)) { // this is a walk, not a bridge
            double WC = ActionCosts.WALK_ONE_BLOCK_COST;
            boolean water = false;
            if (MovementHelper.isWater(pb0) || MovementHelper.isWater(pb1)) {
                WC = context.waterWalkSpeed;
                water = true;
            } else {
                if (destOn.getBlock() == Blocks.SOUL_SAND) {
                    WC +=
                            (ActionCosts.WALK_ONE_OVER_SOUL_SAND_COST
                                            - ActionCosts.WALK_ONE_BLOCK_COST)
                                    / 2;
                } else if (frostWalker) {
                    // with frostwalker we can walk on water without the penalty, if we are sure we
                    // won't be using jesus
                } else if (destOn.getBlock() == Blocks.WATER) {
                    WC += context.walkOnWaterOnePenalty;
                }
                if (srcDownBlock == Blocks.SOUL_SAND) {
                    WC +=
                            (ActionCosts.WALK_ONE_OVER_SOUL_SAND_COST
                                            - ActionCosts.WALK_ONE_BLOCK_COST)
                                    / 2;
                }
            }
            double hardness1 =
                    MovementHelper.getMiningDurationTicks(context, destX, y, destZ, pb1, false);
            if (hardness1 >= ActionCosts.COST_INF) {
                return ActionCosts.COST_INF;
            }
            double hardness2 =
                    MovementHelper.getMiningDurationTicks(
                            context, destX, y + 1, destZ, pb0,
                            true); // only include falling on the upper block to break
            if (hardness1 == 0 && hardness2 == 0) {
                if (!water && context.canSprint) {
                    // If there's nothing in the way, and this isn't water, and we aren't sneak
                    // placing
                    // We can sprint =D
                    // Don't check for soul sand, since we can sprint on that too
                    WC *= ActionCosts.SPRINT_MULTIPLIER;
                }
                return WC;
            }
            if (srcDownBlock == Blocks.LADDER || srcDownBlock == Blocks.VINE) {
                hardness1 *= 5;
                hardness2 *= 5;
            }
            return WC + hardness1 + hardness2;
        } else { // this is a bridge, so we need to place a block
            if (srcDownBlock == Blocks.LADDER || srcDownBlock == Blocks.VINE) {
                return ActionCosts.COST_INF;
            }
            if (MovementHelper.isReplaceable(destX, y - 1, destZ, destOn, context.bsi)) {
                boolean throughWater = MovementHelper.isWater(pb0) || MovementHelper.isWater(pb1);
                if (MovementHelper.isWater(destOn) && throughWater) {
                    // this happens when assume walk on water is true and this is a traverse in
                    // water, which isn't allowed
                    return ActionCosts.COST_INF;
                }
                double placeCost = context.costOfPlacingAt(destX, y - 1, destZ, destOn);
                if (placeCost >= ActionCosts.COST_INF) {
                    return ActionCosts.COST_INF;
                }
                double hardness1 =
                        MovementHelper.getMiningDurationTicks(context, destX, y, destZ, pb1, false);
                if (hardness1 >= ActionCosts.COST_INF) {
                    return ActionCosts.COST_INF;
                }
                double hardness2 =
                        MovementHelper.getMiningDurationTicks(
                                context, destX, y + 1, destZ, pb0,
                                true); // only include falling on the upper block to break
                double WC = throughWater ? context.waterWalkSpeed : ActionCosts.WALK_ONE_BLOCK_COST;
                for (int i = 0; i < 5; i++) {
                    int againstX =
                            destX
                                    + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP
                                            .get(i)
                                            .getStepX();
                    int againstY =
                            y
                                    - 1
                                    + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP
                                            .get(i)
                                            .getStepY();
                    int againstZ =
                            destZ
                                    + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP
                                            .get(i)
                                            .getStepZ();
                    if (againstX == x && againstZ == z) { // this would be a backplace
                        continue;
                    }
                    if (MovementHelper.canPlaceAgainst(
                            context.bsi,
                            againstX,
                            againstY,
                            againstZ)) { // found a side place option
                        return WC + placeCost + hardness1 + hardness2;
                    }
                }
                // now that we've checked all possible directions to side place, we actually need to
                // backplace
                if (srcDownBlock == Blocks.SOUL_SAND
                        || (srcDownBlock instanceof SlabBlock
                                && srcDown.getValue(SlabBlock.TYPE) != SlabType.DOUBLE)) {
                    return ActionCosts
                            .COST_INF; // can't sneak and backplace against soul sand or half slabs
                    // (regardless of whether it's top half or bottom half) =/
                }
                if (!standingOnABlock) { // standing on water / swimming
                    return ActionCosts.COST_INF; // this is obviously impossible
                }
                Block blockSrc = context.getBlock(x, y, z);
                if ((blockSrc == Blocks.LILY_PAD || blockSrc instanceof CarpetBlock)
                        && !srcDown.getFluidState().isEmpty()) {
                    return ActionCosts
                            .COST_INF; // we can stand on these but can't place against them
                }
                WC =
                        WC
                                * (ActionCosts.SNEAK_ONE_BLOCK_COST
                                        / ActionCosts.WALK_ONE_BLOCK_COST); // since we are sneak
                // backplacing,
                // we are sneaking lol
                return WC + placeCost + hardness1 + hardness2;
            }
            return ActionCosts.COST_INF;
        }
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        BlockState pb0 = BlockStateInterface.get(ctx, positionsToBreak[0].toBlockPos());
        BlockState pb1 = BlockStateInterface.get(ctx, positionsToBreak[1].toBlockPos());
        if (state.getStatus() != MovementStatus.RUNNING) {
            // if the setting is enabled
            if (!Agent.settings().walkWhileBreaking.value) {
                return state;
            }
            // and if we're prepping (aka mining the block in front)
            if (state.getStatus() != MovementStatus.PREPPING) {
                return state;
            }
            // and if it's fine to walk into the blocks in front
            if (MovementHelper.avoidWalkingInto(pb0)) {
                return state;
            }
            if (MovementHelper.avoidWalkingInto(pb1)) {
                return state;
            }
            // and we aren't already pressed up against the block
            double dist =
                    Math.max(
                            Math.abs(ctx.player().position().x - (dest.getX() + 0.5D)),
                            Math.abs(ctx.player().position().z - (dest.getZ() + 0.5D)));
            if (dist < 0.83) {
                // Already close enough - stop moving forward to prevent drift
                maestro.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
                maestro.getInputOverrideHandler().setInputForceState(Input.SPRINT, false);
                return state;
            }
            if (state.getTarget().getRotation().isEmpty()) {
                // this can happen rarely when the server lags and doesn't send the falling sand
                // entity until you've already walked through the block and are now mining the next
                // one
                return state;
            }

            // combine the yaw to the center of the destination, and the pitch to the specific block
            // we're trying to break
            // it's safe to do this since the two blocks we break (in a traverse) are right on top
            // of each other and so will have the same yaw
            float yawToDest =
                    RotationUtils.calcRotationFromVec3d(
                                    ctx.playerHead(),
                                    VecUtils.calculateBlockCenter(ctx.world(), dest.toBlockPos()),
                                    ctx.playerRotations())
                            .getYaw();
            float pitchToBreak = state.getTarget().getRotation().get().getPitch();
            if ((MovementHelper.isBlockNormalCube(pb0)
                    || (pb0.getBlock() instanceof AirBlock
                            && (MovementHelper.isBlockNormalCube(pb1)
                                    || pb1.getBlock() instanceof AirBlock)))) {
                // in the meantime, before we're right up against the block, we can break
                // efficiently at this angle
                pitchToBreak = 26;
            }

            state.setTarget(
                    new MovementState.MovementTarget(new Rotation(yawToDest, pitchToBreak), true));
            maestro.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
            maestro.getInputOverrideHandler().setInputForceState(Input.SPRINT, true);
            return state;
        }

        // sneak may have been set to true in the PREPPING state while mining an adjacent block
        maestro.getInputOverrideHandler().setInputForceState(Input.SNEAK, false);

        Block fd = BlockStateInterface.get(ctx, src.below().toBlockPos()).getBlock();
        boolean ladder = fd == Blocks.LADDER || fd == Blocks.VINE;

        if (pb0.getBlock() instanceof DoorBlock || pb1.getBlock() instanceof DoorBlock) {
            boolean notPassable =
                    (pb0.getBlock() instanceof DoorBlock
                                    && !MovementHelper.isDoorPassable(
                                            ctx, src.toBlockPos(), dest.toBlockPos()))
                            || (pb1.getBlock() instanceof DoorBlock
                                    && !MovementHelper.isDoorPassable(
                                            ctx, dest.toBlockPos(), src.toBlockPos()));
            boolean canOpen =
                    !(Blocks.IRON_DOOR.equals(pb0.getBlock())
                            || Blocks.IRON_DOOR.equals(pb1.getBlock()));

            if (notPassable && canOpen) {
                state.setTarget(
                        new MovementState.MovementTarget(
                                RotationUtils.calcRotationFromVec3d(
                                        ctx.playerHead(),
                                        VecUtils.calculateBlockCenter(
                                                ctx.world(), positionsToBreak[0].toBlockPos()),
                                        ctx.playerRotations()),
                                true));
                maestro.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                return state;
            }
        }

        if (pb0.getBlock() instanceof FenceGateBlock || pb1.getBlock() instanceof FenceGateBlock) {
            BlockPos blocked =
                    !MovementHelper.isGatePassable(
                                    ctx, positionsToBreak[0].toBlockPos(), src.above().toBlockPos())
                            ? positionsToBreak[0].toBlockPos()
                            : !MovementHelper.isGatePassable(
                                            ctx, positionsToBreak[1].toBlockPos(), src.toBlockPos())
                                    ? positionsToBreak[1].toBlockPos()
                                    : null;
            if (blocked != null) {
                Optional<Rotation> rotation = RotationUtils.reachable(ctx, blocked);
                if (rotation.isPresent()) {
                    state.setTarget(new MovementState.MovementTarget(rotation.get(), true));
                    maestro.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                    return state;
                }
            }
        }

        boolean isTheBridgeBlockThere =
                MovementHelper.canWalkOn(ctx, positionToPlace.toBlockPos())
                        || ladder
                        || MovementHelper.canUseFrostWalker(ctx, positionToPlace.toBlockPos());
        BlockPos feet = ctx.playerFeetBlockPos();
        if (feet.getY() != dest.getY() && !ladder) {
            log.atDebug()
                    .addKeyValue("expected_y", dest.getY())
                    .addKeyValue("actual_y", feet.getY())
                    .addKeyValue("player_x", feet.getX())
                    .addKeyValue("player_z", feet.getZ())
                    .log("Movement Y coordinate mismatch");
            if (feet.getY() < dest.getY()) {
                maestro.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
            }
            return state;
        }

        if (isTheBridgeBlockThere) {
            if (feet.equals(dest.toBlockPos())) {
                return state.setStatus(MovementStatus.SUCCESS);
            }
            if (Agent.settings().overshootTraverse.value
                    && (feet.equals(dest.offset(getDirection()).toBlockPos())
                            || feet.equals(
                                    dest.offset(getDirection())
                                            .offset(getDirection())
                                            .toBlockPos()))) {
                return state.setStatus(MovementStatus.SUCCESS);
            }
            Block low = BlockStateInterface.get(ctx, src.toBlockPos()).getBlock();
            Block high = BlockStateInterface.get(ctx, src.above().toBlockPos()).getBlock();
            if (ctx.player().position().y > src.getY() + 0.1D
                    && !ctx.player().onGround()
                    && (low == Blocks.VINE
                            || low == Blocks.LADDER
                            || high == Blocks.VINE
                            || high == Blocks.LADDER)) {
                // hitting W could cause us to climb the ladder instead of going forward
                // wait until we're on the ground
                return state;
            }
            BlockPos into = dest.toBlockPos().subtract(src.toBlockPos()).offset(dest.toBlockPos());
            BlockState intoBelow = BlockStateInterface.get(ctx, into);
            BlockState intoAbove = BlockStateInterface.get(ctx, into.above());
            if (wasTheBridgeBlockAlwaysThere
                    && (!MovementHelper.isLiquid(ctx, feet) || Agent.settings().sprintInWater.value)
                    && (!MovementHelper.avoidWalkingInto(intoBelow)
                            || MovementHelper.isWater(intoBelow))
                    && !MovementHelper.avoidWalkingInto(intoAbove)) {
                maestro.getInputOverrideHandler().setInputForceState(Input.SPRINT, true);
            }

            BlockState destDown = BlockStateInterface.get(ctx, dest.below().toBlockPos());
            BlockPos against = positionsToBreak[0].toBlockPos();
            if (feet.getY() != dest.getY()
                    && ladder
                    && (destDown.getBlock() == Blocks.VINE
                            || destDown.getBlock() == Blocks.LADDER)) {
                against =
                        destDown.getBlock() == Blocks.VINE
                                ? MovementPillar.getAgainst(
                                        new CalculationContext(maestro), dest.below())
                                : dest.toBlockPos()
                                        .relative(
                                                destDown.getValue(LadderBlock.FACING)
                                                        .getOpposite());
                if (against == null) {
                    log.atError()
                            .addKeyValue("vine_position_x", dest.below().getX())
                            .addKeyValue("vine_position_y", dest.below().getY())
                            .addKeyValue("vine_position_z", dest.below().getZ())
                            .log("Cannot climb vines - no adjacent support blocks found");
                    return state.setStatus(MovementStatus.UNREACHABLE);
                }
            }

            // Check if there are still blocks to break
            boolean blocksRemaining =
                    !MovementHelper.canWalkThrough(ctx, positionsToBreak[0])
                            || !MovementHelper.canWalkThrough(ctx, positionsToBreak[1]);

            // Don't walk into the block if already close enough and blocks still exist
            double distToTarget =
                    Math.max(
                            Math.abs(ctx.player().position().x - (against.getX() + 0.5D)),
                            Math.abs(ctx.player().position().z - (against.getZ() + 0.5D)));

            if (blocksRemaining && distToTarget < 0.83) {
                // Already close and blocks still exist - stop moving to prevent drift
                maestro.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
            } else {
                // Keep moving toward target until we reach destination
                MovementHelper.moveTowards(ctx, state, against, maestro);
            }

            return state;
        } else {
            wasTheBridgeBlockAlwaysThere = false;
            Block standingOn = BlockStateInterface.get(ctx, feet.below()).getBlock();
            if (standingOn.equals(Blocks.SOUL_SAND)
                    || standingOn instanceof SlabBlock) { // see issue #118
                double dist =
                        Math.max(
                                Math.abs(dest.getX() + 0.5D - ctx.player().position().x),
                                Math.abs(dest.getZ() + 0.5D - ctx.player().position().z));
                if (dist < 0.85) { // 0.5 + 0.3 + epsilon
                    MovementHelper.moveTowards(ctx, state, dest.toBlockPos(), maestro);
                    maestro.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
                    maestro.getInputOverrideHandler().setInputForceState(Input.MOVE_BACK, true);
                    return state;
                }
            }
            double dist1 =
                    Math.max(
                            Math.abs(ctx.player().position().x - (dest.getX() + 0.5D)),
                            Math.abs(ctx.player().position().z - (dest.getZ() + 0.5D)));
            PlaceResult p =
                    MovementHelper.attemptToPlaceABlock(
                            state, maestro, dest.below().toBlockPos(), false, true);
            if ((p == PlaceResult.READY_TO_PLACE || dist1 < 0.6)
                    && !Agent.settings().assumeSafeWalk.value) {
                maestro.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
            }
            switch (p) {
                case READY_TO_PLACE:
                    {
                        if (ctx.player().isCrouching() || Agent.settings().assumeSafeWalk.value) {
                            maestro.getInputOverrideHandler()
                                    .setInputForceState(Input.CLICK_RIGHT, true);
                        }
                        return state;
                    }
                case ATTEMPTING:
                    {
                        if (dist1 > 0.83) {
                            // might need to go forward a bit
                            float yaw =
                                    RotationUtils.calcRotationFromVec3d(
                                                    ctx.playerHead(),
                                                    VecUtils.getBlockPosCenter(dest.toBlockPos()),
                                                    ctx.playerRotations())
                                            .getYaw();
                            if (Math.abs(state.getTarget().rotation.getYaw() - yaw) < 0.1) {
                                // but only if our attempted place is straight ahead
                                maestro.getInputOverrideHandler()
                                        .setInputForceState(Input.MOVE_FORWARD, true);
                                return state;
                            }
                        } else if (ctx.playerRotations()
                                .isReallyCloseTo(state.getTarget().rotation)) {
                            // well I guess there's something in the way
                            maestro.getInputOverrideHandler()
                                    .setInputForceState(Input.CLICK_LEFT, true);
                            return state;
                        }
                        return state;
                    }
                default:
                    break;
            }
            if (feet.equals(dest.toBlockPos())) {
                // If we are in the block that we are trying to get to, we are sneaking over air,
                // and
                // we need to place a block beneath us against the one we just walked off of
                // Out.log(from + " " + to + " " + faceX + "," + faceY + "," + faceZ + " " +
                // whereAmI);
                double faceX = (dest.getX() + src.getX() + 1.0D) * 0.5D;
                double faceY = (dest.getY() + src.getY() - 1.0D) * 0.5D;
                double faceZ = (dest.getZ() + src.getZ() + 1.0D) * 0.5D;
                // faceX, faceY, faceZ is the middle of the face between from and to
                BlockPos goalLook =
                        src.below().toBlockPos(); // this is the block we were just standing on, and
                // the one
                // we want to place against

                Rotation backToFace =
                        RotationUtils.calcRotationFromVec3d(
                                ctx.playerHead(),
                                new Vec3(faceX, faceY, faceZ),
                                ctx.playerRotations());
                float pitch = backToFace.getPitch();
                double dist2 =
                        Math.max(
                                Math.abs(ctx.player().position().x - faceX),
                                Math.abs(ctx.player().position().z - faceZ));
                if (dist2 < 0.29) { // see issue #208
                    float yaw =
                            RotationUtils.calcRotationFromVec3d(
                                            VecUtils.getBlockPosCenter(dest.toBlockPos()),
                                            ctx.playerHead(),
                                            ctx.playerRotations())
                                    .getYaw();
                    state.setTarget(
                            new MovementState.MovementTarget(new Rotation(yaw, pitch), true));
                    maestro.getInputOverrideHandler().setInputForceState(Input.MOVE_BACK, true);
                } else {
                    state.setTarget(new MovementState.MovementTarget(backToFace, true));
                }
                if (ctx.isLookingAt(goalLook)) {
                    // wait to right-click until we are able to place
                    maestro.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                    return state;
                }
                // Out.log("Trying to look at " + goalLook + ", actually looking at" +
                // Maestro.whatAreYouLookingAt());
                if (ctx.playerRotations().isReallyCloseTo(state.getTarget().rotation)) {
                    maestro.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                }
                return state;
            }
            MovementHelper.moveTowards(ctx, state, positionsToBreak[0].toBlockPos(), maestro);
            return state;
            // TODO MovementManager.moveTowardsBlock(to); // move towards not look at because if we
            // are bridging for a couple blocks in a row, it is faster if we dont spin around and
            // walk forwards then spin around and place backwards for every block
        }
    }

    @Override
    public boolean safeToCancel(MovementState state) {
        // if we're in the process of breaking blocks before walking forwards
        // or if this isn't a sneak place (the block is already there)
        // then it's safe to cancel this
        return state.getStatus() != MovementStatus.RUNNING
                || MovementHelper.canWalkOn(ctx, dest.below().toBlockPos());
    }

    @Override
    protected boolean prepared(MovementState state) {
        if (ctx.playerFeet().toBlockPos().equals(src.toBlockPos())
                || ctx.playerFeet().toBlockPos().equals(src.below().toBlockPos())) {
            Block block = BlockStateInterface.getBlock(ctx, src.below().toBlockPos());
            if (block == Blocks.LADDER || block == Blocks.VINE) {
                maestro.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
            }
        }
        return super.prepared(state);
    }
}
