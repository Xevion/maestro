package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.exception.CommandException;

public class ClickCommand extends Command {

    public ClickCommand(Agent agent) {
        super(agent, "click");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        agent.openClick();
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Open click";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList("Opens click dude", "", "Usage:", "> click");
    }
}
