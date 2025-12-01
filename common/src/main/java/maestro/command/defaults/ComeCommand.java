package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.pathing.goals.GoalBlock;

public class ComeCommand extends Command {

    public ComeCommand(IAgent maestro) {
        super(maestro, "come");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        maestro.getCustomGoalProcess().setGoalAndPath(new GoalBlock(ctx.viewerPos()));
        log.atInfo().log("Pathing to camera position");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Start heading towards your camera";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The come command tells Maestro to head towards your camera.",
                "",
                "This can be useful in hacked clients where freecam doesn't move your player"
                        + " position.",
                "",
                "Usage:",
                "> come");
    }
}
