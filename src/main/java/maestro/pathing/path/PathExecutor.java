package maestro.pathing.path;

import static maestro.api.pathing.movement.MovementStatus.*;

import java.util.*;
import maestro.Agent;
import maestro.api.pathing.calc.IPath;
import maestro.api.pathing.movement.ActionCosts;
import maestro.api.pathing.movement.IMovement;
import maestro.api.pathing.movement.MovementStatus;
import maestro.api.pathing.path.IPathExecutor;
import maestro.api.utils.*;
import maestro.api.utils.MaestroLogger;
import maestro.api.utils.input.Input;
import maestro.behavior.PathingBehavior;
import maestro.pathing.calc.AbstractNodeCostSearch;
import maestro.pathing.movement.CalculationContext;
import maestro.pathing.movement.Movement;
import maestro.pathing.movement.MovementHelper;
import maestro.pathing.movement.movements.*;
import maestro.pathing.recovery.FailureReason;
import maestro.pathing.recovery.PathRecoveryManager;
import maestro.utils.BlockStateInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Behavior to execute a precomputed path
 *
 * @author leijurv
 */
public class PathExecutor implements IPathExecutor, Helper {
    private static final Logger log = MaestroLogger.get("path");

    private static final double MAX_MAX_DIST_FROM_PATH = 3;
    private static final double MAX_DIST_FROM_PATH = 2;

    /**
     * Default value is equal to 10 seconds. It's find to decrease it, but it must be at least 5.5s
     * (110 ticks). For more information, see issue #102.
     *
     * @see <a href="https://github.com/cabaletta/baritone/issues/102">Issue #102</a>
     * @see <a href="https://i.imgur.com/5s5GLnI.png">Anime</a>
     */
    private static final double MAX_TICKS_AWAY = 200;

    private final IPath path;
    private PathCorridor corridor;
    private int pathPosition;
    private int ticksAway;
    private int ticksOnCurrent;
    private Double currentMovementOriginalCostEstimate;
    private Integer costEstimateIndex;
    private boolean failed;
    private boolean recalcBP = true;
    private HashSet<BlockPos> toBreak = new HashSet<>();
    private HashSet<BlockPos> toPlace = new HashSet<>();
    private HashSet<BlockPos> toWalkInto = new HashSet<>();

    private final PathingBehavior behavior;
    private final IPlayerContext ctx;

    private final PathRecoveryManager recoveryManager;

    private boolean sprintNextTick;

    // Prevent splicing immediately after reconnection to avoid state corruption
    private int ticksSinceReconnection = -1; // -1 means no recent reconnection
    private static final int RECONNECTION_SPLICE_DELAY_TICKS = 4;

    public PathExecutor(PathingBehavior behavior, IPath path) {
        this.behavior = behavior;
        this.ctx = behavior.ctx;
        this.path = path;
        this.corridor = new PathCorridor(path, 0);
        this.pathPosition = 0;
        this.recoveryManager = new PathRecoveryManager(behavior);
    }

