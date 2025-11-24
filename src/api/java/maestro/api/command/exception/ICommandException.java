package maestro.api.command.exception;

import static maestro.api.utils.Helper.HELPER;

import java.util.List;
import maestro.api.command.ICommand;
import maestro.api.command.argument.ICommandArgument;
import net.minecraft.ChatFormatting;

/**
 * The base for a Maestro Command Exception, checked or unchecked. Provides a {@link
 * #handle(ICommand, List)} method that is used to provide useful output to the user for diagnosing
 * issues that may have occurred during execution.
 *
 * <p>Anything implementing this interface should be assignable to {@link Exception}.
 */
public interface ICommandException {

    /**
     * @return The exception details
     * @see Exception#getMessage()
     */
    String getMessage();

    /**
     * Called when this exception is thrown, to handle the exception.
     *
     * @param command The command that threw it.
     * @param args The arguments the command was called with.
     */
    default void handle(ICommand command, List<ICommandArgument> args) {
        HELPER.logDirect(this.getMessage(), ChatFormatting.RED);
    }
}
