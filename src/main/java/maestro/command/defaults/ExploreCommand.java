package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IMaestro;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.datatypes.RelativeGoalXZ;
import maestro.api.command.exception.CommandException;
import maestro.api.pathing.goals.GoalXZ;

public class ExploreCommand extends Command {

    public ExploreCommand(IMaestro maestro) {
        super(maestro, "explore");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny()) {
            args.requireExactly(2);
        } else {
            args.requireMax(0);
        }
        GoalXZ goal =
                args.hasAny()
                        ? args.getDatatypePost(RelativeGoalXZ.INSTANCE, ctx.playerFeet())
                        : new GoalXZ(ctx.playerFeet());
        maestro.getExploreProcess().explore(goal.getX(), goal.getZ());
        logDirect(String.format("Exploring from %s", goal));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasAtMost(2)) {
            return args.tabCompleteDatatype(RelativeGoalXZ.INSTANCE);
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Explore things";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Tell Maestro to explore randomly. If you used explorefilter before this, it will"
                        + " be applied.",
                "",
                "Usage:",
                "> explore - Explore from your current position.",
                "> explore <x> <z> - Explore from the specified X and Z position.");
    }
}
