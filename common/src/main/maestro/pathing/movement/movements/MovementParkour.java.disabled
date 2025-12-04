package maestro.pathing.movement.movements;

import java.util.HashSet;
import java.util.Set;
import maestro.Agent;
import maestro.api.IAgent;
import maestro.api.pathing.movement.ActionCosts;
import maestro.api.pathing.movement.MovementStatus;
import maestro.api.utils.MaestroLogger;
import maestro.api.utils.PackedBlockPos;
import maestro.api.utils.input.Input;
import maestro.pathing.movement.CalculationContext;
import maestro.pathing.movement.Movement;
import maestro.pathing.movement.MovementHelper;
import maestro.pathing.movement.MovementState;
import maestro.utils.BlockStateInterface;
import maestro.utils.pathing.MutableMoveResult;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.WaterFluid;
import org.slf4j.Logger;

public class MovementParkour extends Movement {
    private static final Logger log = MaestroLogger.get("move");

    private static final PackedBlockPos[] EMPTY = new PackedBlockPos[] {};

    private final Direction direction;
    private final int dist;
    private final boolean ascend;

    private MovementParkour(
            IAgent maestro, PackedBlockPos src, int dist, Direction dir, boolean ascend) {
        super(
                maestro,
                src,
                src.relative(dir, dist).above(ascend ? 1 : 0),
                EMPTY,
                src.relative(dir, dist).below(ascend ? 0 : 1));
        this.direction = dir;
        this.dist = dist;
        this.ascend = ascend;
    }

    public static MovementParkour cost(
            CalculationContext context, PackedBlockPos src, Direction direction) {
        MutableMoveResult res = new MutableMoveResult();
        cost(context, src.getX(), src.getY(), src.getZ(), direction, res);
        int dist = Math.abs(res.x - src.getX()) + Math.abs(res.z - src.getZ());
        return new MovementParkour(context.getMaestro(), src, dist, direction, res.y > src.getY());
    }

