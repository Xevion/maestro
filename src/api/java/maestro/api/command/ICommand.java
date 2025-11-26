package maestro.api.command;

import java.util.List;
import java.util.stream.Stream;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;

/** The base for a command. */
public interface ICommand {

    /** Called when this command is executed. */
    void execute(String label, IArgConsumer args) throws CommandException;

    /**
     * Called when the command needs to tab complete. Return a Stream representing the entries to
     * put in the completions list.
     */
    Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException;

    /**
     * @return A <b>single-line</b> string containing a short description of this command's purpose.
     */
    String getShortDesc();

    /**
     * @return A list of lines that will be printed by the help command when the user wishes to view
     *     them.
     */
    List<String> getLongDesc();

    /**
     * @return A list of the names that can be accepted to have arguments passed to this command
     */
    List<String> getNames();

    /**
     * @return {@code true} if this command should be hidden from the help menu
     */
    default boolean hiddenFromHelp() {
        return false;
    }
}
