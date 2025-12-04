package maestro.pathing.movement.movements;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import maestro.Agent;
import maestro.api.IAgent;
import maestro.api.pathing.movement.ActionCosts;
import maestro.api.pathing.movement.MovementStatus;
import maestro.api.utils.PackedBlockPos;
import maestro.api.utils.input.Input;
import maestro.pathing.movement.CalculationContext;
import maestro.pathing.movement.Movement;
import maestro.pathing.movement.MovementHelper;
import maestro.pathing.movement.MovementState;
import maestro.utils.BlockStateInterface;
import maestro.utils.pathing.MutableMoveResult;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class MovementDiagonal extends Movement {

    private static final double SQRT_2 = Math.sqrt(2);

    public MovementDiagonal(
            IAgent maestro, PackedBlockPos start, Direction dir1, Direction dir2, int dy) {
        this(maestro, start, start.relative(dir1), start.relative(dir2), dir2, dy);
        // super(start, start.offset(dir1).offset(dir2), new BlockPos[]{start.offset(dir1),
        // start.offset(dir1).up(), start.offset(dir2), start.offset(dir2).up(),
        // start.offset(dir1).offset(dir2), start.offset(dir1).offset(dir2).up()}, new
        // BlockPos[]{start.offset(dir1).offset(dir2).down()});
    }

    private MovementDiagonal(
            IAgent maestro,
            PackedBlockPos start,
            PackedBlockPos dir1,
            PackedBlockPos dir2,
            Direction drr2,
            int dy) {
        this(maestro, start, dir1.relative(drr2).above(dy), dir1, dir2);
    }

    private MovementDiagonal(
            IAgent maestro,
            PackedBlockPos start,
            PackedBlockPos end,
            PackedBlockPos dir1,
            PackedBlockPos dir2) {
        super(
                maestro,
                start,
                end,
                new PackedBlockPos[] {dir1, dir1.above(), dir2, dir2.above(), end, end.above()});
    }

    @Override
    protected boolean safeToCancel(MovementState state) {
        // too simple. backfill does not work after cornering with this
        // return context.precomputedData.canWalkOn(ctx, ctx.playerFeet().down());
        LocalPlayer player = ctx.player();
        double offset = 0.25;
        double x = player.position().x;
        double y = player.position().y - 1;
        double z = player.position().z;
        // standard
        if (ctx.playerFeet().toBlockPos().equals(src.toBlockPos())) {
            return true;
        }
        // both corners are walkable
        if (MovementHelper.canWalkOn(ctx, new BlockPos(src.getX(), src.getY() - 1, dest.getZ()))
                && MovementHelper.canWalkOn(
                        ctx, new BlockPos(dest.getX(), src.getY() - 1, src.getZ()))) {
            return true;
        }
        // we are in a likely unwalkable corner, check for a supporting block
        if (ctx.playerFeet()
                        .toBlockPos()
                        .equals(
                                new PackedBlockPos(src.getX(), src.getY(), dest.getZ())
                                        .toBlockPos())
                || ctx.playerFeet()
                        .toBlockPos()
                        .equals(
                                new PackedBlockPos(dest.getX(), src.getY(), src.getZ())
                                        .toBlockPos())) {
            return (MovementHelper.canWalkOn(
                            ctx,
                            new PackedBlockPos((int) (x + offset), (int) y, (int) (z + offset)))
                    || MovementHelper.canWalkOn(
                            ctx,
                            new PackedBlockPos((int) (x + offset), (int) y, (int) (z - offset)))
                    || MovementHelper.canWalkOn(
                            ctx,
                            new PackedBlockPos((int) (x - offset), (int) y, (int) (z + offset)))
                    || MovementHelper.canWalkOn(
                            ctx,
                            new PackedBlockPos((int) (x - offset), (int) y, (int) (z - offset))));
        }
        return true;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        MutableMoveResult result = new MutableMoveResult();
        cost(context, src.getX(), src.getY(), src.getZ(), dest.getX(), dest.getZ(), result);
        if (result.y != dest.getY()) {
            return ActionCosts.COST_INF; // doesn't apply to us, this position is incorrect
        }
        return result.cost;
    }

    @Override
    protected Set<PackedBlockPos> calculateValidPositions() {
        PackedBlockPos diagA = new PackedBlockPos(src.getX(), src.getY(), dest.getZ());
        PackedBlockPos diagB = new PackedBlockPos(dest.getX(), src.getY(), src.getZ());
        if (dest.getY() < src.getY()) {
            return ImmutableSet.of(
                    src, dest.above(), diagA, diagB, dest, diagA.below(), diagB.below());
        }
        if (dest.getY() > src.getY()) {
            return ImmutableSet.of(
                    src, src.above(), diagA, diagB, dest, diagA.above(), diagB.above());
        }
        return ImmutableSet.of(src, dest, diagA, diagB);
    }

    public static void cost(
            CalculationContext context,
            int x,
            int y,
            int z,
            int destX,
            int destZ,
            MutableMoveResult res) {
        if (!MovementHelper.canWalkThrough(context, destX, y + 1, destZ)) {
            return;
        }
        BlockState destInto = context.get(destX, y, destZ);
        BlockState fromDown;
        boolean ascend = false;
        BlockState destWalkOn;
        boolean descend = false;
        boolean frostWalker = false;
        if (!MovementHelper.canWalkThrough(context, destX, y, destZ, destInto)) {
            ascend = true;
            if (!context.allowDiagonalAscend
                    || !MovementHelper.canWalkThrough(context, x, y + 2, z)
                    || !MovementHelper.canWalkOn(context, destX, y, destZ, destInto)
                    || !MovementHelper.canWalkThrough(context, destX, y + 2, destZ)) {
                return;
            }
            destWalkOn = destInto;
            fromDown = context.get(x, y - 1, z);
        } else {
            destWalkOn = context.get(destX, y - 1, destZ);
            fromDown = context.get(x, y - 1, z);
            boolean standingOnABlock =
                    MovementHelper.mustBeSolidToWalkOn(context, x, y - 1, z, fromDown);
            frostWalker = standingOnABlock && MovementHelper.canUseFrostWalker(context, destWalkOn);
            if (!frostWalker
                    && !MovementHelper.canWalkOn(context, destX, y - 1, destZ, destWalkOn)) {
                descend = true;
                if (!context.allowDiagonalDescend
                        || !MovementHelper.canWalkOn(context, destX, y - 2, destZ)
                        || !MovementHelper.canWalkThrough(
                                context, destX, y - 1, destZ, destWalkOn)) {
                    return;
                }
            }
            frostWalker &=
                    !context.assumeWalkOnWater; // do this after checking for descends because jesus
            // can't prevent the water from freezing, it just
            // prevents us from relying on the water freezing
        }
        double multiplier = ActionCosts.WALK_ONE_BLOCK_COST;
        // For either possible soul sand, that affects half of our walking
        if (destWalkOn.getBlock() == Blocks.SOUL_SAND) {
            multiplier +=
                    (ActionCosts.WALK_ONE_OVER_SOUL_SAND_COST - ActionCosts.WALK_ONE_BLOCK_COST)
                            / 2;
        } else if (frostWalker) {
            // frostwalker lets us walk on water without the penalty
        } else if (destWalkOn.getBlock() == Blocks.WATER) {
            multiplier += context.walkOnWaterOnePenalty * SQRT_2;
        }
        Block fromDownBlock = fromDown.getBlock();
        if (fromDownBlock == Blocks.LADDER || fromDownBlock == Blocks.VINE) {
            return;
        }
        if (fromDownBlock == Blocks.SOUL_SAND) {
            multiplier +=
                    (ActionCosts.WALK_ONE_OVER_SOUL_SAND_COST - ActionCosts.WALK_ONE_BLOCK_COST)
                            / 2;
        }
        BlockState cuttingOver1 = context.get(x, y - 1, destZ);
        if (cuttingOver1.getBlock() == Blocks.MAGMA_BLOCK || MovementHelper.isLava(cuttingOver1)) {
            return;
        }
        BlockState cuttingOver2 = context.get(destX, y - 1, z);
        if (cuttingOver2.getBlock() == Blocks.MAGMA_BLOCK || MovementHelper.isLava(cuttingOver2)) {
            return;
        }
        boolean water = false;
        BlockState startState = context.get(x, y, z);
        Block startIn = startState.getBlock();
        if (MovementHelper.isWater(startState) || MovementHelper.isWater(destInto)) {
            if (ascend) {
                return;
            }
            // Ignore previous multiplier
            // Whatever we were walking on (possibly soul sand) doesn't matter as we're actually
            // floating on water
            // Not even touching the blocks below
            multiplier = context.waterWalkSpeed;
            water = true;
        }
        BlockState pb0 = context.get(x, y, destZ);
        BlockState pb2 = context.get(destX, y, z);
        if (ascend) {
            boolean ATop = MovementHelper.canWalkThrough(context, x, y + 2, destZ);
            boolean AMid = MovementHelper.canWalkThrough(context, x, y + 1, destZ);
            boolean ALow = MovementHelper.canWalkThrough(context, x, y, destZ, pb0);
            boolean BTop = MovementHelper.canWalkThrough(context, destX, y + 2, z);
            boolean BMid = MovementHelper.canWalkThrough(context, destX, y + 1, z);
            boolean BLow = MovementHelper.canWalkThrough(context, destX, y, z, pb2);
            if ((!(ATop && AMid && ALow) && !(BTop && BMid && BLow)) // no option
                    || MovementHelper.avoidWalkingInto(pb0) // bad
                    || MovementHelper.avoidWalkingInto(pb2) // bad
                    || (ATop
                            && AMid
                            && MovementHelper.canWalkOn(
                                    context, x, y, destZ, pb0)) // we could just ascend
                    || (BTop
                            && BMid
                            && MovementHelper.canWalkOn(
                                    context, destX, y, z, pb2)) // we could just ascend
                    || (!ATop && AMid && ALow) // head bonk A
                    || (!BTop && BMid && BLow)) { // head bonk B
                return;
            }
            res.cost = multiplier * SQRT_2 + ActionCosts.JUMP_ONE_BLOCK_COST;
            res.x = destX;
            res.z = destZ;
            res.y = y + 1;
            return;
        }
        double optionA = MovementHelper.getMiningDurationTicks(context, x, y, destZ, pb0, false);
        double optionB = MovementHelper.getMiningDurationTicks(context, destX, y, z, pb2, false);
        if (optionA != 0 && optionB != 0) {
            // check these one at a time -- if pb0 and pb2 were nonzero, we already know that
            // (optionA != 0 && optionB != 0)
            // so no need to check pb1 as well, might as well return early here
            return;
        }
        BlockState pb1 = context.get(x, y + 1, destZ);
        optionA += MovementHelper.getMiningDurationTicks(context, x, y + 1, destZ, pb1, true);
        if (optionA != 0 && optionB != 0) {
            // same deal, if pb1 makes optionA nonzero and option B already was nonzero, pb3 can't
            // affect the result
            return;
        }
        BlockState pb3 = context.get(destX, y + 1, z);
        if (optionA == 0
                && ((MovementHelper.avoidWalkingInto(pb2) && pb2.getBlock() != Blocks.WATER)
                        || MovementHelper.avoidWalkingInto(pb3))) {
            // at this point we're done calculating optionA, so we can check if it's actually
            // possible to edge around in that direction
            return;
        }
        optionB += MovementHelper.getMiningDurationTicks(context, destX, y + 1, z, pb3, true);
        if (optionA != 0 && optionB != 0) {
            // and finally, if the cost is nonzero for both ways to approach this diagonal, it's not
            // possible
            return;
        }
        if (optionB == 0
                && ((MovementHelper.avoidWalkingInto(pb0) && pb0.getBlock() != Blocks.WATER)
                        || MovementHelper.avoidWalkingInto(pb1))) {
            // and now that option B is fully calculated, see if we can edge around that way
            return;
        }
        if (optionA != 0 || optionB != 0) {
            multiplier *= SQRT_2 - 0.001; // TODO tune
            if (startIn == Blocks.LADDER || startIn == Blocks.VINE) {
                // edging around doesn't work if doing so would climb a ladder or vine instead of
                // moving sideways
                return;
            }
        } else {
            // only can sprint if not edging around
            if (context.canSprint && !water) {
                // If we aren't edging around anything, and we aren't in water
                // We can sprint =D
                // Don't check for soul sand, since we can sprint on that too
                multiplier *= ActionCosts.SPRINT_MULTIPLIER;
            }
        }
        res.cost = multiplier * SQRT_2;
        if (descend) {
            res.cost +=
                    Math.max(ActionCosts.FALL_N_BLOCKS_COST[1], ActionCosts.CENTER_AFTER_FALL_COST);
            res.y = y - 1;
        } else {
            res.y = y;
        }
        res.x = destX;
        res.z = destZ;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        if (ctx.playerFeet().toBlockPos().equals(dest.toBlockPos())) {
            return state.setStatus(MovementStatus.SUCCESS);
        } else if (!playerInValidPosition()
                && !(MovementHelper.isLiquid(ctx, src.toBlockPos())
                        && getValidPositions().contains(ctx.playerFeet().above()))) {
            return state.setStatus(MovementStatus.UNREACHABLE);
        }
        if (dest.getY() > src.getY()
                && ctx.player().position().y < src.getY() + 0.1
                && ctx.player().horizontalCollision) {
            maestro.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
        }
        if (sprint()) {
            maestro.getInputOverrideHandler().setInputForceState(Input.SPRINT, true);
        }

        // Check if there are still blocks to break (6 positions: dir1, dir1.above, dir2,
        // dir2.above, end, end.above)
        boolean blocksRemaining = false;
        for (PackedBlockPos pos : positionsToBreak) {
            if (!MovementHelper.canWalkThrough(ctx, pos)) {
                blocksRemaining = true;
                break;
            }
        }

        // Don't walk into the block if already close enough and blocks still exist
        double distToTarget =
                Math.max(
                        Math.abs(ctx.player().position().x - (dest.getX() + 0.5D)),
                        Math.abs(ctx.player().position().z - (dest.getZ() + 0.5D)));

        if (blocksRemaining && distToTarget < 0.83) {
            // Already close and blocks still exist - stop moving to prevent drift
            maestro.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
        } else {
            // Keep moving toward target until we reach destination
            MovementHelper.moveTowards(ctx, state, dest.toBlockPos(), maestro);
        }

        return state;
    }

    private boolean sprint() {
        if (MovementHelper.isLiquid(ctx, ctx.playerFeet().toBlockPos())
                && !Agent.settings().sprintInWater.value) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (!MovementHelper.canWalkThrough(ctx, positionsToBreak[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean prepared(MovementState state) {
        return true;
    }

    @Override
    public List<BlockPos> toBreak(BlockStateInterface bsi) {
        if (toBreakCached != null) {
            return toBreakCached;
        }
        List<BlockPos> result = new ArrayList<>();
        for (int i = 4; i < 6; i++) {
            if (!MovementHelper.canWalkThrough(
                    bsi,
                    positionsToBreak[i].getX(),
                    positionsToBreak[i].getY(),
                    positionsToBreak[i].getZ())) {
                result.add(positionsToBreak[i].toBlockPos());
            }
        }
        toBreakCached = result;
        return result;
    }

    @Override
    public List<BlockPos> toWalkInto(BlockStateInterface bsi) {
        if (toWalkIntoCached == null) {
            toWalkIntoCached = new ArrayList<>();
        }
        List<BlockPos> result = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (!MovementHelper.canWalkThrough(
                    bsi,
                    positionsToBreak[i].getX(),
                    positionsToBreak[i].getY(),
                    positionsToBreak[i].getZ())) {
                result.add(positionsToBreak[i].toBlockPos());
            }
        }
        toWalkIntoCached = result;
        return toWalkIntoCached;
    }
}