    public static void cost(
            CalculationContext context, int x, int y, int z, Direction dir, MutableMoveResult res) {
        if (!context.allowParkour) {
            return;
        }
        if (!context.allowJumpAtBuildLimit && y >= context.world.getMaxY()) {
            return;
        }
        int xDiff = dir.getStepX();
        int zDiff = dir.getStepZ();
        if (!MovementHelper.fullyPassable(context, x + xDiff, y, z + zDiff)) {
            // most common case at the top -- the adjacent block isn't air
            return;
        }
        BlockState adj = context.get(x + xDiff, y - 1, z + zDiff);
        if (MovementHelper.canWalkOn(
                context, x + xDiff, y - 1, z + zDiff,
                adj)) { // don't parkour if we could just traverse (for now)
            // second most common case -- we could just traverse not parkour
            return;
        }
        if (MovementHelper.avoidWalkingInto(adj)
                && !(adj.getFluidState().getType() instanceof WaterFluid)) { // magma sucks
            return;
        }
        if (!MovementHelper.fullyPassable(context, x + xDiff, y + 1, z + zDiff)) {
            return;
        }
        if (!MovementHelper.fullyPassable(context, x + xDiff, y + 2, z + zDiff)) {
            return;
        }
        if (!MovementHelper.fullyPassable(context, x, y + 2, z)) {
            return;
        }
        BlockState standingOn = context.get(x, y - 1, z);
        if (standingOn.getBlock() == Blocks.VINE
                || standingOn.getBlock() == Blocks.LADDER
                || standingOn.getBlock() instanceof StairBlock
                || MovementHelper.isBottomSlab(standingOn)) {
            return;
        }
        // we can't jump from (frozen) water with assumeWalkOnWater because we can't be sure it will
        // be frozen
        if (context.assumeWalkOnWater && !standingOn.getFluidState().isEmpty()) {
            return;
        }
        if (!context.get(x, y, z).getFluidState().isEmpty()) {
            return; // can't jump out of water
        }
        int maxJump;
        if (standingOn.getBlock() == Blocks.SOUL_SAND) {
            maxJump = 2; // 1 block gap
        } else {
            if (context.canSprint) {
                maxJump = 4;
            } else {
                maxJump = 3;
            }
        }

        // check parkour jumps from smallest to largest for obstacles/walls and landing positions
        int verifiedMaxJump = 1; // i - 1 (when i = 2)
        for (int i = 2; i <= maxJump; i++) {
            int destX = x + xDiff * i;
            int destZ = z + zDiff * i;

            // check head/feet
            if (!MovementHelper.fullyPassable(context, destX, y + 1, destZ)) {
                break;
            }
            if (!MovementHelper.fullyPassable(context, destX, y + 2, destZ)) {
                break;
            }

            // check for ascend landing position
            BlockState destInto = context.bsi.get0(destX, y, destZ);
            if (!MovementHelper.fullyPassable(context, destX, y, destZ, destInto)) {
                if (i <= 3
                        && context.allowParkourAscend
                        && context.canSprint
                        && MovementHelper.canWalkOn(context, destX, y, destZ, destInto)
                        && checkOvershootSafety(context.bsi, destX + xDiff, y + 1, destZ + zDiff)) {
                    res.x = destX;
                    res.y = y + 1;
                    res.z = destZ;
                    res.cost = i * ActionCosts.SPRINT_ONE_BLOCK_COST + context.jumpPenalty;
                    return;
                }
                break;
            }

            // check for flat landing position
            BlockState landingOn = context.bsi.get0(destX, y - 1, destZ);
            // farmland needs to be canWalkOn otherwise farm can never work at all, but we want to
            // specifically disallow ending a jump on farmland haha
            // frostwalker works here because we can't jump from possibly unfrozen water
            if ((landingOn.getBlock() != Blocks.FARMLAND
                            && MovementHelper.canWalkOn(context, destX, y - 1, destZ, landingOn))
                    || (Math.min(16, context.frostWalker + 2) >= i
                            && MovementHelper.canUseFrostWalker(context, landingOn))) {
                if (checkOvershootSafety(context.bsi, destX + xDiff, y, destZ + zDiff)) {
                    res.x = destX;
                    res.y = y;
                    res.z = destZ;
                    res.cost = costFromJumpDistance(i) + context.jumpPenalty;
                    return;
                }
                break;
            }

            if (!MovementHelper.fullyPassable(context, destX, y + 3, destZ)) {
                break;
            }

            verifiedMaxJump = i;
        }

        // parkour place starts here
        if (!context.allowParkourPlace) {
            return;
        }
        // check parkour jumps from largest to smallest for positions to place blocks
        for (int i = verifiedMaxJump; i > 1; i--) {
            int destX = x + i * xDiff;
            int destZ = z + i * zDiff;
            BlockState toReplace = context.get(destX, y - 1, destZ);
            double placeCost = context.costOfPlacingAt(destX, y - 1, destZ, toReplace);
            if (placeCost >= ActionCosts.COST_INF) {
                continue;
            }
            if (!MovementHelper.isReplaceable(destX, y - 1, destZ, toReplace, context.bsi)) {
                continue;
            }
            if (!checkOvershootSafety(context.bsi, destX + xDiff, y, destZ + zDiff)) {
                continue;
            }
            for (int j = 0; j < 5; j++) {
                int againstX =
                        destX
                                + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP
                                        .get(j)
                                        .getStepX();
                int againstY =
                        y
                                - 1
                                + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP
                                        .get(j)
                                        .getStepY();
                int againstZ =
                        destZ
                                + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP
                                        .get(j)
                                        .getStepZ();
                if (againstX == destX - xDiff
                        && againstZ == destZ - zDiff) { // we can't turn around that fast
                    continue;
                }
                if (MovementHelper.canPlaceAgainst(context.bsi, againstX, againstY, againstZ)) {
                    res.x = destX;
                    res.y = y;
                    res.z = destZ;
                    res.cost = costFromJumpDistance(i) + placeCost + context.jumpPenalty;
                    return;
                }
            }
        }
    }

    private static boolean checkOvershootSafety(BlockStateInterface bsi, int x, int y, int z) {
        // we're going to walk into these two blocks after the landing of the parkour anyway, so
        // make sure they aren't avoidWalkingInto
        return !MovementHelper.avoidWalkingInto(bsi.get0(x, y, z))
                && !MovementHelper.avoidWalkingInto(bsi.get0(x, y + 1, z));
    }

    private static double costFromJumpDistance(int dist) {
        return switch (dist) {
            case 2 -> ActionCosts.WALK_ONE_BLOCK_COST * 2;
            case 3 -> ActionCosts.WALK_ONE_BLOCK_COST * 3;
            case 4 -> ActionCosts.SPRINT_ONE_BLOCK_COST * 4;
            default -> throw new IllegalStateException("Invalid distance: " + dist);
        };
    }

