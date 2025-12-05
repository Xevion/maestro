package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.task.ITask;
import maestro.api.task.PathingCommand;
import maestro.pathing.TaskCoordinator;

public class ProcCommand extends Command {

    public ProcCommand(Agent maestro) {
        super(maestro, "proc");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        TaskCoordinator pathingControlManager = maestro.getPathingControlManager();
        ITask process = pathingControlManager.mostRecentInControl().orElse(null);
        if (process == null) {
            throw new CommandException.InvalidState("No process in control");
        }
        log.atInfo()
                .addKeyValue("class", process.getClass().getTypeName())
                .addKeyValue("priority", process.priority())
                .addKeyValue("temporary", process.isTemporary())
                .addKeyValue("display_name", process.displayName())
                .addKeyValue(
                        "last_command",
                        pathingControlManager
                                .mostRecentCommand()
                                .map(PathingCommand::toString)
                                .orElse("None"))
                .log("Process state information");
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
