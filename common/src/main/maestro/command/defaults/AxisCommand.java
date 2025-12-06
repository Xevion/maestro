package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.exception.CommandException;
import maestro.pathing.goals.Goal;
import maestro.pathing.goals.GoalAxis;

public class AxisCommand extends Command {

    public AxisCommand(Agent agent) {
        super(agent, "axis", "highway");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        Goal goal = new GoalAxis();
        agent.getCustomGoalTask().setGoal(goal);
        log.atInfo().addKeyValue("goal", goal).log("Goal set");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Set a goal to the axes";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The axis command sets a goal that tells Maestro to head towards the nearest axis."
                        + " That is, X=0 or Z=0.",
                "",
                "Usage:",
                "> axis");
    }
}
