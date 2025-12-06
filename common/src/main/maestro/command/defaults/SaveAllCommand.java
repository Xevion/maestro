package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.exception.CommandException;
import maestro.utils.Loggers;
import org.slf4j.Logger;

public class SaveAllCommand extends Command {

    private static final Logger log = Loggers.Cmd.get();

    public SaveAllCommand(Agent maestro) {
        super(maestro, "saveall");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        ctx.worldData().getCachedWorld().save();
        log.atInfo().log("Saved");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Saves Maestro's cache for this world";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The saveall command saves Maestro's world cache.", "", "Usage:", "> saveall");
    }
}
