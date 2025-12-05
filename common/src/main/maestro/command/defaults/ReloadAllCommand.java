package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.utils.Loggers;
import org.slf4j.Logger;

public class ReloadAllCommand extends Command {

    private static final Logger log = Loggers.Cmd.get();

    public ReloadAllCommand(Agent maestro) {
        super(maestro, "reloadall");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        ctx.worldData().getCachedWorld().reloadAllFromDisk();
        log.atInfo().log("Reloaded");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Reloads Maestro's cache for this world";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The reloadall command reloads Maestro's world cache.",
                "",
                "Usage:",
                "> reloadall");
    }
}
