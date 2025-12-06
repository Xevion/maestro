package maestro.pathing.path;

import static maestro.utils.LoggingExtKt.format;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import maestro.Agent;
import maestro.behavior.PathingBehavior;
import maestro.pathing.BlockStateInterface;
import maestro.pathing.calc.AbstractNodeCostSearch;
import maestro.pathing.calc.IPath;
import maestro.pathing.movement.ActionCosts;
import maestro.pathing.movement.IMovement;
import maestro.pathing.movement.Movement;
import maestro.pathing.movement.MovementStatus;
import maestro.pathing.movement.MovementValidation;
// TODO: Re-enable after MovementFall, MovementParkour, MovementDiagonal are converted to Kotlin
// import maestro.pathing.movement.movements.MovementFall;
// import maestro.pathing.movement.movements.MovementParkour;
// import maestro.pathing.movement.movements.MovementDiagonal;
import maestro.pathing.movement.movements.*;
import maestro.pathing.recovery.FailureReason;
import maestro.pathing.recovery.PathRecoveryManager;
import maestro.pathing.recovery.RecoveryAction;
import maestro.player.PlayerContext;
import maestro.utils.BlockPosExtKt;
import maestro.utils.Helper;
import maestro.utils.Loggers;
import maestro.utils.PackedBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import org.slf4j.Logger;

/** Behavior to execute a precomputed path */
public class PathExecutor implements IPathExecutor, Helper {
    private static final Logger log = Loggers.Path.get();

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

    @SuppressWarnings(
            "NonAtomicVolatileUpdate") // Single-writer (game thread), multiple-reader pattern
    private volatile int pathPosition;

    private int ticksAway;

    @SuppressWarnings(
            "NonAtomicVolatileUpdate") // Single-writer (game thread), multiple-reader pattern
    private volatile int ticksOnCurrent;

    private Double currentMovementOriginalCostEstimate;
    private Integer costEstimateIndex;
    private boolean failed;
    private boolean recalcBP = true;
    private final Set<BlockPos> toBreak = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> toPlace = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> toWalkInto = ConcurrentHashMap.newKeySet();

    // Reusable collections for recalculation to avoid allocations
    private final Set<BlockPos> recalcBreak = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> recalcPlace = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> recalcWalkInto = ConcurrentHashMap.newKeySet();
    private int maxCollectionSize = 0; // Track high water mark for shrinking

    private final PathingBehavior behavior;
    private final PlayerContext ctx;

    private final PathRecoveryManager recoveryManager;

    // Prevent splicing immediately after reconnection to avoid state corruption
    private int ticksSinceReconnection = -1; // -1 means no recent reconnection
    private static final int RECONNECTION_SPLICE_DELAY_TICKS = 4;

