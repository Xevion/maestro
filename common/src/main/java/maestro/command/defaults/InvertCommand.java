package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.goals.GoalInverted;
import maestro.api.process.ICustomGoalProcess;

public class InvertCommand extends Command {

    public InvertCommand(IAgent maestro) {
        super(maestro, "invert");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        ICustomGoalProcess customGoalProcess = maestro.getCustomGoalProcess();
        Goal goal;
        if ((goal = customGoalProcess.getGoal()) == null) {
            throw new CommandException.InvalidState("No goal");
        }
        if (goal instanceof GoalInverted) {
            goal = ((GoalInverted) goal).origin;
        } else {
            goal = new GoalInverted(goal);
        }
        customGoalProcess.setGoalAndPath(goal);
        log.atInfo().addKeyValue("goal", goal.toString()).log("Goal updated");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Run away from the current goal";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The invert command tells Maestro to head away from the current goal rather than"
                        + " towards it.",
                "",
                "Usage:",
                "> invert - Invert the current goal.");
    }
}
