package maestro.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import maestro.Agent;
import maestro.api.behavior.IPathingBehavior;
import maestro.api.event.events.*;
import maestro.api.pathing.calc.IPath;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.goals.GoalXZ;
import maestro.api.process.PathingCommand;
import maestro.api.utils.BetterBlockPos;
import maestro.api.utils.Helper;
import maestro.api.utils.PathCalculationResult;
import maestro.api.utils.interfaces.IGoalRenderPos;
import maestro.pathing.calc.AStarPathFinder;
import maestro.pathing.calc.AbstractNodeCostSearch;
import maestro.pathing.movement.CalculationContext;
import maestro.pathing.movement.CompositeMovementProvider;
import maestro.pathing.movement.ContinuousSwimmingProvider;
import maestro.pathing.movement.EnumMovementProvider;
import maestro.pathing.movement.IMovementProvider;
import maestro.pathing.movement.MovementHelper;
import maestro.pathing.path.PathExecutor;
import maestro.utils.PathRenderer;
import maestro.utils.PathingCommandContext;
import maestro.utils.pathing.Favoring;
import net.minecraft.core.BlockPos;

public final class PathingBehavior extends Behavior implements IPathingBehavior, Helper {

    private PathExecutor current;
    private PathExecutor next;

    private Goal goal;
    private CalculationContext context;

    /** Movement provider for pathfinding. Configured based on enableDynamicSwimming setting. */
    private final IMovementProvider movementProvider;

    /*eta*/
    private int ticksElapsedSoFar;
    private BetterBlockPos startPosition;

    private boolean safeToCancel;
    private boolean pauseRequestedLastTick;
    private boolean unpausedLastTick;
    private boolean pausedThisTick;
    private boolean cancelRequested;
    private boolean calcFailedLastTick;

    private volatile AbstractNodeCostSearch inProgress;
    private final Object pathCalcLock = new Object();

    private final Object pathPlanLock = new Object();

    private boolean lastAutoJump;

    private BetterBlockPos expectedSegmentStart;

    private final LinkedBlockingQueue<PathEvent> toDispatch = new LinkedBlockingQueue<>();

    public PathingBehavior(Agent maestro) {
        super(maestro);
        this.movementProvider = createMovementProvider();
    }

    /**
     * Creates movement provider based on enableDynamicSwimming feature flag. When flag is off, uses
     * enum-based movements for backward compatibility. When flag is on, combines dynamic swimming
     * with enum-based terrestrial movements.
     *
     * @return Configured movement provider
     */
    private IMovementProvider createMovementProvider() {
        if (Agent.settings().enableDynamicSwimming.value) {
            // Use dynamic swimming + basic movements
            return new CompositeMovementProvider(
                    new ContinuousSwimmingProvider(), new EnumMovementProvider());
        } else {
            // Use enum only (backward compatible)
            return new EnumMovementProvider();
        }
    }

    private void queuePathEvent(PathEvent event) {
        toDispatch.add(event);
    }

    private void dispatchEvents() {
        ArrayList<PathEvent> curr = new ArrayList<>();
        toDispatch.drainTo(curr);
        calcFailedLastTick = curr.contains(PathEvent.CALC_FAILED);
        for (PathEvent event : curr) {
            maestro.getGameEventHandler().onPathEvent(event);
        }
    }

    @Override
    public void onTick(TickEvent event) {
        dispatchEvents();
        if (event.getType() == TickEvent.Type.OUT) {
            secretInternalSegmentCancel();
            maestro.getPathingControlManager().cancelEverything();
            return;
        }

        expectedSegmentStart = pathStart();
        maestro.getPathingControlManager().preTick();
        tickPath();
        ticksElapsedSoFar++;
        dispatchEvents();
    }

    @Override
    public void onPlayerSprintState(SprintStateEvent event) {
        if (isPathing()) {
            event.setState(current.isSprinting());
        }
    }