    // Prevent splicing immediately after movement replacement to avoid path structure corruption
    private int ticksSinceMovementReplacement = -1; // -1 means no recent replacement
    private static final int MOVEMENT_REPLACEMENT_SPLICE_DELAY_TICKS = 4;

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
    @SuppressWarnings(
            "NonAtomicVolatileUpdate") // Single-writer (game thread), multiple-reader pattern
    public boolean onTick() {
        if (pathPosition == path.length() - 1) {
            pathPosition++;
        }
        if (pathPosition >= path.length()) {
            ticksSinceReconnection = -1; // Clear for next path
            ticksSinceMovementReplacement = -1; // Clear for next path
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

        // Increment movement replacement counter to allow splicing after delay
        if (ticksSinceMovementReplacement >= 0) {
            if (ticksSinceMovementReplacement < MOVEMENT_REPLACEMENT_SPLICE_DELAY_TICKS) {
                ticksSinceMovementReplacement++;
            } else if (ticksSinceMovementReplacement == MOVEMENT_REPLACEMENT_SPLICE_DELAY_TICKS) {
                ticksSinceMovementReplacement = -1; // Reset after delay expires
            }
        }

        Movement movement = (Movement) path.movements().get(pathPosition);
        PackedBlockPos whereAmI = ctx.playerFeet();
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
                    return false;
                }
            }
            for (int i = pathPosition + 3;
                    i < path.length() - 1;
                    i++) { // don't check pathPosition+1. the movement tells us when it's done (e.g.
                // sneak placing)
                // also don't check pathPosition+2 because reasons
                if (((Movement) path.movements().get(i)).getValidPositions().contains(whereAmI)) {
                    if (i - pathPosition > 2) {
                        log.atDebug()
                                .addKeyValue("skip_steps", i - pathPosition)
                                .addKeyValue("new_position", i)
                                .log("Skipping forward in path");
                    }
                    pathPosition = i - 1;
                    onChangeInPathPosition();
                    return false;
                }
            }
        }
        if (!corridor.isWithinCorridor(whereAmI)) {
            double distToPath = corridor.distanceToPath(whereAmI);

            if (distToPath > MAX_MAX_DIST_FROM_PATH) {
                log.atDebug()
                        .addKeyValue("distance", format(distToPath))
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
                        RecoveryAction action =
                                recoveryManager.handleCorridorDeviation(
                                        whereAmI,
                                        path,
                                        corridor.currentSegment(),
                                        behavior.secretInternalGetCalculationContext());

                        if (action instanceof RecoveryAction.Reconnect) {
                            jumpToReconnectionPoint(
                                    ((RecoveryAction.Reconnect) action).getPathIndex());
                            ticksAway = 0;
                            log.atInfo()
                                    .addKeyValue("distance", format(distToPath))
                                    .addKeyValue("ticks_away", ticksAway)
                                    .log("Successfully reconnected to path");
                            return false;
                        } else if (action instanceof RecoveryAction.Cancel) {
                            // Fall through to cancel below
                        } else if (action instanceof RecoveryAction.Continue) {
                            // Recovery wants us to keep trying - don't cancel yet
                            return false;
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
        BlockStateInterface bsi = behavior.agent.bsi;
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
            recalcBreak.clear();
            recalcPlace.clear();
            recalcWalkInto.clear();

            for (int i = pathPosition; i < path.movements().size(); i++) {
                Movement m = (Movement) path.movements().get(i);
                recalcBreak.addAll(m.toBreak(bsi));
                recalcPlace.addAll(m.toPlace(bsi));
                recalcWalkInto.addAll(m.toWalkInto(bsi));
            }

            // Swap contents atomically (thread-safe with concurrent sets)
            toBreak.clear();
            toBreak.addAll(recalcBreak);
            toPlace.clear();
            toPlace.addAll(recalcPlace);
            toWalkInto.clear();
            toWalkInto.addAll(recalcWalkInto);
            recalcBP = false;

            // Track collection sizes for periodic shrinking
            int totalSize = recalcBreak.size() + recalcPlace.size() + recalcWalkInto.size();
            maxCollectionSize = Math.max(maxCollectionSize, totalSize);
        }
        if (pathPosition < path.movements().size() - 1) {
            IMovement next = path.movements().get(pathPosition + 1);
            if (!behavior.agent.bsi.worldContainsLoadedChunk(
                    next.getDest().getX(), next.getDest().getZ())) {
                log.atDebug()
                        .addKeyValue(
                                "dest",
                                format(
                                        new PackedBlockPos(
                                                next.getDest().getX(), 0, next.getDest().getZ())))
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
                    i < Agent.getPrimaryAgent().getSettings().costVerificationLookahead.value
                            && pathPosition + i < path.length() - 1;
                    i++) {
                Movement futureMove = (Movement) path.movements().get(pathPosition + i);
                double futureCost =
                        futureMove.calculateCost(behavior.secretInternalGetCalculationContext());
                if (futureCost >= ActionCosts.COST_INF && canCancel) {
                    log.atDebug()
                            .addKeyValue("movement_index", i)
                            .addKeyValue("movement_type", futureMove.getClass().getSimpleName())
                            .addKeyValue("src", format(futureMove.getSrc()))
                            .addKeyValue("dest", format(futureMove.getDest()))
                            .addKeyValue("cost", format(futureCost))
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
                    .addKeyValue("src", format(movement.getSrc()))
                    .addKeyValue("dest", format(movement.getDest()))
                    .addKeyValue("current_cost", format(currentCost))
                    .addKeyValue("original_cost", format(currentMovementOriginalCostEstimate))
                    .log("Current movement became impossible, cancelling path");
            behavior.failureMemory.recordFailure(movement, FailureReason.WORLD_CHANGED);
            cancel();
            return true;
        }
        if (!movement.calculatedWhileLoaded()
                && currentCost - currentMovementOriginalCostEstimate
                        > Agent.getPrimaryAgent().getSettings().maxCostIncrease.value
                && canCancel) {
            // don't do this if the movement was calculated while loaded
            // that means that this isn't a cache error, it's just part of the path interfering with
            // a later part
            log.atDebug()
                    .addKeyValue("original_cost", format(currentMovementOriginalCostEstimate))
                    .addKeyValue("current_cost", format(currentCost))
                    .addKeyValue(
                            "cost_increase",
                            format(currentCost - currentMovementOriginalCostEstimate))
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
        if (movementStatus == MovementStatus.UNREACHABLE
                || movementStatus == MovementStatus.FAILED) {
            log.atDebug().addKeyValue("status", movementStatus).log("Movement failed");
            FailureReason reason =
                    movementStatus == MovementStatus.UNREACHABLE
                            ? FailureReason.UNREACHABLE
                            : FailureReason.BLOCKED;
            behavior.failureMemory.recordFailure(movement, reason);

            // Try recovery via alternative movements
            RecoveryAction action =
                    recoveryManager.handleMovementFailure(
                            movement, ctx.playerFeet(), path, pathPosition, movementStatus, reason);

            if (action instanceof RecoveryAction.Retry) {
                replaceCurrentMovement(((RecoveryAction.Retry) action).getMovement());
                return false; // Don't cancel - continue with alternative
            } else if (action instanceof RecoveryAction.Cancel) {
                cancel();
                return true;
            } else if (action instanceof RecoveryAction.Continue) {
                return false; // Keep trying current movement
            } else {
                throw new IllegalStateException("Unknown recovery type: " + action.getClass());
            }
        }
        if (movementStatus == MovementStatus.SUCCESS) {
            pathPosition++;
            onChangeInPathPosition();
            return true;
        } else {
            ticksOnCurrent++;
            if (ticksOnCurrent
                    > currentMovementOriginalCostEstimate
                            + Agent.getPrimaryAgent().getSettings().movementTimeoutTicks.value) {
                // only cancel if the total time has exceeded the initial estimate
                // as you break the blocks required, the remaining cost goes down, to the point
                // where
                // ticksOnCurrent is greater than recalculateCost + 100
                // this is why we cache cost at the beginning, and don't recalculate for this
                // comparison every tick
                log.atDebug()
                        .addKeyValue("ticks_taken", ticksOnCurrent)
                        .addKeyValue("expected_ticks", format(currentMovementOriginalCostEstimate))
                        .addKeyValue(
                                "timeout",
                                Agent.getPrimaryAgent().getSettings().movementTimeoutTicks.value)
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
            for (PackedBlockPos pos : ((Movement) movement).getValidPositions()) {
                double dist = BlockPosExtKt.distanceTo(ctx.player(), pos.toBlockPos());
                if (dist < best || best == -1) {
                    best = dist;
                    bestPos = pos.toBlockPos();
                }
            }
        }
        return new Tuple<>(best, bestPos);
    }

    private boolean shouldPause() {
        Optional<? extends AbstractNodeCostSearch> current = behavior.getInProgress();
        if (current.isEmpty()) {
            return false;
        }
        if (!ctx.player().onGround()) {
            return false;
        }
        if (!MovementValidation.canWalkOn(ctx, ctx.playerFeet().below())) {
            // we're in some kind of sketchy situation, maybe parkouring
            return false;
        }
        if (!MovementValidation.canWalkThrough(ctx, ctx.playerFeet())
                || !MovementValidation.canWalkThrough(ctx, ctx.playerFeet().above())) {
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
        List<PackedBlockPos> positions = currentBest.get().positions();
        if (positions.size() < 3) {
            return false; // not long enough yet to justify pausing, it's far from certain we'll
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
            // TODO: Re-enable after MovementFall is converted to Kotlin
            // if (path.movements().get(pathPosition) instanceof MovementFall) {
            //     PackedBlockPos fallDest =
            //             path.positions()
            //                     .get(
            //                             pathPosition
            //                                     + 1); // .get(pathPosition) is the block we fell
            // off
            //     // of
            //     return VecUtils.entityFlatDistanceToCenter(ctx.player(), fallDest.toBlockPos())
            //             >= leniency; // ignore Y by using flat distance
            // } else {
            return true;
            // }
        } else {
            return false;
        }
    }

    /**
     * Regardless of current path position, snap to the current player feet if possible
     *
     * @return Whether it was possible to snap to the current player feet
     */
    public boolean snipsnapifpossible() {
        if (!ctx.player().onGround()
                && ctx.world().getFluidState(ctx.playerFeet().toBlockPos()).isEmpty()) {
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

    // TODO: Re-enable after MovementFall is converted to Kotlin
    // private Tuple<Vec3, BlockPos> overrideFall(MovementFall movement) {
    //     Vec3i dir = movement.getDirection();
    //     if (dir.getY() < -3) {
    //         return null;
    //     }
    //     if (!movement.toBreakCached.isEmpty()) {
    //         return null; // it's breaking
    //     }
    //     Vec3i flatDir = new Vec3i(dir.getX(), 0, dir.getZ());
    //     int i;
    //     outer:
    //     for (i = pathPosition + 1; i < path.length() - 1 && i < pathPosition + 3; i++) {
    //         IMovement next = path.movements().get(i);
    //         if (!(next instanceof MovementTraverse)) {
    //             break;
    //         }
    //         if (!flatDir.equals(next.getDirection())) {
    //             break;
    //         }
    //         for (int y = next.getDest().getY(); y <= movement.getSrc().getY() + 1; y++) {
    //             BlockPos chk = new BlockPos(next.getDest().getX(), y, next.getDest().getZ());
    //             if (!MovementValidation.fullyPassable(ctx, chk)) {
    //                 break outer;
    //             }
    //         }
    //         if (!MovementValidation.canWalkOn(ctx, next.getDest().below().toBlockPos())) {
    //             break;
    //         }
    //     }
    //     i--;
    //     if (i == pathPosition) {
    //         return null; // no valid extension exists
    //     }
    //     double len = i - pathPosition - 0.4;
    //     return new Tuple<>(
    //             new Vec3(
    //                     flatDir.getX() * len + movement.getDest().getX() + 0.5,
    //                     movement.getDest().getY(),
    //                     flatDir.getZ() * len + movement.getDest().getZ() + 0.5),
    //             movement.getDest()
    //                     .toBlockPos()
    //                     .offset(
    //                             flatDir.getX() * (i - pathPosition),
    //                             0,
    //                             flatDir.getZ() * (i - pathPosition)));
    // }

    private static boolean skipNow(PlayerContext ctx, IMovement current) {
        var srcCenter = BlockPosExtKt.getCenterXZ(current.getSrc().toBlockPos());
        double offTarget =
                Math.abs(current.getDirection().getX() * (srcCenter.y - ctx.player().position().z))
                        + Math.abs(
                                current.getDirection().getZ()
                                        * (srcCenter.x - ctx.player().position().x));
        if (offTarget > 0.1) {
            return false;
        }
        // we are centered
        BlockPos headBonk = current.getSrc().toBlockPos().subtract(current.getDirection()).above(2);
        if (MovementValidation.fullyPassable(ctx, headBonk)) {
            return true;
        }
        // wait 0.3
        var headBonkCenter = BlockPosExtKt.getCenterXZ(headBonk);
        double flatDist =
                Math.abs(
                                current.getDirection().getX()
                                        * (headBonkCenter.y - ctx.player().position().z))
                        + Math.abs(
                                current.getDirection().getZ()
                                        * (headBonkCenter.x - ctx.player().position().x));
        return flatDist > 0.8;
    }

    private static boolean sprintableAscend(
            PlayerContext ctx, MovementTraverse current, MovementAscend next, IMovement nextnext) {
        if (!Agent.getPrimaryAgent().getSettings().sprintAscends.value) {
            return false;
        }
        if (!current.getDirection().equals(next.getDirection().below())) {
            return false;
        }
        if (nextnext.getDirection().getX() != next.getDirection().getX()
                || nextnext.getDirection().getZ() != next.getDirection().getZ()) {
            return false;
        }
        if (!MovementValidation.canWalkOn(ctx, current.getDest().below().toBlockPos())) {
            return false;
        }
        if (!MovementValidation.canWalkOn(ctx, next.getDest().below().toBlockPos())) {
            return false;
        }
        if (!next.toBreakCached.isEmpty()) {
            return false; // it's breaking
        }
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 3; y++) {
                BlockPos chk = current.getSrc().toBlockPos().above(y);
                if (x == 1) {
                    chk = chk.offset(current.getDirection());
                }
                if (!MovementValidation.fullyPassable(ctx, chk)) {
                    return false;
                }
            }
        }
        if (MovementValidation.avoidWalkingInto(
                ctx.world().getBlockState(current.getSrc().toBlockPos().above(3)))) {
            return false;
        }
        return !MovementValidation.avoidWalkingInto(
                ctx.world()
                        .getBlockState(next.getDest().toBlockPos().above(2))); // codacy smh my head
    }

    private void onChangeInPathPosition() {
        clearKeys();
        ticksOnCurrent = 0;
        corridor.updateSegment(pathPosition);
        recoveryManager.resetRetryBudget(); // Reset retry budget when moving to next position

        // Shrink collections if they're significantly oversized (> 3x current needs)
        int currentNeeds = toBreak.size() + toPlace.size() + toWalkInto.size();
        if (maxCollectionSize > currentNeeds * 3 && maxCollectionSize > 64) {
            log.atDebug()
                    .addKeyValue("previous_max_size", maxCollectionSize)
                    .addKeyValue("current_needs", currentNeeds)
                    .addKeyValue(
                            "shrink_ratio",
                            String.format(
                                    "%.1fx",
                                    (double) maxCollectionSize / Math.max(1, currentNeeds)))
                    .log("Shrinking recalc collections");
            recalcBreak.clear();
            recalcPlace.clear();
            recalcWalkInto.clear();
            maxCollectionSize = 0;
        }
    }

    private void clearKeys() {
        // Clear only movement keys (WASD, JUMP, SNEAK) owned by movements
        // Preserve interaction keys (CLICK_LEFT/RIGHT) owned by processes
        // Sprint is managed directly by SwimmingBehavior, not via inputs
        behavior.agent.getInputOverrideHandler().clearMovementKeys();
    }

    private void stopMovement() {
        // Deactivate swimming mode if active, then clear movement inputs
        // Used when path execution stops (pause, cancel, goal reached, etc.)
        Agent agent = behavior.agent;
        if (agent.isSwimmingActive()) {
            agent.getSwimmingBehavior().deactivateSwimming();
        }
        behavior.agent.getInputOverrideHandler().clearMovementKeys();
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

        // Prevent splicing until replaced movement completes. Splicing with a recently-replaced
        // movement can cause path structure corruption due to interaction with CutoffPath overlap
        // detection.
        ticksSinceMovementReplacement = 0;

        log.atDebug()
                .addKeyValue("position", pathPosition)
                .addKeyValue("new_movement", newMovement.getClass().getSimpleName())
                .addKeyValue("cost", format(newMovement.getCost()))
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
        behavior.agent.getInputOverrideHandler().getBlockBreakManager().stop();
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

        // Prevent splicing immediately after movement replacement to avoid path structure
        // corruption. When a movement is replaced, the path structure may become temporarily
        // inconsistent until the replaced movement either completes or is no longer accessed.
        if (ticksSinceMovementReplacement >= 0
                && ticksSinceMovementReplacement < MOVEMENT_REPLACEMENT_SPLICE_DELAY_TICKS) {
            log.atDebug()
                    .addKeyValue("ticks_since_replacement", ticksSinceMovementReplacement)
                    .log("Skipping splice due to recent movement replacement");
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
        SplicedPath splicedPath = SplicedPath.trySplice(currentPath, next.path, false);
        if (splicedPath != null) {
            if (!splicedPath.getDest().equals(next.getPath().getDest())) {
                throw new IllegalStateException(
                        String.format(
                                "Path has end %s instead of %s after splicing",
                                splicedPath.getDest(), next.getPath().getDest()));
            }
            PathExecutor ret = new PathExecutor(behavior, splicedPath);
            ret.pathPosition = finalAdjustedPosition;
            ret.corridor = new PathCorridor(splicedPath, finalAdjustedPosition);
            ret.currentMovementOriginalCostEstimate = currentMovementOriginalCostEstimate;
            ret.costEstimateIndex = costEstimateIndex;
            ret.ticksOnCurrent = ticksOnCurrent;

            log.atDebug()
                    .addKeyValue("spliced_length", splicedPath.length())
                    .addKeyValue("position", finalAdjustedPosition)
                    .log("Successfully spliced paths");

            return ret;
        } else {
            log.atDebug().log("Splice failed, falling back to cutIfTooLong");
            return cutIfTooLong();
        }
    }

    private PathExecutor cutIfTooLong() {
        if (pathPosition > Agent.getPrimaryAgent().getSettings().maxPathHistoryLength.value) {
            int cutoffAmt = Agent.getPrimaryAgent().getSettings().pathHistoryCutoffAmount.value;
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
}
