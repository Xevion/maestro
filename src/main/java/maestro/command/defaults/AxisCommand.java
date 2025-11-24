package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.goals.GoalAxis;

public class AxisCommand extends Command {

    public AxisCommand(IAgent maestro) {
        super(maestro, "axis", "highway");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        Goal goal = new GoalAxis();
        maestro.getCustomGoalProcess().setGoal(goal);
        logDirect(String.format("Goal: %s", goal));
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
