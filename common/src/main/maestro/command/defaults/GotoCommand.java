package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.datatypes.ForBlockOptionalMeta;
import maestro.command.datatypes.RelativeCoordinate;
import maestro.command.datatypes.RelativeGoal;
import maestro.command.exception.CommandException;
import maestro.pathing.goals.Goal;
import maestro.utils.BlockOptionalMeta;
import maestro.utils.PackedBlockPos;

public class GotoCommand extends Command {

    protected GotoCommand(Agent agent) {
        super(agent, "goto");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        // If we have a numeric first argument, then parse arguments as coordinates.
        // Note: There is no reason to want to go where you're already at so there
        // is no need to handle the case of empty arguments.
        if (args.peekDatatypeOrNull(RelativeCoordinate.INSTANCE) != null) {
            args.requireMax(3);
            PackedBlockPos origin = ctx.playerFeet();
            Goal goal = args.getDatatypePost(RelativeGoal.INSTANCE, origin);
            log.atInfo().addKeyValue("goal", goal).log("Goal set");
            agent.getCustomGoalTask().setGoalAndPath(goal);
            return;
        }
        args.requireMax(1);
        BlockOptionalMeta destination = args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
        agent.getGetToBlockTask().getToBlock(destination);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        // since it's either a goal or a block, I don't think we can tab complete properly?
        // so just tab complete for the block variant
        args.requireMax(1);
        return args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
    }

    @Override
    public String getShortDesc() {
        return "Go to a coordinate or block";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The goto command tells Maestro to head towards a given goal or block.",
                "",
                "Wherever a coordinate is expected, you can use ~ just like in regular Minecraft"
                        + " commands. Or, you can just use regular numbers.",
                "",
                "Usage:",
                "> goto <block> - Go to a block, wherever it is in the world",
                "> goto <y> - Go to a Y level",
                "> goto <x> <z> - Go to an X,Z position",
                "> goto <x> <y> <z> - Go to an X,Y,Z position");
    }
}