    @Override
    public double calculateCost(CalculationContext context) {
        MutableMoveResult res = new MutableMoveResult();
        cost(context, src.getX(), src.getY(), src.getZ(), direction, res);
        if (res.x != dest.getX() || res.y != dest.getY() || res.z != dest.getZ()) {
            return ActionCosts.COST_INF;
        }
        return res.cost;
    }

    @Override
    protected Set<PackedBlockPos> calculateValidPositions() {
        Set<PackedBlockPos> set = new HashSet<>();
        for (int i = 0; i <= dist; i++) {
            for (int y = 0; y < 2; y++) {
                set.add(src.relative(direction, i).above(y));
            }
        }
        return set;
    }

    @Override
    public boolean safeToCancel(MovementState state) {
        // once this movement is instantiated, the state is default to PREPPING
        // but once it's ticked for the first time it changes to RUNNING
        // since we don't really know anything about momentum, it suffices to say Parkour can only
        // be canceled on the 0th tick
        return state.getStatus() != MovementStatus.RUNNING;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }
        if (ctx.playerFeet().getY() < src.getY()) {
            // we have fallen
            log.atDebug()
                    .addKeyValue("expected_y", src.getY())
                    .addKeyValue("actual_y", ctx.playerFeet().getY())
                    .log("Parkour jump failed - player fell below start position");
            return state.setStatus(MovementStatus.UNREACHABLE);
        }
        if (dist >= 4 || ascend) {
            maestro.getInputOverrideHandler().setInputForceState(Input.SPRINT, true);
        }
        MovementHelper.moveTowards(ctx, state, dest.toBlockPos(), maestro);
        if (ctx.playerFeet().toBlockPos().equals(dest.toBlockPos())) {
            Block d = BlockStateInterface.getBlock(ctx, dest.toBlockPos());
            if (d == Blocks.VINE || d == Blocks.LADDER) {
                // it physically hurt me to add support for parkour jumping onto a vine
                // but I did it anyway
                return state.setStatus(MovementStatus.SUCCESS);
            }
            if (ctx.player().position().y - ctx.playerFeet().getY() < 0.094) { // lilypads
                state.setStatus(MovementStatus.SUCCESS);
            }
        } else if (!ctx.playerFeet().toBlockPos().equals(src.toBlockPos())) {
            if (ctx.playerFeet().toBlockPos().equals(src.relative(direction).toBlockPos())
                    || ctx.player().position().y - src.getY() > 0.0001) {
                if (Agent.settings().allowPlace.value // see PR #3775
                        && ((Agent) maestro).getInventoryBehavior().hasGenericThrowaway()
                        && !MovementHelper.canWalkOn(ctx, dest.toBlockPos().below())
                        && !ctx.player().onGround()
                        && MovementHelper.attemptToPlaceABlock(
                                        state, maestro, dest.toBlockPos().below(), true, false)
                                == PlaceResult.READY_TO_PLACE) {
                    // go in the opposite order to check DOWN before all horizontals -- down is
                    // preferable because you don't have to look to the side while in midair, which
                    // could mess up the trajectory
                    maestro.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                }
                // prevent jumping too late by checking for ascend
                if (dist == 3 && !ascend) { // this is a 2 block gap, dest = src + direction * 3
                    double xDiff = (src.getX() + 0.5) - ctx.player().position().x;
                    double zDiff = (src.getZ() + 0.5) - ctx.player().position().z;
                    double distFromStart = Math.max(Math.abs(xDiff), Math.abs(zDiff));
                    if (distFromStart < 0.7) {
                        return state;
                    }
                }

                maestro.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
            } else if (!ctx.playerFeet()
                    .toBlockPos()
                    .equals(dest.relative(direction, -1).toBlockPos())) {
                maestro.getInputOverrideHandler().setInputForceState(Input.SPRINT, false);
                if (ctx.playerFeet()
                        .toBlockPos()
                        .equals(src.relative(direction, -1).toBlockPos())) {
                    MovementHelper.moveTowards(ctx, state, src.toBlockPos(), maestro);
                } else {
                    MovementHelper.moveTowards(
                            ctx, state, src.relative(direction, -1).toBlockPos(), maestro);
                }
            }
        }
        return state;
    }
}
