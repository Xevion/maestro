package maestro.api.command.exception;

import static maestro.api.utils.Helper.HELPER;

import java.util.List;
import maestro.api.command.ICommand;
import maestro.api.command.argument.ICommandArgument;

public class CommandNotFoundException extends CommandException {

    public final String command;

    public CommandNotFoundException(String command) {
        super(String.format("Command not found: %s", command));
        this.command = command;
    }

    @Override
    public void handle(ICommand command, List<ICommandArgument> args) {
        HELPER.logDirect(getMessage());
    }
}
