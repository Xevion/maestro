package maestro.process

import maestro.Agent
import maestro.api.pathing.goals.Goal
import maestro.api.process.ICustomGoalProcess
import maestro.api.process.PathingCommand
import maestro.api.process.PathingCommandType
import maestro.utils.MaestroProcessHelper

class CustomGoalProcess(
    maestro: Agent,
) : MaestroProcessHelper(maestro),
    ICustomGoalProcess {
    /** The current goal */
    private var goal: Goal? = null

    /** The most recent goal. Not invalidated upon [onLostControl] */
    private var mostRecentGoal: Goal? = null

    /** The current process state. */
    private var state: State = State.None

    override fun setGoal(goal: Goal) {
        this.goal = goal
        this.mostRecentGoal = goal

        if (maestro.getElytraProcess().isActive) {
            maestro.getElytraProcess().pathTo(goal)
        }

        state =
            when (state) {
                State.None -> State.GoalSet
                State.Executing -> State.PathRequested
                else -> state
            }
    }

    override fun path() {
        state = State.PathRequested
    }

    override fun getGoal(): Goal? = goal

    override fun mostRecentGoal(): Goal? = mostRecentGoal

    override fun isActive(): Boolean = state != State.None

    override fun onTick(
        calcFailed: Boolean,
        isSafeToCancel: Boolean,
    ): PathingCommand {
        return when (state) {
            State.GoalSet -> {
                PathingCommand(goal, PathingCommandType.CANCEL_AND_SET_GOAL)
            }

            State.PathRequested -> {
                // Return FORCE_REVALIDATE_GOAL_AND_PATH just once
                state = State.Executing
                PathingCommand(goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH)
            }

            State.Executing -> {
                if (calcFailed) {
                    onLostControl()
                    return PathingCommand(goal, PathingCommandType.CANCEL_AND_SET_GOAL)
                }

                val currentGoal = goal
                if (currentGoal == null ||
                    (
                        currentGoal.isInGoal(ctx.playerFeet()) &&
                            currentGoal.isInGoal(maestro.getPathingBehavior().pathStart())
                    )
                ) {
                    // We're there xd
                    onLostControl()

                    if (Agent.settings().disconnectOnArrival.value) {
                        ctx.world().disconnect()
                    }

                    if (Agent.settings().notificationOnPathComplete.value) {
                        logNotification("Pathing complete", false)
                    }

                    return PathingCommand(goal, PathingCommandType.CANCEL_AND_SET_GOAL)
                }

                PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH)
            }

            State.None -> {
                throw IllegalStateException("Unexpected state $state")
            }
        }
    }

    override fun onLostControl() {
        state = State.None
        goal = null
    }

    override fun displayName0(): String = "Custom Goal $goal"

    sealed class State {
        object None : State()

        object GoalSet : State()

        object PathRequested : State()

        object Executing : State()
    }
}
