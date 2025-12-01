package maestro.utils;

import java.util.*;
import maestro.Agent;
import maestro.api.event.events.TickEvent;
import maestro.api.event.listener.AbstractGameEventListener;
import maestro.api.pathing.calc.IPathingControlManager;
import maestro.api.pathing.goals.Goal;
import maestro.api.process.IMaestroProcess;
import maestro.api.process.PathingCommand;
import maestro.api.process.PathingCommandType;
import maestro.behavior.PathingBehavior;
import maestro.pathing.path.PathExecutor;
import net.minecraft.core.BlockPos;

public class PathingControlManager implements IPathingControlManager {

    private final Agent maestro;
    private final HashSet<IMaestroProcess> processes; // unGh
    private final List<IMaestroProcess> active;
    private IMaestroProcess inControlLastTick;
    private IMaestroProcess inControlThisTick;
    private PathingCommand command;

    public PathingControlManager(Agent maestro) {
        this.maestro = maestro;
        this.processes = new HashSet<>();
        this.active = new ArrayList<>();
        maestro.getGameEventHandler()
                .registerEventListener(
                        new AbstractGameEventListener() { // needs to be after all behavior ticks
                            @Override
                            public void onTick(TickEvent event) {
                                if (event.type == TickEvent.Type.IN) {
                                    postTick();
                                }
                            }
                        });
    }

    @Override
    public void registerProcess(IMaestroProcess process) {
        process.onLostControl(); // make sure it's reset
        processes.add(process);
    }

    public void cancelEverything() { // called by PathingBehavior on TickEvent Type OUT
        inControlLastTick = null;
        inControlThisTick = null;
        command = null;
        active.clear();
        for (IMaestroProcess proc : processes) {
            proc.onLostControl();
            if (proc.isActive()
                    && !proc.isTemporary()) { // it's okay only for a temporary thing (like combat
                // pause) to maintain control even if you say to
                // cancel
                throw new IllegalStateException(
                        proc.displayName() + " stayed active after being cancelled");
            }
        }
    }

    @Override
    public Optional<IMaestroProcess> mostRecentInControl() {
        return Optional.ofNullable(inControlThisTick);
    }

    @Override
    public Optional<PathingCommand> mostRecentCommand() {
        return Optional.ofNullable(command);
    }

    public void preTick() {
        inControlLastTick = inControlThisTick;
        inControlThisTick = null;
        PathingBehavior p = maestro.getPathingBehavior();
        command = executeProcesses();
        if (command == null) {
            p.cancelSegmentIfSafe();
            p.secretInternalSetGoal(null);
            return;
        }
        if (!Objects.equals(inControlThisTick, inControlLastTick)
                && command.commandType != PathingCommandType.REQUEST_PAUSE
                && inControlLastTick != null
                && !inControlLastTick.isTemporary()) {
            // if control has changed from a real process to another real process, and the new
            // process wants to do something
            p.cancelSegmentIfSafe();
            // get rid of the in progress stuff from the last process
        }
        switch (command.commandType) {
            case SET_GOAL_AND_PAUSE:
                p.secretInternalSetGoalAndPath(command);
            case REQUEST_PAUSE:
                p.requestPause();
                break;
            case CANCEL_AND_SET_GOAL:
                p.secretInternalSetGoal(command.goal);
                p.cancelSegmentIfSafe();
                break;
            case FORCE_REVALIDATE_GOAL_AND_PATH:
            case REVALIDATE_GOAL_AND_PATH:
                if (!p.isPathing() && p.getInProgress().isEmpty()) {
                    p.secretInternalSetGoalAndPath(command);
                }
                break;
            case SET_GOAL_AND_PATH:
                // now this I can do
                if (command.goal != null) {
                    p.secretInternalSetGoalAndPath(command);
                }
                break;
            default:
                throw new IllegalStateException("Unexpected command type " + command.commandType);
        }
    }

    private void postTick() {
        // if we did this in pretick, it would suck
        // we use the time between ticks as calculation time
        // therefore, we only cancel and recalculate after the tick for the current path has
        // executed
        // "it would suck" means it would actually execute a path every other tick
        if (command == null) {
            return;
        }
        PathingBehavior p = maestro.getPathingBehavior();
        switch (command.commandType) {
            case FORCE_REVALIDATE_GOAL_AND_PATH:
                if (command.goal == null
                        || forceRevalidate(command.goal)
                        || revalidateGoal(command.goal)) {
                    // pwnage
                    p.softCancelIfSafe();
                }
                p.secretInternalSetGoalAndPath(command);
                break;
            case REVALIDATE_GOAL_AND_PATH:
                if (Agent.settings().cancelOnGoalInvalidation.value
                        && (command.goal == null || revalidateGoal(command.goal))) {
                    p.softCancelIfSafe();
                }
                p.secretInternalSetGoalAndPath(command);
                break;
            default:
        }
    }

    public boolean forceRevalidate(Goal newGoal) {
        PathExecutor current = maestro.getPathingBehavior().getCurrent();
        if (current != null) {
            if (newGoal.isInGoal(current.getPath().getDest())) {
                return false;
            }
            return !newGoal.equals(current.getPath().getGoal());
        }
        return false;
    }

    public boolean revalidateGoal(Goal newGoal) {
        PathExecutor current = maestro.getPathingBehavior().getCurrent();
        if (current != null) {
            Goal intended = current.getPath().getGoal();
            BlockPos end = current.getPath().getDest();
            // this path used to end in the goal
            // but the goal has changed, so there's no reason to continue...
            return intended.isInGoal(end) && !newGoal.isInGoal(end);
        }
        return false;
    }

    public PathingCommand executeProcesses() {
        for (IMaestroProcess process : processes) {
            if (process.isActive()) {
                if (!active.contains(process)) {
                    // put a newly active process at the very front of the queue
                    active.addFirst(process);
                }
            } else {
                active.remove(process);
            }
        }
        // ties are broken by which was added to the beginning of the list first
        active.sort(Comparator.comparingDouble(IMaestroProcess::priority).reversed());

        Iterator<IMaestroProcess> iterator = active.iterator();
        while (iterator.hasNext()) {
            IMaestroProcess proc = iterator.next();

            PathingCommand exec =
                    proc.onTick(
                            Objects.equals(proc, inControlLastTick)
                                    && maestro.getPathingBehavior().calcFailedLastTick(),
                            maestro.getPathingBehavior().isSafeToCancel());
            if (exec == null) {
                if (proc.isActive()) {
                    throw new IllegalStateException(
                            proc.displayName() + " actively returned null PathingCommand");
                }
                // no need to call onLostControl; they are reporting inactive.
            } else if (exec.commandType != PathingCommandType.DEFER) {
                inControlThisTick = proc;
                if (!proc.isTemporary()) {
                    iterator.forEachRemaining(IMaestroProcess::onLostControl);
                }
                return exec;
            }
        }
        return null;
    }
}
