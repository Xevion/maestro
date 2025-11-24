package maestro.api.command.exception;

import static maestro.api.utils.Helper.HELPER;

import java.util.List;
import maestro.api.command.ICommand;
import maestro.api.command.argument.ICommandArgument;

public class CommandUnhandledException extends RuntimeException implements ICommandException {

    public CommandUnhandledException(String message) {
        super(message);
    }

    public CommandUnhandledException(Throwable cause) {
        super(cause);
    }

    @Override
    public void handle(ICommand command, List<ICommandArgument> args) {
        HELPER.logUnhandledException(this);
    }
}
