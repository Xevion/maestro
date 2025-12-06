package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.exception.CommandException;

public class VersionCommand extends Command {

    public VersionCommand(Agent maestro) {
        super(maestro, "version");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        String version = getClass().getPackage().getImplementationVersion();
        if (version == null) {
            throw new CommandException.InvalidState(
                    "Null version (this is normal in a dev environment)");
        } else {
            log.atInfo().addKeyValue("version", version).log("Maestro version");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "View the Maestro version";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The version command prints the version of Maestro you're currently running.",
                "",
                "Usage:",
                "> version - View version information, if present");
    }
}