    private void tickPath() {
        pausedThisTick = false;
        if (pauseRequestedLastTick && safeToCancel) {
            pauseRequestedLastTick = false;
            if (unpausedLastTick) {
                maestro.getInputOverrideHandler().clearAllKeys();
                maestro.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
                // Deactivate swimming mode when pausing
                ((Agent) maestro).getSwimmingBehavior().deactivateSwimming();
            }
            unpausedLastTick = false;
            pausedThisTick = true;
            return;
        }
        unpausedLastTick = true;
        if (cancelRequested) {
            cancelRequested = false;
            maestro.getInputOverrideHandler().clearAllKeys();
        }
        synchronized (pathPlanLock) {
            synchronized (pathCalcLock) {
                if (inProgress != null) {
                    // we are calculating
                    // are we calculating the right thing though? 🤔
                    BetterBlockPos calcFrom = inProgress.getStart();
                    Optional<IPath> currentBest = inProgress.bestPathSoFar();
                    if ((current == null
                                    || !current.getPath()
                                            .getDest()
                                            .equals(calcFrom)) // if current ends in inProgress's
                            // start, then we're ok
                            && !calcFrom.equals(ctx.playerFeet())
                            && !calcFrom.equals(
                                    expectedSegmentStart) // if current starts in our playerFeet or
                            // pathStart, then we're ok
                            && (currentBest.isEmpty()
                                    || (!currentBest.get().positions().contains(ctx.playerFeet())
                                            && !currentBest
                                                    .get()
                                                    .positions()
                                                    .contains(expectedSegmentStart))) // if
                    ) {
                        // when it was *just* started, currentBest will be empty so we need to also
                        // check calcFrom since that's always present
                        inProgress.cancel(); // cancellation doesn't dispatch any events
                    }
                }
            }
            if (current == null) {
                return;
            }
            safeToCancel = current.onTick();
            if (current.failed() || current.finished()) {
                current = null;
                if (goal == null || goal.isInGoal(ctx.playerFeet())) {
                    logDebug("All done. At " + goal);
                    queuePathEvent(PathEvent.AT_GOAL);
                    next = null;
                    // Deactivate swimming mode when goal reached
                    ((Agent) maestro).getSwimmingBehavior().deactivateSwimming();
                    if (Agent.settings().disconnectOnArrival.value) {
                        ctx.world().disconnect();
                    }
                    return;
                }
                if (next != null
                        && !next.getPath().positions().contains(ctx.playerFeet())
                        && !next.getPath()
                                .positions()
                                .contains(expectedSegmentStart)) { // can contain either one
                    // if the current path failed, we may not actually be on the next one, so make
                    // sure
                    logDebug("Discarding next path as it does not contain current position");
                    // for example if we had a nicely planned ahead path that starts where current
                    // ends
                    // that's all fine and good
                    // but if we fail in the middle of current
                    // we're nowhere close to our planned ahead path
                    // so need to discard it sadly.
                    queuePathEvent(PathEvent.DISCARD_NEXT);
                    next = null;
                }
                if (next != null) {
                    logDebug("Continuing on to planned next path");
                    queuePathEvent(PathEvent.CONTINUING_ONTO_PLANNED_NEXT);
                    current = next;
                    next = null;
                    current.onTick(); // don't waste a tick doing nothing, get started right away
                    return;
                }
                // at this point, current just ended, but we aren't in the goal and have no plan for
                // the future
                synchronized (pathCalcLock) {
                    if (inProgress != null) {
                        queuePathEvent(PathEvent.PATH_FINISHED_NEXT_STILL_CALCULATING);
                        return;
                    }
                    // we aren't calculating
                    queuePathEvent(PathEvent.CALC_STARTED);
                    findPathInNewThread(expectedSegmentStart, true, context);
                }
                return;
            }
            // at this point, we know current is in progress
            if (safeToCancel && next != null && next.snipsnapifpossible()) {
                // a movement just ended; jump directly onto the next path
                logDebug("Splicing into planned next path early...");
                queuePathEvent(PathEvent.SPLICING_ONTO_NEXT_EARLY);
                current = next;
                next = null;
                current.onTick();
                return;
            }
            if (Agent.settings().splicePath.value) {
                current = current.trySplice(next);
            }
            if (next != null && current.getPath().getDest().equals(next.getPath().getDest())) {
                next = null;
            }
            synchronized (pathCalcLock) {
                if (inProgress != null) {
                    // if we aren't calculating right now
                    return;
                }
                if (next != null) {
                    // and we have no plan for what to do next
                    return;
                }
                if (goal == null || goal.isInGoal(current.getPath().getDest())) {
                    // and this path doesn't get us all the way there
                    return;
                }
                if (ticksRemainingInSegment(false).get()
                        < Agent.settings().planningTickLookahead.value) {
                    // and this path has 7.5 seconds or less left
                    // don't include the current movement so a very long last movement (e.g.
                    // descend) doesn't trip it up
                    // if we actually included current, it wouldn't start planning ahead until the
                    // last movement was done, if the last movement took more than 7.5 seconds on
                    // its own
                    logDebug("Path almost over. Planning ahead...");
                    queuePathEvent(PathEvent.NEXT_SEGMENT_CALC_STARTED);
                    findPathInNewThread(current.getPath().getDest(), false, context);
                }
            }
        }
    }

