package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.datatypes.RelativeGoalXZ;
import maestro.command.exception.CommandException;
import maestro.pathing.goals.GoalXZ;

public class ExploreCommand extends Command {

    public ExploreCommand(Agent agent) {
        super(agent, "explore");
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
        agent.getExploreTask().explore(goal.getX(), goal.getZ());
        log.atInfo().addKeyValue("goal", goal).log("Exploration started");
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
