package maestro.pathing

import maestro.Agent
import maestro.api.event.events.TickEvent
import maestro.api.event.listener.AbstractGameEventListener
import maestro.api.pathing.goals.Goal
import maestro.api.task.ITask
import maestro.api.task.PathingCommand
import maestro.api.task.PathingCommandType
import maestro.behavior.PathingBehavior
import maestro.pathing.path.PathExecutor
import net.minecraft.core.BlockPos
import java.util.Optional

class TaskCoordinator(
    private val maestro: Agent,
) {
    private val tasks = mutableSetOf<ITask>()
    private val active = mutableListOf<ITask>()
    private var inControlLastTick: ITask? = null
    private var inControlThisTick: ITask? = null
    private var command: PathingCommand? = null

    init {
        maestro.gameEventHandler.registerEventListener(
            object : AbstractGameEventListener { // needs to be after all behavior ticks
                override fun onTick(event: TickEvent) {
                    if (event.type == TickEvent.Type.IN) {
                        postTick()
                    }
                }
            },
        )
    }

    fun registerTask(task: ITask) {
        task.onLostControl() // make sure it's reset
        tasks.add(task)
    }

    fun cancelEverything() { // called by PathingBehavior on TickEvent Type OUT
        inControlLastTick = null
        inControlThisTick = null
        command = null
        active.clear()
        for (proc in tasks) {
            proc.onLostControl()
            // It's okay only for a temporary thing (like combat pause) to maintain control even if you say
            // to cancel
            if (proc.isActive() && !proc.isTemporary()) {
                throw IllegalStateException("${proc.displayName()} stayed active after being cancelled")
            }
        }
    }

    fun mostRecentInControl(): Optional<ITask> = Optional.ofNullable(inControlThisTick)

    fun mostRecentCommand(): Optional<PathingCommand> = Optional.ofNullable(command)

    fun preTick() {
        inControlLastTick = inControlThisTick
        inControlThisTick = null
        val p: PathingBehavior = maestro.pathingBehavior
        command = executeTasks()
        val cmd = command

        if (cmd == null) {
            p.cancelSegmentIfSafe()
            p.secretInternalSetGoal(null)
            return
        }

        if (inControlThisTick != inControlLastTick &&
            cmd.commandType != PathingCommandType.REQUEST_PAUSE &&
            inControlLastTick != null &&
            !(inControlLastTick ?: return).isTemporary()
        ) {
            // if control has changed from a real process to another real process, and the new process wants to do something
            p.cancelSegmentIfSafe()
            // get rid of the in progress stuff from the last process
        }

        when (cmd.commandType) {
            PathingCommandType.SET_GOAL_AND_PAUSE -> {
                p.secretInternalSetGoalAndPath(cmd)
                p.requestPause()
            }

            PathingCommandType.REQUEST_PAUSE -> {
                p.requestPause()
            }

            PathingCommandType.CANCEL_AND_SET_GOAL -> {
                p.secretInternalSetGoal(cmd.goal)
                p.cancelSegmentIfSafe()
            }

            PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH,
            PathingCommandType.REVALIDATE_GOAL_AND_PATH,
            -> {
                if (!p.isPathing() && p.getInProgress().isEmpty) {
                    p.secretInternalSetGoalAndPath(cmd)
                }
            }

            PathingCommandType.SET_GOAL_AND_PATH -> {
                // now this I can do
                if (cmd.goal != null) {
                    p.secretInternalSetGoalAndPath(cmd)
                }
            }

            PathingCommandType.DEFER -> {
                throw IllegalStateException("Unexpected command type ${cmd.commandType}")
            }
        }
    }

    private fun postTick() {
        // if we did this in preTick, it would suck
        // we use the time between ticks as calculation time
        // therefore, we only cancel and recalculate after the tick for the current path has executed
        // "it would suck" means it would actually execute a path every other tick
        val cmd = command ?: return

        val p: PathingBehavior = maestro.pathingBehavior
        when (cmd.commandType) {
            PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH -> {
                if (cmd.goal == null ||
                    forceRevalidate(cmd.goal) ||
                    revalidateGoal(cmd.goal)
                ) {
                    // pwnage
                    p.softCancelIfSafe()
                }
                p.secretInternalSetGoalAndPath(cmd)
            }

            PathingCommandType.REVALIDATE_GOAL_AND_PATH -> {
                if (Agent
                        .getPrimaryAgent()
                        .settings.cancelOnGoalInvalidation.value &&
                    (cmd.goal == null || revalidateGoal(cmd.goal))
                ) {
                    p.softCancelIfSafe()
                }
                p.secretInternalSetGoalAndPath(cmd)
            }

            else -> {} // No action for other command types
        }
    }

    fun forceRevalidate(newGoal: Goal): Boolean {
        val current: PathExecutor? = maestro.pathingBehavior.getCurrent()
        if (current != null) {
            if (newGoal.isInGoal(current.path.dest.toBlockPos())) {
                return false
            }
            return newGoal != current.path.goal
        }
        return false
    }

    fun revalidateGoal(newGoal: Goal): Boolean {
        val current: PathExecutor? = maestro.pathingBehavior.getCurrent()
        if (current != null) {
            val intended: Goal = current.path.goal
            val end: BlockPos = current.path.dest.toBlockPos()
            // this path used to end in the goal
            // but the goal has changed, so there's no reason to continue...
            return intended.isInGoal(end) && !newGoal.isInGoal(end)
        }
        return false
    }

    fun executeTasks(): PathingCommand? {
        for (task in tasks) {
            if (task.isActive()) {
                if (!active.contains(task)) {
                    // put a newly active task at the very front of the queue
                    active.add(0, task)
                }
            } else {
                active.remove(task)
            }
        }
        // ties are broken by which was added to the beginning of the list first
        active.sortByDescending { it.priority() }

        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val proc = iterator.next()

            val exec =
                proc.onTick(
                    proc == inControlLastTick && maestro.pathingBehavior.calcFailedLastTick(),
                    maestro.pathingBehavior.isSafeToCancel(),
                )

            if (exec == null) {
                if (proc.isActive()) {
                    throw IllegalStateException("${proc.displayName()} actively returned null PathingCommand")
                }
                // no need to call onLostControl; they are reporting inactive.
            } else if (exec.commandType != PathingCommandType.DEFER) {
                inControlThisTick = proc
                if (!proc.isTemporary()) {
                    iterator.forEachRemaining { it.onLostControl() }
                }
                return exec
            }
        }
        return null
    }
}
