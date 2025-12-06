package maestro.behavior

import maestro.Agent
import maestro.event.events.PathEvent
import maestro.event.events.PlayerUpdateEvent
import maestro.event.events.RenderEvent
import maestro.event.events.TickEvent
import maestro.event.events.type.EventState
import maestro.pathing.PathingCommandContext
import maestro.pathing.PreferredPaths
import maestro.pathing.calc.AStarPathFinder
import maestro.pathing.calc.AbstractNodeCostSearch
import maestro.pathing.calc.IPath
import maestro.pathing.goals.Goal
import maestro.pathing.goals.GoalXZ
import maestro.pathing.movement.CalculationContext
import maestro.pathing.movement.CompositeMovementProvider
import maestro.pathing.movement.IMovementProvider
import maestro.pathing.movement.MovementValidation
import maestro.pathing.movement.StandardMovementProvider
import maestro.pathing.movement.SwimmingProvider
import maestro.pathing.movement.TeleportMovementProvider
import maestro.pathing.path.PathExecutor
import maestro.pathing.recovery.MovementFailureMemory
import maestro.rendering.IGoalRenderPos
import maestro.rendering.PathRenderer
import maestro.task.PathingCommand
import maestro.utils.Loggers
import maestro.utils.PackedBlockPos
import maestro.utils.PathCalculationResult
import maestro.utils.format
import net.minecraft.core.BlockPos
import org.slf4j.Logger
import java.util.ArrayList
import java.util.Optional
import java.util.concurrent.LinkedBlockingQueue
import java.util.function.Function
import java.util.function.ToDoubleFunction
import kotlin.concurrent.Volatile
import kotlin.math.abs

