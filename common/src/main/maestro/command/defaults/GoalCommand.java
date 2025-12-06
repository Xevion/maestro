package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.datatypes.RelativeCoordinate;
import maestro.command.datatypes.RelativeGoal;
import maestro.command.exception.CommandException;
import maestro.command.helpers.TabCompleteHelper;
import maestro.pathing.goals.Goal;
import maestro.task.CustomGoalTask;
import maestro.utils.PackedBlockPos;

public class GoalCommand extends Command {

    public GoalCommand(Agent maestro) {
        super(maestro, "goal");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        CustomGoalTask goalProcess = maestro.getCustomGoalTask();
        if (args.hasAny() && Arrays.asList("reset", "clear", "none").contains(args.peekString())) {
            args.requireMax(1);
            if (goalProcess.getGoal() != null) {
                goalProcess.setGoal(null);
                log.atInfo().log("Goal cleared");
            } else {
                log.atInfo().log("No goal to clear");
            }
        } else {
            args.requireMax(3);
            PackedBlockPos origin = ctx.playerFeet();
            Goal goal = args.getDatatypePost(RelativeGoal.INSTANCE, origin);
            goalProcess.setGoal(goal);
            log.atInfo().addKeyValue("goal", goal).log("Goal set");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        TabCompleteHelper helper = new TabCompleteHelper();
        if (args.hasExactlyOne()) {
            helper.append("reset", "clear", "none", "~");
        } else {
            if (args.hasAtMost(3)) {
                while (args.has(2)) {
                    if (args.peekDatatypeOrNull(RelativeCoordinate.INSTANCE) == null) {
                        break;
                    }
                    args.get();
                    if (!args.has(2)) {
                        helper.append("~");
                    }
                }
            }
        }
        return helper.filterPrefix(args.getString()).stream();
    }

    @Override
    public String getShortDesc() {
        return "Set or clear the goal";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The goal command allows you to set or clear Maestro's goal.",
                "",
                "Wherever a coordinate is expected, you can use ~ just like in regular Minecraft"
                        + " commands. Or, you can just use regular numbers.",
                "",
                "Usage:",
                "> goal - Set the goal to your current position",
                "> goal <reset/clear/none> - Erase the goal",
                "> goal <y> - Set the goal to a Y level",
                "> goal <x> <z> - Set the goal to an X,Z position",
                "> goal <x> <y> <z> - Set the goal to an X,Y,Z position");
    }
}
