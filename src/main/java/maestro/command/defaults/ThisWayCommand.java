package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.pathing.goals.GoalXZ;

public class ThisWayCommand extends Command {

    public ThisWayCommand(IAgent maestro) {
        super(maestro, "thisway", "forward");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireExactly(1);
        GoalXZ goal =
                GoalXZ.fromDirection(
                        ctx.playerFeetAsVec(),
                        ctx.player().getYHeadRot(),
                        args.getAs(Double.class));
        maestro.getCustomGoalProcess().setGoal(goal);
        logDirect(String.format("Goal: %s", goal));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Travel in your current direction";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Creates a GoalXZ some amount of blocks in the direction you're currently looking",
                "",
                "Usage:",
                "> thisway <distance> - makes a GoalXZ distance blocks in front of you");
    }
}