    @Override
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (current != null) {
            switch (event.getState()) {
                case PRE:
                    lastAutoJump = ctx.minecraft().options.autoJump().get();
                    ctx.minecraft().options.autoJump().set(false);
                    break;
                case POST:
                    ctx.minecraft().options.autoJump().set(lastAutoJump);
                    break;
                default:
                    break;
            }
        }
    }

    public void secretInternalSetGoal(Goal goal) {
        this.goal = goal;
    }

    public boolean secretInternalSetGoalAndPath(PathingCommand command) {
        secretInternalSetGoal(command.goal);
        if (command instanceof PathingCommandContext) {
            context = ((PathingCommandContext) command).desiredCalcContext;
        } else {
            context = new CalculationContext(maestro, true);
        }
        if (goal == null) {
            return false;
        }
        if (goal.isInGoal(ctx.playerFeet())) {
            return false;
        }
        synchronized (pathPlanLock) {
            if (current != null) {
                return false;
            }
            synchronized (pathCalcLock) {
                if (inProgress != null) {
                    return false;
                }
                queuePathEvent(PathEvent.CALC_STARTED);
                findPathInNewThread(expectedSegmentStart, true, context);
                return true;
            }
        }
    }

    @Override
    public Goal getGoal() {
        return goal;
    }

    @Override
    public boolean isPathing() {
        return hasPath() && !pausedThisTick;
    }

    @Override
    public PathExecutor getCurrent() {
        return current;
    }

    @Override
    public PathExecutor getNext() {
        return next;
    }

    @Override
    public Optional<AbstractNodeCostSearch> getInProgress() {
        return Optional.ofNullable(inProgress);
    }

    public boolean isSafeToCancel() {
        if (current == null) {
            return !maestro.getElytraProcess().isActive()
                    || maestro.getElytraProcess().isSafeToCancel();
        }
        return safeToCancel;
    }

    public void requestPause() {
        pauseRequestedLastTick = true;
    }

    public boolean cancelSegmentIfSafe() {
        if (isSafeToCancel()) {
            secretInternalSegmentCancel();
            return true;
        }
        return false;
    }

    @Override
    public boolean cancelEverything() {
        boolean doIt = isSafeToCancel();
        if (doIt) {
            secretInternalSegmentCancel();
        } else {
            // Restore user control even when pathing can't be safely cancelled to prevent stuck
            // camera/input states
            maestro.getInputOverrideHandler().clearAllKeys();
            maestro.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
            ((Agent) maestro).getSwimmingBehavior().deactivateSwimming();
        }
        maestro.getPathingControlManager()
                .cancelEverything(); // regardless of if we can stop the current segment, we can
        // still stop the processes
        return doIt;
    }

    public boolean calcFailedLastTick() { // NOT exposed on public api
        return calcFailedLastTick;
    }

    public void softCancelIfSafe() {
        synchronized (pathPlanLock) {
            getInProgress().ifPresent(AbstractNodeCostSearch::cancel); // only cancel ours
            if (!isSafeToCancel()) {
                // Deactivate swimming to prevent stuck camera state
                ((Agent) maestro).getSwimmingBehavior().deactivateSwimming();
                return;
            }
            current = null;
            next = null;
            // Deactivate swimming to restore camera control
            ((Agent) maestro).getSwimmingBehavior().deactivateSwimming();
        }
        cancelRequested = true;
        // do everything BUT clear keys
    }

    // just cancel the current path
    public void secretInternalSegmentCancel() {
        queuePathEvent(PathEvent.CANCELED);
        synchronized (pathPlanLock) {
            getInProgress().ifPresent(AbstractNodeCostSearch::cancel);
            if (current != null) {
                current = null;
                next = null;
                maestro.getInputOverrideHandler().clearAllKeys();
                maestro.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
                // Deactivate swimming mode to restore normal camera control
                ((Agent) maestro).getSwimmingBehavior().deactivateSwimming();
            }
        }
    }

    @Override
    public void forceCancel() { // exposed on public api because :sob:
        cancelEverything();
        secretInternalSegmentCancel();
        synchronized (pathCalcLock) {
            inProgress = null;
        }
    }

    public CalculationContext secretInternalGetCalculationContext() {
        return context;
    }

    public Optional<Double> estimatedTicksToGoal() {
        BetterBlockPos currentPos = ctx.playerFeet();
        if (goal == null || currentPos == null || startPosition == null) {
            return Optional.empty();
        }
        if (goal.isInGoal(ctx.playerFeet())) {
            resetEstimatedTicksToGoal();
            return Optional.of(0.0);
        }
        if (ticksElapsedSoFar == 0) {
            return Optional.empty();
        }
        double current = goal.heuristic(currentPos.x, currentPos.y, currentPos.z);
        double start = goal.heuristic(startPosition.x, startPosition.y, startPosition.z);
        if (current == start) { // can't check above because current and start can be equal even if
            // currentPos and startPosition are not
            return Optional.empty();
        }
        double eta =
                Math.abs(current - goal.heuristic())
                        * ticksElapsedSoFar
                        / Math.abs(start - current);
        return Optional.of(eta);
    }

    private void resetEstimatedTicksToGoal() {
        resetEstimatedTicksToGoal(expectedSegmentStart);
    }

    private void resetEstimatedTicksToGoal(BlockPos start) {
        resetEstimatedTicksToGoal(new BetterBlockPos(start));
    }

    private void resetEstimatedTicksToGoal(BetterBlockPos start) {
        ticksElapsedSoFar = 0;
        startPosition = start;
    }

    /**
     * See issue #209
     *
     * @return The starting {@link BlockPos} for a new path
     */
    public BetterBlockPos pathStart() { // TODO move to a helper or util class
        BetterBlockPos feet = ctx.playerFeet();
        if (!MovementHelper.canWalkOn(ctx, feet.below())) {
            if (ctx.player().onGround()) {
                double playerX = ctx.player().position().x;
                double playerZ = ctx.player().position().z;
                ArrayList<BetterBlockPos> closest = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        closest.add(new BetterBlockPos(feet.x + dx, feet.y, feet.z + dz));
                    }
                }
                closest.sort(
                        Comparator.comparingDouble(
                                pos ->
                                        ((pos.x + 0.5D) - playerX) * ((pos.x + 0.5D) - playerX)
                                                + ((pos.z + 0.5D) - playerZ)
                                                        * ((pos.z + 0.5D) - playerZ)));
                for (int i = 0; i < 4; i++) {
                    BetterBlockPos possibleSupport = closest.get(i);
                    double xDist = Math.abs((possibleSupport.x + 0.5D) - playerX);
                    double zDist = Math.abs((possibleSupport.z + 0.5D) - playerZ);
                    if (xDist > 0.8 && zDist > 0.8) {
                        // can't possibly be sneaking off of this one, we're too far away
                        continue;
                    }
                    if (MovementHelper.canWalkOn(ctx, possibleSupport.below())
                            && MovementHelper.canWalkThrough(ctx, possibleSupport)
                            && MovementHelper.canWalkThrough(ctx, possibleSupport.above())) {
                        // this is plausible
                        // logDebug("Faking path start assuming player is standing off the edge of a
                        // block");
                        return possibleSupport;
                    }
                }

            } else {
                // !onGround
                // we're in the middle of a jump
                if (MovementHelper.canWalkOn(ctx, feet.below().below())) {
                    // logDebug("Faking path start assuming player is midair and falling");
                    return feet.below();
                }
            }
        }
        return feet;
    }

    /** In a new thread, pathfind to target blockpos */
    private void findPathInNewThread(
            final BlockPos start, final boolean talkAboutIt, CalculationContext context) {
        // this must be called with synchronization on pathCalcLock!
        // actually, we can check this, muahaha
        if (!Thread.holdsLock(pathCalcLock)) {
            throw new IllegalStateException("Must be called with synchronization on pathCalcLock");
            // why do it this way? it's already indented so much that putting the whole thing in a
            // synchronized(pathCalcLock) was just too much lol
        }
        if (inProgress != null) {
            throw new IllegalStateException(
                    "Already doing it"); // should have been checked by caller
        }
        if (!context.safeForThreadedUse) {
            throw new IllegalStateException("Improper context thread safety level");
        }
        Goal goal = this.goal;
        if (goal == null) {
            logDebug("no goal"); // TODO should this be an exception too? definitely should be
            // checked by caller
            return;
        }
        long primaryTimeout;
        long failureTimeout;
        if (current == null) {
            primaryTimeout = Agent.settings().primaryTimeoutMS.value;
            failureTimeout = Agent.settings().failureTimeoutMS.value;
        } else {
            primaryTimeout = Agent.settings().planAheadPrimaryTimeoutMS.value;
            failureTimeout = Agent.settings().planAheadFailureTimeoutMS.value;
        }
        AbstractNodeCostSearch pathfinder =
                createPathfinder(start, goal, current == null ? null : current.getPath(), context);
        if (!Objects.equals(
                pathfinder.getGoal(),
                goal)) { // will return the exact same object if simplification didn't happen
            logDebug("Simplifying " + goal.getClass() + " to GoalXZ due to distance");
        }
        inProgress = pathfinder;
        Agent.getExecutor()
                .execute(
                        () -> {
                            if (talkAboutIt) {
                                logDebug(
                                        "Starting to search for path from "
                                                + start
                                                + " to "
                                                + goal);
                            }

                            PathCalculationResult calcResult =
                                    pathfinder.calculate(primaryTimeout, failureTimeout);
                            synchronized (pathPlanLock) {
                                Optional<PathExecutor> executor =
                                        calcResult
                                                .getPath()
                                                .map(
                                                        p ->
                                                                new PathExecutor(
                                                                        PathingBehavior.this, p));
                                if (current == null) {
                                    if (executor.isPresent()) {
                                        if (executor.get()
                                                .getPath()
                                                .positions()
                                                .contains(expectedSegmentStart)) {
                                            queuePathEvent(PathEvent.CALC_FINISHED_NOW_EXECUTING);
                                            current = executor.get();
                                            resetEstimatedTicksToGoal(start);
                                        } else {
                                            logDebug(
                                                    "Warning: discarding orphan path segment with"
                                                            + " incorrect start");
                                        }
                                    } else {
                                        if (calcResult.getType()
                                                        != PathCalculationResult.Type.CANCELLATION
                                                && calcResult.getType()
                                                        != PathCalculationResult.Type.EXCEPTION) {
                                            // don't dispatch CALC_FAILED on cancellation
                                            queuePathEvent(PathEvent.CALC_FAILED);
                                        }
                                    }
                                } else {
                                    if (next == null) {
                                        if (executor.isPresent()) {
                                            if (executor.get()
                                                    .getPath()
                                                    .getSrc()
                                                    .equals(current.getPath().getDest())) {
                                                queuePathEvent(
                                                        PathEvent.NEXT_SEGMENT_CALC_FINISHED);
                                                next = executor.get();
                                            } else {
                                                logDebug(
                                                        "Warning: discarding orphan next segment"
                                                                + " with incorrect start");
                                            }
                                        } else {
                                            queuePathEvent(PathEvent.NEXT_CALC_FAILED);
                                        }
                                    } else {
                                        // throw new IllegalStateException("I have no idea what to
                                        // do with this path");
                                        // no point in throwing an exception here, and it gets it
                                        // stuck with inProgress being not null
                                        logDirect(
                                                "Warning: PathingBehaivor illegal state! Discarding"
                                                        + " invalid path!");
                                    }
                                }
                                if (talkAboutIt && current != null && current.getPath() != null) {
                                    if (goal.isInGoal(current.getPath().getDest())) {
                                        logDebug(
                                                "Finished finding a path from "
                                                        + start
                                                        + " to "
                                                        + goal
                                                        + ". "
                                                        + current.getPath().getNumNodesConsidered()
                                                        + " nodes considered");
                                    } else {
                                        logDebug(
                                                "Found path segment from "
                                                        + start
                                                        + " towards "
                                                        + goal
                                                        + ". "
                                                        + current.getPath().getNumNodesConsidered()
                                                        + " nodes considered");
                                    }
                                }
                                synchronized (pathCalcLock) {
                                    inProgress = null;
                                }
                            }
                        });
    }

    private AbstractNodeCostSearch createPathfinder(
            BlockPos start, Goal goal, IPath previous, CalculationContext context) {
        Goal transformed = goal;
        if (Agent.settings().simplifyUnloadedYCoord.value && goal instanceof IGoalRenderPos) {
            BlockPos pos = ((IGoalRenderPos) goal).getGoalPos();
            if (!context.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
                transformed = new GoalXZ(pos.getX(), pos.getZ());
            }
        }
        Favoring favoring =
                new Favoring(context.getMaestro().getPlayerContext(), previous, context);
        BetterBlockPos feet = ctx.playerFeet();
        var realStart = new BetterBlockPos(start);
        var sub = feet.subtract(realStart);
        if (feet.getY() == realStart.getY()
                && Math.abs(sub.getX()) <= 1
                && Math.abs(sub.getZ()) <= 1) {
            realStart = feet;
        }
        return new AStarPathFinder(
                realStart,
                start.getX(),
                start.getY(),
                start.getZ(),
                transformed,
                favoring,
                context,
                movementProvider);
    }

    @Override
    public void onRenderPass(RenderEvent event) {
        PathRenderer.render(event, this);
    }
}