    /**
     * Tick this executor
     *
     * @return True if a movement just finished (and the player is therefore in a "stable" state,
     *     like, not sneaking out over lava), false otherwise
     */
    public boolean onTick() {
        if (pathPosition == path.length() - 1) {
            pathPosition++;
        }
        if (pathPosition >= path.length()) {
            ticksSinceReconnection = -1; // Clear for next path
            return true; // stop bugging me, I'm done
        }

        // Increment reconnection counter to allow splicing after delay
        if (ticksSinceReconnection >= 0) {
            if (ticksSinceReconnection < RECONNECTION_SPLICE_DELAY_TICKS) {
                ticksSinceReconnection++;
            } else if (ticksSinceReconnection == RECONNECTION_SPLICE_DELAY_TICKS) {
                ticksSinceReconnection = -1; // Reset after delay expires
            }
        }

        Movement movement = (Movement) path.movements().get(pathPosition);
        BetterBlockPos whereAmI = ctx.playerFeet();
        if (!movement.getValidPositions().contains(whereAmI)) {
            for (int i = 0;
                    i < pathPosition && i < path.length();
                    i++) { // this happens for example when you lag out and get teleported back a
                // couple blocks
                if (((Movement) path.movements().get(i)).getValidPositions().contains(whereAmI)) {
                    int previousPos = pathPosition;
                    pathPosition = i;
                    for (int j = pathPosition; j <= previousPos; j++) {
                        path.movements().get(j).reset();
                    }
                    onChangeInPathPosition();
                    onTick();
                    return false;
                }
            }
            for (int i = pathPosition + 3;
                    i < path.length() - 1;
                    i++) { // dont check pathPosition+1. the movement tells us when it's done (e.g.
                // sneak placing)
                // also don't check pathPosition+2 because reasons
                if (((Movement) path.movements().get(i)).getValidPositions().contains(whereAmI)) {
                    if (i - pathPosition > 2) {
                        log.atDebug()
                                .addKeyValue("skip_steps", i - pathPosition)
                                .addKeyValue("new_position", i)
                                .log("Skipping forward in path");
                    }
                    // System.out.println("Double skip sundae");
                    pathPosition = i - 1;
                    onChangeInPathPosition();
                    onTick();
                    return false;
                }
            }
        }
        if (!corridor.isWithinCorridor(whereAmI)) {
            double distToPath = corridor.distanceToPath(whereAmI);

            if (distToPath > MAX_MAX_DIST_FROM_PATH) {
                log.atDebug()
                        .addKeyValue("distance", distToPath)
                        .addKeyValue("max_distance", MAX_MAX_DIST_FROM_PATH)
                        .log("Too far from path, cancelling");
                cancel();
                return false;
            }

            if (distToPath > MAX_DIST_FROM_PATH) {
                ticksAway++;
                if (ticksAway > MAX_TICKS_AWAY) {
                    // Try recovery via reconnection
                    if (movement.safeToCancel()) {
                        PathRecoveryManager.RecoveryAction action =
                                recoveryManager.handleCorridorDeviation(
                                        whereAmI,
                                        path,
                                        corridor.currentSegment(),
                                        behavior.secretInternalGetCalculationContext());

                        switch (action.type()) {
                            case RECONNECT_PATH:
                                jumpToReconnectionPoint(action.reconnectionIndex());
                                ticksAway = 0;
                                log.atInfo()
                                        .addKeyValue("distance", distToPath)
                                        .addKeyValue("ticks_away", ticksAway)
                                        .log("Successfully reconnected to path");
                                return false;
                            case CANCEL_PATH:
                                // Fall through to cancel below
                                break;
                            case CONTINUE:
                                // Recovery wants us to keep trying - don't cancel yet
                                return false;
                            default:
                                throw new IllegalStateException(
                                        "Unknown recovery type: " + action.type());
                        }
                    }

                    // Only reached if movement not safe to cancel OR recovery returned
                    // CANCEL_PATH
                    log.atDebug()
                            .addKeyValue("ticks_away", ticksAway)
                            .addKeyValue("max_ticks", MAX_TICKS_AWAY)
                            .log("Too far from path for too long, cancelling");
                    cancel();
                    return false;
                }
            } else {
                ticksAway = Math.max(0, ticksAway - 5); // Decay counter
            }
        } else {
            ticksAway = 0;
        }
        // long start = System.nanoTime() / 1000000L;
        BlockStateInterface bsi = new BlockStateInterface(ctx);
        for (int i = pathPosition - 10; i < pathPosition + 10; i++) {
            if (i < 0 || i >= path.movements().size()) {
                continue;
            }
            Movement m = (Movement) path.movements().get(i);
            List<BlockPos> prevBreak = m.toBreak(bsi);
            List<BlockPos> prevPlace = m.toPlace(bsi);
            List<BlockPos> prevWalkInto = m.toWalkInto(bsi);
            m.resetBlockCache();
            if (!prevBreak.equals(m.toBreak(bsi))) {
                recalcBP = true;
            }
            if (!prevPlace.equals(m.toPlace(bsi))) {
                recalcBP = true;
            }
            if (!prevWalkInto.equals(m.toWalkInto(bsi))) {
                recalcBP = true;
            }
        }
        if (recalcBP) {
            HashSet<BlockPos> newBreak = new HashSet<>();
            HashSet<BlockPos> newPlace = new HashSet<>();
            HashSet<BlockPos> newWalkInto = new HashSet<>();
            for (int i = pathPosition; i < path.movements().size(); i++) {
                Movement m = (Movement) path.movements().get(i);
                newBreak.addAll(m.toBreak(bsi));
                newPlace.addAll(m.toPlace(bsi));
                newWalkInto.addAll(m.toWalkInto(bsi));
            }
            toBreak = newBreak;
            toPlace = newPlace;
            toWalkInto = newWalkInto;
            recalcBP = false;
        }
        if (pathPosition < path.movements().size() - 1) {
            IMovement next = path.movements().get(pathPosition + 1);
            if (!behavior.maestro.bsi.worldContainsLoadedChunk(
                    next.getDest().x, next.getDest().z)) {
                log.atDebug()
                        .addKeyValue("dest_x", next.getDest().x)
                        .addKeyValue("dest_z", next.getDest().z)
                        .log("Pausing - destination at edge of loaded chunks");
                stopMovement();
                return true;
            }
        }
        boolean canCancel = movement.safeToCancel();
        if (costEstimateIndex == null || costEstimateIndex != pathPosition) {
            costEstimateIndex = pathPosition;
            // do this only once, when the movement starts, and deliberately get the cost as cached
            // when this path was calculated, not the cost as it is right now
            currentMovementOriginalCostEstimate = movement.getCost();
            for (int i = 1;
                    i < Agent.settings().costVerificationLookahead.value
                            && pathPosition + i < path.length() - 1;
                    i++) {
                Movement futureMove = (Movement) path.movements().get(pathPosition + i);
                double futureCost =
                        futureMove.calculateCost(behavior.secretInternalGetCalculationContext());
                if (futureCost >= ActionCosts.COST_INF && canCancel) {
                    log.atDebug()
                            .addKeyValue("movement_index", i)
                            .addKeyValue("movement_type", futureMove.getClass().getSimpleName())
                            .addKeyValue("source", futureMove.getSrc())
                            .addKeyValue("dest", futureMove.getDest())
                            .addKeyValue("cost", futureCost)
                            .log("Future movement became impossible, cancelling path");
                    behavior.failureMemory.recordFailure(futureMove, FailureReason.BLOCKED);
                    cancel();
                    return true;
                }
            }
        }
        double currentCost =
                movement.recalculateCost(behavior.secretInternalGetCalculationContext());
        if (currentCost >= ActionCosts.COST_INF && canCancel) {
            log.atDebug()
                    .addKeyValue("movement_type", movement.getClass().getSimpleName())
                    .addKeyValue("source", movement.getSrc())
                    .addKeyValue("dest", movement.getDest())
                    .addKeyValue("current_cost", currentCost)
                    .addKeyValue("original_cost", currentMovementOriginalCostEstimate)
                    .log("Current movement became impossible, cancelling path");
            behavior.failureMemory.recordFailure(movement, FailureReason.WORLD_CHANGED);
            cancel();
            return true;
        }
        if (!movement.calculatedWhileLoaded()
                && currentCost - currentMovementOriginalCostEstimate
                        > Agent.settings().maxCostIncrease.value
                && canCancel) {
            // don't do this if the movement was calculated while loaded
            // that means that this isn't a cache error, it's just part of the path interfering with
            // a later part
            log.atDebug()
                    .addKeyValue("original_cost", currentMovementOriginalCostEstimate)
                    .addKeyValue("current_cost", currentCost)
                    .addKeyValue("cost_increase", currentCost - currentMovementOriginalCostEstimate)
                    .log("Movement cost increased too much, cancelling");
            behavior.failureMemory.recordFailure(movement, FailureReason.WORLD_CHANGED);
            cancel();
            return true;
        }
        if (shouldPause()) {
            log.atDebug().log("Pausing - current best path is a backtrack");
            stopMovement();
            return true;
        }
        MovementStatus movementStatus = movement.update();
        if (movementStatus == UNREACHABLE || movementStatus == FAILED) {
            log.atDebug().addKeyValue("status", movementStatus).log("Movement failed");
            FailureReason reason =
                    movementStatus == UNREACHABLE
                            ? FailureReason.UNREACHABLE
                            : FailureReason.BLOCKED;
            behavior.failureMemory.recordFailure(movement, reason);

            // Try recovery via alternative movements
            PathRecoveryManager.RecoveryAction action =
                    recoveryManager.handleMovementFailure(
                            movement, ctx.playerFeet(), path, pathPosition, movementStatus, reason);

            switch (action.type()) {
                case RETRY_MOVEMENT:
                    replaceCurrentMovement(action.movement());
                    return false; // Don't cancel - continue with alternative
                case CANCEL_PATH:
                    cancel();
                    return true;
                case CONTINUE:
                    return false; // Keep trying current movement
                default:
                    throw new IllegalStateException("Unknown recovery type: " + action.type());
            }
        }
        if (movementStatus == SUCCESS) {
            // System.out.println("Movement done, next path");
            pathPosition++;
            onChangeInPathPosition();
            onTick();
            return true;
        } else {
            sprintNextTick = shouldSprintNextTick();
            if (!sprintNextTick) {
                ctx.player()
                        .setSprinting(
                                false); // letting go of control doesn't make you stop sprinting
                // actually
            }
            ticksOnCurrent++;
            if (ticksOnCurrent
                    > currentMovementOriginalCostEstimate
                            + Agent.settings().movementTimeoutTicks.value) {
                // only cancel if the total time has exceeded the initial estimate
                // as you break the blocks required, the remaining cost goes down, to the point
                // where
                // ticksOnCurrent is greater than recalculateCost + 100
                // this is why we cache cost at the beginning, and don't recalculate for this
                // comparison every tick
                log.atDebug()
                        .addKeyValue("ticks_taken", ticksOnCurrent)
                        .addKeyValue("expected_ticks", currentMovementOriginalCostEstimate)
                        .addKeyValue("timeout", Agent.settings().movementTimeoutTicks.value)
                        .log("Movement timeout exceeded, cancelling");
                behavior.failureMemory.recordFailure(movement, FailureReason.TIMEOUT);
                cancel();
                return true;
            }
        }
        return canCancel; // movement is in progress, but if it reports cancellable, PathingBehavior
        // is good to cut onto the next path
    }