class PathingBehavior(
    agent: Agent,
) : Behavior(agent),
    IPathingBehavior {
    private var current: PathExecutor? = null
    private var next: PathExecutor? = null

    private var goal: Goal? = null
    private var context: CalculationContext? = null

    /**
     * Movement provider for pathfinding. Combines SwimmingProvider (dynamic 3D swimming)
     * with StandardMovementProvider (terrestrial movements).
     */
    @JvmField
    val movementProvider: IMovementProvider

    /** Tracks recently failed movements to avoid retrying the same failures  */
    @JvmField
    val failureMemory: MovementFailureMemory

    // eta
    private var ticksElapsedSoFar = 0
    private var startPosition: PackedBlockPos? = null

    private var safeToCancel = false
    private var pauseRequestedLastTick = false
    private var unpausedLastTick = false
    private var pausedThisTick = false
    private var cancelRequested = false
    private var calcFailedLastTick = false

    @Volatile
    private var inProgress: AbstractNodeCostSearch? = null
    private val pathCalcLock = Any()

    private val pathPlanLock = Any()

    private var lastAutoJump = false

    private var expectedSegmentStart: PackedBlockPos? = null

    private val toDispatch = LinkedBlockingQueue<PathEvent>()

    init {
        this.movementProvider = createMovementProvider()
        this.failureMemory = MovementFailureMemory(agent)
    }

    /**
     * Creates movement provider combining continuous swimming (dynamic multi-directional with 3D
     * diagonals), enum-based terrestrial movements (walk, parkour, etc.), and teleportation.
     *
     * @return Configured movement provider
     */
    private fun createMovementProvider(): IMovementProvider {
        // Swimming uses SwimmingProvider exclusively (configurable precision)
        // Terrestrial movements use StandardMovementProvider (Walk, Parkour, etc.)
        // Teleportation uses TeleportMovementProvider (packet-based exploit)
        return CompositeMovementProvider(
            SwimmingProvider(),
            StandardMovementProvider(),
            TeleportMovementProvider(),
        )
    }

    private fun queuePathEvent(event: PathEvent?) {
        toDispatch.add(event)
    }

    private fun dispatchEvents() {
        val curr = ArrayList<PathEvent?>()
        toDispatch.drainTo(curr)
        calcFailedLastTick = curr.contains(PathEvent.CALC_FAILED)
        for (event in curr) {
            agent.gameEventHandler.onPathEvent(event)
        }
    }

    override fun onTick(event: TickEvent) {
        dispatchEvents()
        if (event.type == TickEvent.Type.OUT) {
            secretInternalSegmentCancel()
            agent.pathingControlManager.cancelEverything()
            return
        }

        expectedSegmentStart = pathStart()
        agent.pathingControlManager.preTick()
        tickPath()
        ticksElapsedSoFar++

        // Cleanup expired failure records every 5 seconds
        if (ticksElapsedSoFar % 100 == 0) {
            failureMemory.cleanup()
        }

        dispatchEvents()
    }

    private fun tickPath() {
        pausedThisTick = false
        if (pauseRequestedLastTick && safeToCancel) {
            pauseRequestedLastTick = false
            if (unpausedLastTick) {
                // Clear only movement keys - let processes manage interaction keys
                agent.inputOverrideHandler.clearMovementKeys()
                agent.inputOverrideHandler.blockBreakManager.stop()
                // Deactivate swimming mode when pausing
                (agent as Agent).swimmingBehavior.deactivateSwimming()
            }
            unpausedLastTick = false
            pausedThisTick = true
            return
        }
        unpausedLastTick = true
        if (cancelRequested) {
            cancelRequested = false
            // Clear only movement keys - preserve interaction keys (CLICK_LEFT/RIGHT)
            // This prevents mining interruption during path revalidation
            agent.inputOverrideHandler.clearMovementKeys()
        }
        synchronized(pathPlanLock) {
            synchronized(pathCalcLock) {
                if (inProgress != null) {
                    // we are calculating
                    // are we calculating the right thing though? 🤔
                    val calcFrom: PackedBlockPos = inProgress!!.getStart()
                    val currentBest: Optional<IPath> = inProgress!!.bestPathSoFar()
                    if ((
                            current == null ||
                                (
                                    current!!
                                        .path
                                        .dest
                                        != calcFrom
                                )
                        ) &&
                        // if current ends in inProgress's
                        // start, then we're ok
                        (calcFrom != ctx.playerFeet()) &&
                        (calcFrom != expectedSegmentStart) &&
                        (
                            currentBest.isEmpty ||
                                (
                                    !currentBest.get().positions().contains(ctx.playerFeet()) &&
                                        !currentBest
                                            .get()
                                            .positions()
                                            .contains(expectedSegmentStart)
                                )
                        ) // if
                    ) {
                        // when it was *just* started, currentBest will be empty so we need to also
                        // check calcFrom since that's always present
                        inProgress!!.cancel() // cancellation doesn't dispatch any events
                    }
                }
            }
            if (current == null) {
                return
            }
            safeToCancel = (current ?: return@synchronized).onTick()
            if ((current ?: return@synchronized).failed() || (current ?: return@synchronized).finished()) {
                current = null
                if (goal == null || goal!!.isInGoal(ctx.playerFeet().toBlockPos())) {
                    log.atDebug().addKeyValue("goal", goal).log("All done")
                    queuePathEvent(PathEvent.AT_GOAL)
                    next = null
                    // Deactivate swimming mode when goal reached
                    agent.swimmingBehavior.deactivateSwimming()
                    if (Agent
                            .getPrimaryAgent()
                            .settings.disconnectOnArrival.value
                    ) {
                        ctx.world().disconnect()
                    }
                    return
                }
                if (next != null &&
                    !next!!.path.positions().contains(ctx.playerFeet()) &&
                    !next!!
                        .path
                        .positions()
                        .contains(expectedSegmentStart)
                ) { // can contain either one
                    // if the current path failed, we may not actually be on the next one, so make
                    // sure
                    log.atDebug().log("Discarding next path, does not contain current position")
                    // for example if we had a nicely planned ahead path that starts where current
                    // ends
                    // that's all fine and good
                    // but if we fail in the middle of current
                    // we're nowhere close to our planned ahead path
                    // so need to discard it sadly.
                    queuePathEvent(PathEvent.DISCARD_NEXT)
                    next = null
                }
                if (next != null) {
                    log.atDebug().log("Continuing on to planned next path")
                    queuePathEvent(PathEvent.CONTINUING_ONTO_PLANNED_NEXT)
                    current = next
                    next = null
                    current!!.onTick() // don't waste a tick doing nothing, get started right away
                    return
                }
                // at this point, current just ended, but we aren't in the goal and have no plan for
                // the future
                synchronized(pathCalcLock) {
                    if (inProgress != null) {
                        queuePathEvent(PathEvent.PATH_FINISHED_NEXT_STILL_CALCULATING)
                        return
                    }
                    // we aren't calculating
                    queuePathEvent(PathEvent.CALC_STARTED)
                    findPathInNewThread(expectedSegmentStart!!.toBlockPos(), PathfindingReason.INITIAL_PATH, context!!)
                }
                return
            }
            // at this point, we know current is in progress
            if (safeToCancel && next != null && next!!.snipsnapifpossible()) {
                // a movement just ended; jump directly onto the next path
                log.atDebug().log("Splicing into planned next path early")
                queuePathEvent(PathEvent.SPLICING_ONTO_NEXT_EARLY)
                current = next
                next = null
                current!!.onTick()
                return
            }
            if (Agent
                    .getPrimaryAgent()
                    .settings.splicePath.value
            ) {
                current = current!!.trySplice(next)
            }
            if (next != null && current!!.path.dest == next!!.path.dest) {
                next = null
            }
            synchronized(pathCalcLock) {
                if (inProgress != null) {
                    // if we aren't calculating right now
                    return
                }
                if (next != null) {
                    // and we have no plan for what to do next
                    return
                }
                if (goal == null || goal!!.isInGoal(current!!.path.dest.toBlockPos())) {
                    // and this path doesn't get us all the way there
                    return
                }
                if (ticksRemainingInSegment(false).get()
                    <
                    Agent
                        .getPrimaryAgent()
                        .settings.planningTickLookahead.value
                ) {
                    // and this path has 7.5 seconds or less left
                    // don't include the current movement so a very long last movement (e.g.
                    // descend) doesn't trip it up
                    // if we actually included current, it wouldn't start planning ahead until the
                    // last movement was done, if the last movement took more than 7.5 seconds on
                    // its own
                    log.atDebug().log("Path almost over, planning ahead")
                    queuePathEvent(PathEvent.NEXT_SEGMENT_CALC_STARTED)
                    findPathInNewThread(current!!.path.dest.toBlockPos(), PathfindingReason.PLAN_AHEAD, context!!)
                }
            }
        }
    }

    override fun onPlayerUpdate(event: PlayerUpdateEvent) {
        if (current != null) {
            when (event.state) {
                EventState.PRE -> {
                    lastAutoJump =
                        ctx
                            .minecraft()
                            .options
                            .autoJump()
                            .get()
                    ctx
                        .minecraft()
                        .options
                        .autoJump()
                        .set(false)
                }

                EventState.POST ->
                    ctx
                        .minecraft()
                        .options
                        .autoJump()
                        .set(lastAutoJump)
            }
        }
    }

    fun secretInternalSetGoal(goal: Goal?) {
        this.goal = goal
    }

    fun secretInternalSetGoalAndPath(command: PathingCommand): Boolean {
        secretInternalSetGoal(command.goal)
        context =
            if (command is PathingCommandContext) {
                command.desiredCalcContext
            } else {
                CalculationContext(agent, true)
            }
        if (goal == null) {
            return false
        }
        if (goal!!.isInGoal(ctx.playerFeet().toBlockPos())) {
            return false
        }
        synchronized(pathPlanLock) {
            if (current != null) {
                return false
            }
            synchronized(pathCalcLock) {
                if (inProgress != null) {
                    return false
                }
                queuePathEvent(PathEvent.CALC_STARTED)
                findPathInNewThread(expectedSegmentStart!!.toBlockPos(), PathfindingReason.INITIAL_PATH, context!!)
                return true
            }
        }
    }

    override fun getGoal(): Goal? = goal

    override fun isPathing(): Boolean = hasPath() && !pausedThisTick

    override fun getCurrent(): PathExecutor? = current

    override fun getNext(): PathExecutor? = next

    override fun getInProgress(): Optional<out AStarPathFinder?> {
        @Suppress("UNCHECKED_CAST")
        return Optional.ofNullable(inProgress) as Optional<out AStarPathFinder?>
    }

    fun isSafeToCancel(): Boolean {
        if (current == null) {
            return !agent.elytraTask.isActive() ||
                agent.elytraTask.isSafeToCancel()
        }
        return safeToCancel
    }

    fun requestPause() {
        pauseRequestedLastTick = true
    }

    fun cancelSegmentIfSafe(): Boolean {
        if (isSafeToCancel()) {
            secretInternalSegmentCancel()
            return true
        }
        return false
    }

    override fun cancelEverything(): Boolean {
        val doIt = isSafeToCancel()
        if (doIt) {
            secretInternalSegmentCancel()
        } else {
            // Restore user control even when pathing can't be safely cancelled to prevent stuck
            // camera/input states
            agent.inputOverrideHandler.clearAllKeys()
            agent.inputOverrideHandler.blockBreakManager.stop()
            agent.swimmingBehavior.deactivateSwimming()
        }
        agent.pathingControlManager
            .cancelEverything() // regardless of if we can stop the current segment, we can
        // still stop the processes
        return doIt
    }

    fun calcFailedLastTick(): Boolean { // NOT exposed on public api
        return calcFailedLastTick
    }

    fun softCancelIfSafe() {
        synchronized(pathPlanLock) {
            inProgress?.cancel() // only cancel ours
            if (!isSafeToCancel()) {
                // Deactivate swimming to prevent stuck camera state
                agent.swimmingBehavior.deactivateSwimming()
                return
            }
            current = null
            next = null
            // Deactivate swimming to restore camera control
            agent.swimmingBehavior.deactivateSwimming()
        }
        cancelRequested = true
        // do everything BUT clear keys
    }

    // just cancel the current path
    fun secretInternalSegmentCancel() {
        queuePathEvent(PathEvent.CANCELED)
        synchronized(pathPlanLock) {
            inProgress?.cancel()
            if (current != null) {
                current = null
                next = null
                agent.inputOverrideHandler.clearAllKeys()
                agent.inputOverrideHandler.blockBreakManager.stop()
                // Deactivate swimming mode to restore normal camera control
                (agent as Agent).swimmingBehavior.deactivateSwimming()
            }
        }
    }

    override fun forceCancel() { // exposed on public api because :sob:
        cancelEverything()
        secretInternalSegmentCancel()
        synchronized(pathCalcLock) {
            inProgress = null
        }
    }

    fun secretInternalGetCalculationContext(): CalculationContext = context!!

    override fun estimatedTicksToGoal(): Optional<Double?> {
        val currentPos = ctx.playerFeet()
        if (goal == null || currentPos == null || startPosition == null) {
            @Suppress("UNCHECKED_CAST")
            return Optional.empty<Double>() as Optional<Double?>
        }
        if (goal!!.isInGoal(ctx.playerFeet().toBlockPos())) {
            resetEstimatedTicksToGoal()
            @Suppress("UNCHECKED_CAST")
            return Optional.of(0.0) as Optional<Double?>
        }
        if (ticksElapsedSoFar == 0) {
            @Suppress("UNCHECKED_CAST")
            return Optional.empty<Double>() as Optional<Double?>
        }
        val current = goal!!.heuristic(currentPos.x, currentPos.y, currentPos.z)
        val start = goal!!.heuristic(startPosition!!.x, startPosition!!.y, startPosition!!.z)
        if (current == start) { // can't check above because current and start can be equal even if
            // currentPos and startPosition are not
            @Suppress("UNCHECKED_CAST")
            return Optional.empty<Double>() as Optional<Double?>
        }
        val eta =
            (
                abs(current - goal!!.heuristic()) * ticksElapsedSoFar /
                    abs(start - current)
            )
        @Suppress("UNCHECKED_CAST")
        return Optional.of(eta) as Optional<Double?>
    }

    private fun resetEstimatedTicksToGoal(start: PackedBlockPos? = expectedSegmentStart) {
        ticksElapsedSoFar = 0
        startPosition = start
    }

    /**
     * See Baritone issue #209
     *
     * @return The starting [BlockPos] for a new path
     */
    fun pathStart(): PackedBlockPos? { // TODO move to a helper or util class
        val feet = ctx.playerFeet()
        if (!MovementValidation.canWalkOn(ctx, feet.below())) {
            if (ctx.player().onGround()) {
                val playerX = ctx.player().position().x
                val playerZ = ctx.player().position().z
                val closest = ArrayList<PackedBlockPos>()
                for (dx in -1..1) {
                    for (dz in -1..1) {
                        closest.add(PackedBlockPos(feet.x + dx, feet.y, feet.z + dz))
                    }
                }
                closest.sortWith(
                    Comparator.comparingDouble(
                        ToDoubleFunction { pos: PackedBlockPos? ->
                            (
                                ((pos!!.x + 0.5) - playerX) * ((pos.x + 0.5) - playerX) +
                                    ((pos.z + 0.5) - playerZ) *
                                    ((pos.z + 0.5) - playerZ)
                            )
                        },
                    ),
                )
                for (i in 0..3) {
                    val possibleSupport = closest[i]
                    val xDist = abs((possibleSupport.x + 0.5) - playerX)
                    val zDist = abs((possibleSupport.z + 0.5) - playerZ)
                    if (xDist > 0.8 && zDist > 0.8) {
                        // can't possibly be sneaking off of this one, we're too far away
                        continue
                    }
                    if (MovementValidation.canWalkOn(ctx, possibleSupport.below()) &&
                        MovementValidation.canWalkThrough(ctx, possibleSupport) &&
                        MovementValidation.canWalkThrough(ctx, possibleSupport.above())
                    ) {
                        // this is plausible
                        // logDebug("Faking path start assuming player is standing off the edge of a
                        // block");
                        return possibleSupport
                    }
                }
            } else {
                // !onGround
                // we're in the middle of a jump
                if (MovementValidation.canWalkOn(ctx, feet.below().below())) {
                    // logDebug("Faking path start assuming player is midair and falling");
                    return feet.below()
                }
            }
        }
        return feet
    }

    /** In a new thread, pathfind to target blockpos  */
    private fun findPathInNewThread(
        start: BlockPos,
        reason: PathfindingReason,
        context: CalculationContext,
    ) {
        // this must be called with synchronization on pathCalcLock!
        // actually, we can check this, muahaha
        check(Thread.holdsLock(pathCalcLock)) { "Must be called with synchronization on pathCalcLock" }
        check(inProgress == null) { "Already doing it" }
        check(context.safeForThreadedUse) { "Improper context thread safety level" }
        val goal = this.goal
        if (goal == null) {
            log.atDebug().log("No goal set")
            return
        }
        val primaryTimeout: Long
        val failureTimeout: Long
        if (current == null) {
            primaryTimeout =
                Agent
                    .getPrimaryAgent()
                    .settings.primaryTimeoutMS.value
            failureTimeout =
                Agent
                    .getPrimaryAgent()
                    .settings.failureTimeoutMS.value
        } else {
            primaryTimeout =
                Agent
                    .getPrimaryAgent()
                    .settings.planAheadPrimaryTimeoutMS.value
            failureTimeout =
                Agent
                    .getPrimaryAgent()
                    .settings.planAheadFailureTimeoutMS.value
        }
        val pathfinder =
            createPathfinder(start, goal, if (current == null) null else current!!.path, context)
        if (pathfinder.getGoal() != goal) { // will return the exact same object if simplification didn't happen
            log
                .atDebug()
                .addKeyValue("from", goal.javaClass.getSimpleName())
                .log("Simplifying goal to GoalXZ")
        }
        inProgress = pathfinder
        Agent
            .getExecutor()
            .execute {
                val shouldLogStart =
                    when (reason) {
                        PathfindingReason.INITIAL_PATH, PathfindingReason.RECOVERY -> true
                        PathfindingReason.PLAN_AHEAD -> false
                    }

                if (shouldLogStart) {
                    log
                        .atDebug()
                        .addKeyValue("reason", reason.name.lowercase())
                        .addKeyValue("start", PackedBlockPos(start).format())
                        .addKeyValue("goal", goal)
                        .log("Starting path search")
                }
                val calcResult =
                    pathfinder.calculate(primaryTimeout, failureTimeout)
                synchronized(pathPlanLock) {
                    val executor =
                        calcResult
                            .getPath()
                            .map(
                                Function { p: IPath? ->
                                    PathExecutor(
                                        this@PathingBehavior,
                                        p,
                                    )
                                },
                            )
                    if (current == null) {
                        if (executor.isPresent) {
                            if (executor
                                    .get()
                                    .path
                                    .positions()
                                    .contains(expectedSegmentStart)
                            ) {
                                queuePathEvent(PathEvent.CALC_FINISHED_NOW_EXECUTING)
                                current = executor.get()
                                resetEstimatedTicksToGoal(PackedBlockPos(start))
                            } else {
                                log
                                    .atWarn()
                                    .log(
                                        "Discarding orphan path segment with" +
                                            " incorrect start",
                                    )
                            }
                        } else {
                            if ((
                                    calcResult.type
                                        != PathCalculationResult.Type.CANCELLATION
                                ) &&
                                (
                                    calcResult.type
                                        != PathCalculationResult.Type.EXCEPTION
                                )
                            ) {
                                // don't dispatch CALC_FAILED on cancellation
                                queuePathEvent(PathEvent.CALC_FAILED)
                            }
                        }
                    } else {
                        if (next == null) {
                            if (executor.isPresent) {
                                if (executor
                                        .get()
                                        .path
                                        .src
                                    == current!!.path.dest
                                ) {
                                    queuePathEvent(
                                        PathEvent.NEXT_SEGMENT_CALC_FINISHED,
                                    )
                                    next = executor.get()
                                } else {
                                    log
                                        .atWarn()
                                        .log(
                                            "Discarding orphan next segment" +
                                                " with incorrect start",
                                        )
                                }
                            } else {
                                queuePathEvent(PathEvent.NEXT_CALC_FAILED)
                            }
                        } else {
                            // throw new IllegalStateException("I have no idea what to
                            // do with this path");
                            // no point in throwing an exception here, and it gets it
                            // stuck with inProgress being not null
                            log
                                .atWarn()
                                .log(
                                    "PathingBehavior illegal state, discarding" +
                                        " invalid path",
                                )
                        }
                    }
                    val shouldLogCompletion =
                        when (reason) {
                            PathfindingReason.INITIAL_PATH, PathfindingReason.RECOVERY -> true
                            PathfindingReason.PLAN_AHEAD -> false
                        }

                    if (shouldLogCompletion && current != null) {
                        if (goal.isInGoal(current!!.path.dest.toBlockPos())) {
                            log
                                .atInfo()
                                .addKeyValue("reason", reason.name.lowercase())
                                .addKeyValue("start", PackedBlockPos(start).format())
                                .addKeyValue("goal", goal)
                                .addKeyValue(
                                    "nodes_considered",
                                    current!!.path.numNodesConsidered,
                                ).log("Finished finding path")
                        } else {
                            log
                                .atDebug()
                                .addKeyValue("reason", reason.name.lowercase())
                                .addKeyValue("start", PackedBlockPos(start).format())
                                .addKeyValue("goal", goal)
                                .addKeyValue(
                                    "nodes_considered",
                                    current!!.path.numNodesConsidered,
                                ).log("Found path segment")
                        }
                    }
                    synchronized(pathCalcLock) {
                        inProgress = null
                    }
                }
            }
    }

    private fun createPathfinder(
        start: BlockPos,
        goal: Goal,
        previous: IPath?,
        context: CalculationContext,
    ): AbstractNodeCostSearch {
        var transformed: Goal = goal
        if (Agent
                .getPrimaryAgent()
                .settings.simplifyUnloadedYCoord.value &&
            goal is IGoalRenderPos
        ) {
            val pos = (goal as IGoalRenderPos).getGoalPos()
            if (!context.bsi.worldContainsLoadedChunk(pos.x, pos.z)) {
                transformed = GoalXZ(pos.x, pos.z)
            }
        }
        val preferredPaths =
            PreferredPaths(context.getMaestro().getPlayerContext(), previous, context)
        val feet = ctx.playerFeet()
        var realStart = PackedBlockPos(start)
        val sub = feet.subtract(realStart.toBlockPos())
        if (feet.y == realStart.y && abs(sub.x) <= 1 && abs(sub.z) <= 1) {
            realStart = feet
        }
        return AStarPathFinder(
            realStart,
            start.x,
            start.y,
            start.z,
            transformed,
            preferredPaths,
            context,
            movementProvider,
        )
    }

    override fun onRenderPass(event: RenderEvent?) {
        PathRenderer.render(event, this)
    }

    companion object {
        private val log: Logger = Loggers.Path.get()
    }
}
