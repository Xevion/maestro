package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.command.exception.CommandInvalidStateException;
import maestro.api.pathing.calc.IPathingControlManager;
import maestro.api.process.IMaestroProcess;
import maestro.api.process.PathingCommand;

public class ProcCommand extends Command {

    public ProcCommand(IAgent maestro) {
        super(maestro, "proc");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        IPathingControlManager pathingControlManager = maestro.getPathingControlManager();
        IMaestroProcess process = pathingControlManager.mostRecentInControl().orElse(null);
        if (process == null) {
            throw new CommandInvalidStateException("No process in control");
        }
        logDirect(
                String.format(
                        """
                        Class: %s
                        Priority: %f
                        Temporary: %b
                        Display name: %s
                        Last command: %s\
                        """,
                        process.getClass().getTypeName(),
                        process.priority(),
                        process.isTemporary(),
                        process.displayName(),
                        pathingControlManager
                                .mostRecentCommand()
                                .map(PathingCommand::toString)
                                .orElse("None")));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "View process state information";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The proc command provides miscellaneous information about the process currently"
                        + " controlling Maestro.",
                "",
                "You are not expected to understand this if you aren't familiar with how Maestro"
                        + " works.",
                "",
                "Usage:",
                "> proc - View process information, if present");
    }
}