    private Tuple<Double, BlockPos> closestPathPos(IPath path) {
        double best = -1;
        BlockPos bestPos = null;
        for (IMovement movement : path.movements()) {
            for (BlockPos pos : ((Movement) movement).getValidPositions()) {
                double dist = VecUtils.entityDistanceToCenter(ctx.player(), pos);
                if (dist < best || best == -1) {
                    best = dist;
                    bestPos = pos;
                }
            }
        }
        return new Tuple<>(best, bestPos);
    }

    private boolean shouldPause() {
        Optional<AbstractNodeCostSearch> current = behavior.getInProgress();
        if (current.isEmpty()) {
            return false;
        }
        if (!ctx.player().onGround()) {
            return false;
        }
        if (!MovementHelper.canWalkOn(ctx, ctx.playerFeet().below())) {
            // we're in some kind of sketchy situation, maybe parkouring
            return false;
        }
        if (!MovementHelper.canWalkThrough(ctx, ctx.playerFeet())
                || !MovementHelper.canWalkThrough(ctx, ctx.playerFeet().above())) {
            // suffocating?
            return false;
        }
        if (!path.movements().get(pathPosition).safeToCancel()) {
            return false;
        }
        Optional<IPath> currentBest = current.get().bestPathSoFar();
        if (currentBest.isEmpty()) {
            return false;
        }
        List<BetterBlockPos> positions = currentBest.get().positions();
        if (positions.size() < 3) {
            return false; // not long enough yet to justify pausing, its far from certain we'll
            // actually take this route
        }
        // the first block of the next path will always overlap
        // no need to pause our very last movement when it would have otherwise cleanly exited with
        // MovementStatus SUCCESS
        positions = positions.subList(1, positions.size());
        return positions.contains(ctx.playerFeet());
    }

