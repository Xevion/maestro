package maestro.process;

import maestro.Agent;
import maestro.api.pathing.goals.Goal;
import maestro.api.process.ICustomGoalProcess;
import maestro.api.process.PathingCommand;
import maestro.api.process.PathingCommandType;
import maestro.utils.MaestroProcessHelper;

/**
 * As set by ExampleMaestroControl or something idk
 *
 * @author leijurv
 */
public final class CustomGoalProcess extends MaestroProcessHelper implements ICustomGoalProcess {

    /** The current goal */
    private Goal goal;

    /** The most recent goal. Not invalidated upon {@link #onLostControl()} */
    private Goal mostRecentGoal;

    /**
     * The current process state.
     *
     * @see State
     */
    private State state;

    public CustomGoalProcess(Agent maestro) {
        super(maestro);
    }

    @Override
    public void setGoal(Goal goal) {
        this.goal = goal;
        this.mostRecentGoal = goal;
        if (maestro.getElytraProcess().isActive()) {
            maestro.getElytraProcess().pathTo(goal);
        }
        if (this.state == State.NONE) {
            this.state = State.GOAL_SET;
        }
        if (this.state == State.EXECUTING) {
            this.state = State.PATH_REQUESTED;
        }
    }

    @Override
    public void path() {
        this.state = State.PATH_REQUESTED;
    }

    @Override
    public Goal getGoal() {
        return this.goal;
    }

    @Override
    public Goal mostRecentGoal() {
        return this.mostRecentGoal;
    }

    @Override
    public boolean isActive() {
        return this.state != State.NONE;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        switch (this.state) {
            case GOAL_SET:
                return new PathingCommand(this.goal, PathingCommandType.CANCEL_AND_SET_GOAL);
            case PATH_REQUESTED:
                // return FORCE_REVALIDATE_GOAL_AND_PATH just once
                PathingCommand ret =
                        new PathingCommand(
                                this.goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
                this.state = State.EXECUTING;
                return ret;
            case EXECUTING:
                if (calcFailed) {
                    onLostControl();
                    return new PathingCommand(this.goal, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                if (this.goal == null
                        || (this.goal.isInGoal(ctx.playerFeet())
                                && this.goal.isInGoal(maestro.getPathingBehavior().pathStart()))) {
                    onLostControl(); // we're there xd
                    if (Agent.settings().disconnectOnArrival.value) {
                        ctx.world().disconnect();
                    }
                    if (Agent.settings().notificationOnPathComplete.value) {
                        logNotification("Pathing complete", false);
                    }
                    return new PathingCommand(this.goal, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                return new PathingCommand(this.goal, PathingCommandType.SET_GOAL_AND_PATH);
            default:
                throw new IllegalStateException("Unexpected state " + this.state);
        }
    }

    @Override
    public void onLostControl() {
        this.state = State.NONE;
        this.goal = null;
    }

    @Override
    public String displayName0() {
        return "Custom Goal " + this.goal;
    }

    protected enum State {
        NONE,
        GOAL_SET,
        PATH_REQUESTED,
        EXECUTING
    }
}
