package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.behavior.PathingBehavior;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.exception.CommandException;

public class ForceCancelCommand extends Command {

    public ForceCancelCommand(Agent maestro) {
        super(maestro, "forcecancel");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        PathingBehavior pathingBehavior = maestro.getPathingBehavior();
        pathingBehavior.cancelEverything();
        pathingBehavior.forceCancel();
        log.atInfo().log("Pathing force canceled");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Force cancel";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList("Like cancel, but more forceful.", "", "Usage:", "> forcecancel");
    }
}