    private boolean possiblyOffPath(Tuple<Double, BlockPos> status, double leniency) {
        double distanceFromPath = status.getA();
        if (distanceFromPath > leniency) {
            // when we're midair in the middle of a fall, we're very far from both the beginning and
            // the end, but we aren't actually off path
            if (path.movements().get(pathPosition) instanceof MovementFall) {
                BlockPos fallDest =
                        path.positions()
                                .get(
                                        pathPosition
                                                + 1); // .get(pathPosition) is the block we fell off
                // of
                return VecUtils.entityFlatDistanceToCenter(ctx.player(), fallDest)
                        >= leniency; // ignore Y by using flat distance
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    /**
     * Regardless of current path position, snap to the current player feet if possible
     *
     * @return Whether or not it was possible to snap to the current player feet
     */
    public boolean snipsnapifpossible() {
        if (!ctx.player().onGround() && ctx.world().getFluidState(ctx.playerFeet()).isEmpty()) {
            // if we're falling in the air, and not in water, don't splice
            return false;
        } else {
            // we are either onGround or in liquid
            if (ctx.player().getDeltaMovement().y < -0.1) {
                // if we are strictly moving downwards (not stationary)
                // we could be falling through water, which could be unsafe to splice
                return false; // so don't
            }
        }
        int index = path.positions().indexOf(ctx.playerFeet());
        if (index == -1) {
            return false;
        }
        pathPosition = index; // jump directly to current position
        clearKeys();
        return true;
    }

    private boolean shouldSprintNextTick() {
        boolean requested =
                behavior.maestro.getInputOverrideHandler().isInputForcedDown(Input.SPRINT);

        // we'll take it from here, no need for minecraft to see we're holding down control and
        // sprint for us
        behavior.maestro.getInputOverrideHandler().setInputForceState(Input.SPRINT, false);

        // first and foremost, if allowSprint is off, or if we don't have enough hunger, don't try
        // and sprint
        if (!new CalculationContext(behavior.maestro, false).canSprint) {
            return false;
        }
        IMovement current = path.movements().get(pathPosition);

        // traverse requests sprinting, so we need to do this check first
        if (current instanceof MovementTraverse && pathPosition < path.length() - 3) {
            IMovement next = path.movements().get(pathPosition + 1);
            if (next instanceof MovementAscend
                    && sprintableAscend(
                            ctx,
                            (MovementTraverse) current,
                            (MovementAscend) next,
                            path.movements().get(pathPosition + 2))) {
                if (skipNow(ctx, current)) {
                    log.atDebug().log("Skipping traverse to straight ascend");
                    pathPosition++;
                    onChangeInPathPosition();
                    onTick();
                    behavior.maestro.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
                    return true;
                } else {
                    log.atDebug().log("Too far to the side to safely sprint ascend");
                }
            }
        }

        // if the movement requested sprinting, then we're done
        if (requested) {
            return true;
        }

        // however, descend and ascend don't request sprinting, because they don't know the context
        // of what movement comes after it
        if (current instanceof MovementDescend) {

            if (pathPosition < path.length() - 2) {
                // keep this out of onTick, even if that means a tick of delay before it has an
                // effect
                IMovement next = path.movements().get(pathPosition + 1);
                if (MovementHelper.canUseFrostWalker(ctx, next.getDest().below())) {
                    // frostwalker only works if you cross the edge of the block on ground so in
                    // some cases we may not overshoot
                    // Since MovementDescend can't know the next movement we have to tell it
                    if (next instanceof MovementTraverse || next instanceof MovementParkour) {
                        boolean couldPlaceInstead =
                                Agent.settings().allowPlace.value
                                        && behavior.maestro
                                                .getInventoryBehavior()
                                                .hasGenericThrowaway()
                                        && next
                                                instanceof
                                                MovementParkour; // traverse doesn't react fast
                        // enough
                        // this is true if the next movement does not ascend or descends and goes
                        // into the same cardinal direction (N-NE-E-SE-S-SW-W-NW) as the descend
                        // in that case current.getDirection() is e.g. (0, -1, 1) and
                        // next.getDirection() is e.g. (0, 0, 3) so the cross product of (0, 0, 1)
                        // and (0, 0, 3) is taken, which is (0, 0, 0) because the vectors are
                        // colinear (don't form a plane)
                        // since movements in exactly the opposite direction (e.g. descend (0, -1,
                        // 1) and traverse (0, 0, -1)) would also pass this check we also have to
                        // rule out that case
                        // we can do that by adding the directions because traverse is always 1 long
                        // like descend and parkour can't jump through current.getSrc().down()
                        boolean sameFlatDirection =
                                !current.getDirection()
                                                .above()
                                                .offset(next.getDirection())
                                                .equals(BlockPos.ZERO)
                                        && current.getDirection()
                                                .above()
                                                .cross(next.getDirection())
                                                .equals(BlockPos.ZERO); // here's why you learn
                        // maths in school
                        if (sameFlatDirection && !couldPlaceInstead) {
                            ((MovementDescend) current).forceSafeMode();
                        }
                    }
                }
            }
            if (((MovementDescend) current).safeMode()
                    && !((MovementDescend) current).skipToAscend()) {
                log.atDebug().log("Sprinting would be unsafe");
                return false;
            }

            if (pathPosition < path.length() - 2) {
                IMovement next = path.movements().get(pathPosition + 1);
                if (next instanceof MovementAscend
                        && current.getDirection().above().equals(next.getDirection().below())) {
                    // a descend then an ascend in the same direction
                    pathPosition++;
                    onChangeInPathPosition();
                    onTick();
                    // okay to skip clearKeys and / or onChangeInPathPosition here since this isn't
                    // possible to repeat, since it's asymmetric
                    log.atDebug().log("Skipping descend to straight ascend");
                    return true;
                }
                if (canSprintFromDescendInto(ctx, current, next)) {

                    if (next instanceof MovementDescend && pathPosition < path.length() - 3) {
                        IMovement next_next = path.movements().get(pathPosition + 2);
                        if (next_next instanceof MovementDescend
                                && !canSprintFromDescendInto(ctx, next, next_next)) {
                            return false;
                        }
                    }
                    if (ctx.playerFeet().equals(current.getDest())) {
                        pathPosition++;
                        onChangeInPathPosition();
                        onTick();
                    }

                    return true;
                }
                // logDebug("Turning off sprinting " + movement + " " + next + " " +
                // movement.getDirection() + " " + next.getDirection().down() + " " +
                // next.getDirection().down().equals(movement.getDirection()));
            }
        }
        if (current instanceof MovementAscend && pathPosition != 0) {
            IMovement prev = path.movements().get(pathPosition - 1);
            if (prev instanceof MovementDescend
                    && prev.getDirection().above().equals(current.getDirection().below())) {
                BlockPos center = current.getSrc().above();
                // playerFeet adds 0.1251 to account for soul sand
                // farmland is 0.9375
                // 0.07 is to account for farmland
                if (ctx.player().position().y >= center.getY() - 0.07) {
                    behavior.maestro
                            .getInputOverrideHandler()
                            .setInputForceState(Input.JUMP, false);
                    return true;
                }
            }
            if (pathPosition < path.length() - 2
                    && prev instanceof MovementTraverse
                    && sprintableAscend(
                            ctx,
                            (MovementTraverse) prev,
                            (MovementAscend) current,
                            path.movements().get(pathPosition + 1))) {
                return true;
            }
        }
        if (current instanceof MovementFall) {
            Tuple<Vec3, BlockPos> data = overrideFall((MovementFall) current);
            if (data != null) {
                BetterBlockPos fallDest = new BetterBlockPos(data.getB());
                if (!path.positions().contains(fallDest)) {
                    throw new IllegalStateException(
                            String.format(
                                    "Fall override at %s %s %s returned illegal destination %s %s"
                                            + " %s",
                                    current.getSrc(), fallDest));
                }
                if (ctx.playerFeet().equals(fallDest)) {
                    pathPosition = path.positions().indexOf(fallDest);
                    onChangeInPathPosition();
                    onTick();
                    return true;
                }
                clearKeys();
                behavior.maestro
                        .getLookBehavior()
                        .updateTarget(
                                RotationUtils.calcRotationFromVec3d(
                                        ctx.playerHead(), data.getA(), ctx.playerRotations()),
                                false);
                behavior.maestro
                        .getInputOverrideHandler()
                        .setInputForceState(Input.MOVE_FORWARD, true);
                return true;
            }
        }
        return false;
    }

    private Tuple<Vec3, BlockPos> overrideFall(MovementFall movement) {
        Vec3i dir = movement.getDirection();
        if (dir.getY() < -3) {
            return null;
        }
        if (!movement.toBreakCached.isEmpty()) {
            return null; // it's breaking
        }
        Vec3i flatDir = new Vec3i(dir.getX(), 0, dir.getZ());
        int i;
        outer:
        for (i = pathPosition + 1; i < path.length() - 1 && i < pathPosition + 3; i++) {
            IMovement next = path.movements().get(i);
            if (!(next instanceof MovementTraverse)) {
                break;
            }
            if (!flatDir.equals(next.getDirection())) {
                break;
            }
            for (int y = next.getDest().y; y <= movement.getSrc().y + 1; y++) {
                BlockPos chk = new BlockPos(next.getDest().x, y, next.getDest().z);
                if (!MovementHelper.fullyPassable(ctx, chk)) {
                    break outer;
                }
            }
            if (!MovementHelper.canWalkOn(ctx, next.getDest().below())) {
                break;
            }
        }
        i--;
        if (i == pathPosition) {
            return null; // no valid extension exists
        }
        double len = i - pathPosition - 0.4;
        return new Tuple<>(
                new Vec3(
                        flatDir.getX() * len + movement.getDest().x + 0.5,
                        movement.getDest().y,
                        flatDir.getZ() * len + movement.getDest().z + 0.5),
                movement.getDest()
                        .offset(
                                flatDir.getX() * (i - pathPosition),
                                0,
                                flatDir.getZ() * (i - pathPosition)));
    }

    private static boolean skipNow(IPlayerContext ctx, IMovement current) {
        double offTarget =
                Math.abs(
                                current.getDirection().getX()
                                        * (current.getSrc().z + 0.5D - ctx.player().position().z))
                        + Math.abs(
                                current.getDirection().getZ()
                                        * (current.getSrc().x + 0.5D - ctx.player().position().x));
        if (offTarget > 0.1) {
            return false;
        }
        // we are centered
        BlockPos headBonk = current.getSrc().subtract(current.getDirection()).above(2);
        if (MovementHelper.fullyPassable(ctx, headBonk)) {
            return true;
        }
        // wait 0.3
        double flatDist =
                Math.abs(
                                current.getDirection().getX()
                                        * (headBonk.getX() + 0.5D - ctx.player().position().x))
                        + Math.abs(
                                current.getDirection().getZ()
                                        * (headBonk.getZ() + 0.5 - ctx.player().position().z));
        return flatDist > 0.8;
    }

    private static boolean sprintableAscend(
            IPlayerContext ctx, MovementTraverse current, MovementAscend next, IMovement nextnext) {
        if (!Agent.settings().sprintAscends.value) {
            return false;
        }
        if (!current.getDirection().equals(next.getDirection().below())) {
            return false;
        }
        if (nextnext.getDirection().getX() != next.getDirection().getX()
                || nextnext.getDirection().getZ() != next.getDirection().getZ()) {
            return false;
        }
        if (!MovementHelper.canWalkOn(ctx, current.getDest().below())) {
            return false;
        }
        if (!MovementHelper.canWalkOn(ctx, next.getDest().below())) {
            return false;
        }
        if (!next.toBreakCached.isEmpty()) {
            return false; // it's breaking
        }
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 3; y++) {
                BlockPos chk = current.getSrc().above(y);
                if (x == 1) {
                    chk = chk.offset(current.getDirection());
                }
                if (!MovementHelper.fullyPassable(ctx, chk)) {
                    return false;
                }
            }
        }
        if (MovementHelper.avoidWalkingInto(ctx.world().getBlockState(current.getSrc().above(3)))) {
            return false;
        }
        return !MovementHelper.avoidWalkingInto(
                ctx.world().getBlockState(next.getDest().above(2))); // codacy smh my head
    }

    private static boolean canSprintFromDescendInto(
            IPlayerContext ctx, IMovement current, IMovement next) {
        if (next instanceof MovementDescend && next.getDirection().equals(current.getDirection())) {
            return true;
        }
        if (!MovementHelper.canWalkOn(ctx, current.getDest().offset(current.getDirection()))) {
            return false;
        }
        if (next instanceof MovementTraverse
                && next.getDirection().equals(current.getDirection())) {
            return true;
        }
        return next instanceof MovementDiagonal
                && Agent.settings().allowOvershootDiagonalDescend.value;
    }

    private void onChangeInPathPosition() {
        clearKeys();
        ticksOnCurrent = 0;
        corridor.updateSegment(pathPosition);
        recoveryManager.resetRetryBudget(); // Reset retry budget when moving to next position
    }

    private void clearKeys() {
        // Don't clear keys during swimming transitions - preserve swimming inputs
        Agent agent = (Agent) behavior.maestro;
        if (agent.getSwimmingBehavior().shouldActivateSwimming()) {
            return; // Keep swimming inputs active across movement transitions
        }
        behavior.maestro.getInputOverrideHandler().clearAllKeys();
    }

    private void stopMovement() {
        // Deactivate swimming mode if active, then clear all inputs
        // Used when path execution stops (pause, cancel, goal reached, etc.)
        Agent agent = (Agent) behavior.maestro;
        if (agent.getSwimmingBehavior().shouldActivateSwimming()) {
            agent.getSwimmingBehavior().deactivateSwimming();
        }
        behavior.maestro.getInputOverrideHandler().clearAllKeys();
    }

    /**
     * Replaces the current movement with an alternative.
     *
     * <p>Resets the current movement state and replaces it in the path's movement list. This allows
     * trying alternative movements (e.g., walking instead of teleporting) without recalculating the
     * entire path.
     *
     * @param newMovement the alternative movement to try
     */
    private void replaceCurrentMovement(Movement newMovement) {
        // Reset current movement state
        Movement currentMovement = (Movement) path.movements().get(pathPosition);
        currentMovement.reset();

        // Replace in path's movement list using dedicated IPath method
        path.replaceMovement(pathPosition, newMovement);

        // Update corridor to reflect new movement geometry
        corridor.updateSegment(pathPosition);

        // Reset timing and cost tracking for new movement
        ticksOnCurrent = 0;
        currentMovementOriginalCostEstimate = newMovement.getCost();

        log.atDebug()
                .addKeyValue("position", pathPosition)
                .addKeyValue("new_movement", newMovement.getClass().getSimpleName())
                .addKeyValue("cost", newMovement.getCost())
                .log("Replaced current movement with alternative");
    }

    /**
     * Jumps execution to a reconnection point in the existing path.
     *
     * <p>Updates path position and corridor to resume execution from the reconnection point. This
     * allows recovery from corridor deviation without full recalculation.
     *
     * @param index Index in the path to jump to
     */
    private void jumpToReconnectionPoint(int index) {
        // Reset old movement state if valid
        if (pathPosition >= 0 && pathPosition < path.movements().size()) {
            Movement oldMovement = (Movement) path.movements().get(pathPosition);
            oldMovement.reset();
        }

        // Update position and corridor
        pathPosition = index;
        corridor = new PathCorridor(path, index);
        ticksSinceReconnection = 0;

        // Reset timing and cost tracking for new position
        ticksOnCurrent = 0;
        currentMovementOriginalCostEstimate = null;
        costEstimateIndex = null;

        log.atDebug().addKeyValue("reconnection_index", index).log("Jumped to reconnection point");
    }

    private void cancel() {
        stopMovement();
        behavior.maestro.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
        pathPosition = path.length() + 3;
        failed = true;
    }

    @Override
    public int getPosition() {
        return pathPosition;
    }

    public PathExecutor trySplice(PathExecutor next) {
        if (next == null) {
            return cutIfTooLong();
        }

        // Prevent splicing immediately after reconnection to avoid state corruption
        // When we jump to a reconnection point mid-path, the path's position doesn't align
        // with splicing assumptions (which expect pathPosition = 0)
        if (ticksSinceReconnection >= 0
                && ticksSinceReconnection < RECONNECTION_SPLICE_DELAY_TICKS) {
            log.atDebug()
                    .addKeyValue("ticks_since_reconnection", ticksSinceReconnection)
                    .log("Skipping splice due to recent reconnection");
            return this;
        }

        // When pathPosition > 0, create a CutoffPath to realign indices before splicing
        // This ensures the splice logic works correctly by making the current position
        // the start of the path (index 0)
        IPath currentPath = path;
        int adjustedPathPosition = pathPosition;

        if (pathPosition > 0) {
            // Edge case: if we're at the very end of the path, don't create cutoff
            if (pathPosition >= path.length() - 1) {
                log.atDebug()
                        .addKeyValue("path_position", pathPosition)
                        .addKeyValue("path_length", path.length())
                        .log("Skipping splice - at end of path");
                return cutIfTooLong();
            }

            // Create cutoff path from current position to end
            currentPath = new CutoffPath(path, pathPosition, path.length() - 1);
            adjustedPathPosition = 0; // We're now at the start of the cutoff path

            log.atDebug()
                    .addKeyValue("original_position", pathPosition)
                    .addKeyValue("cutoff_start", pathPosition)
                    .addKeyValue("cutoff_end", path.length() - 1)
                    .addKeyValue("new_length", currentPath.length())
                    .log("Created cutoff path for mid-path splice");
        }

        final int finalAdjustedPosition = adjustedPathPosition;
        return SplicedPath.trySplice(currentPath, next.path, false)
                .map(
                        splicedPath -> {
                            if (!splicedPath.getDest().equals(next.getPath().getDest())) {
                                throw new IllegalStateException(
                                        String.format(
                                                "Path has end %s instead of %s after splicing",
                                                splicedPath.getDest(), next.getPath().getDest()));
                            }
                            PathExecutor ret = new PathExecutor(behavior, splicedPath);
                            ret.pathPosition = finalAdjustedPosition;
                            ret.corridor = new PathCorridor(splicedPath, finalAdjustedPosition);
                            ret.currentMovementOriginalCostEstimate =
                                    currentMovementOriginalCostEstimate;
                            ret.costEstimateIndex = costEstimateIndex;
                            ret.ticksOnCurrent = ticksOnCurrent;

                            log.atDebug()
                                    .addKeyValue("spliced_length", splicedPath.length())
                                    .addKeyValue("position", finalAdjustedPosition)
                                    .log("Successfully spliced paths");

                            return ret;
                        })
                .orElseGet(
                        () -> {
                            log.atDebug().log("Splice failed, falling back to cutIfTooLong");
                            return cutIfTooLong();
                        }); // dont actually call cutIfTooLong every tick if we
        // won't actually use it, use a method reference
    }

    private PathExecutor cutIfTooLong() {
        if (pathPosition > Agent.settings().maxPathHistoryLength.value) {
            int cutoffAmt = Agent.settings().pathHistoryCutoffAmount.value;
            CutoffPath newPath = new CutoffPath(path, cutoffAmt, path.length() - 1);
            if (!newPath.getDest().equals(path.getDest())) {
                throw new IllegalStateException(
                        String.format(
                                "Path has end %s instead of %s after trimming its start",
                                newPath.getDest(), path.getDest()));
            }
            log.atDebug()
                    .addKeyValue("old_length", path.length())
                    .addKeyValue("new_length", newPath.length())
                    .addKeyValue("cutoff_amount", cutoffAmt)
                    .log("Discarding earliest segment movements");
            PathExecutor ret = new PathExecutor(behavior, newPath);
            ret.pathPosition = pathPosition - cutoffAmt;
            ret.corridor = new PathCorridor(newPath, ret.pathPosition);
            ret.currentMovementOriginalCostEstimate = currentMovementOriginalCostEstimate;
            if (costEstimateIndex != null) {
                ret.costEstimateIndex = costEstimateIndex - cutoffAmt;
            }
            ret.ticksOnCurrent = ticksOnCurrent;
            return ret;
        }
        return this;
    }

    @Override
    public IPath getPath() {
        return path;
    }

    public boolean failed() {
        return failed;
    }

    public boolean finished() {
        return pathPosition >= path.length();
    }

    public Set<BlockPos> toBreak() {
        return Collections.unmodifiableSet(toBreak);
    }

    public Set<BlockPos> toPlace() {
        return Collections.unmodifiableSet(toPlace);
    }

    public Set<BlockPos> toWalkInto() {
        return Collections.unmodifiableSet(toWalkInto);
    }

    public boolean isSprinting() {
        return sprintNextTick;
    }
}
