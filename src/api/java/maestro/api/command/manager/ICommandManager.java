package maestro.api.command.manager;

import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.command.ICommand;
import maestro.api.command.argument.ICommandArgument;
import maestro.api.command.registry.Registry;
import net.minecraft.util.Tuple;

public interface ICommandManager {

    IAgent getMaestro();

    Registry<ICommand> getRegistry();

    /**
     * @param name The command name to search for.
     * @return The command, if found.
     */
    ICommand getCommand(String name);

    boolean execute(String string);

    boolean execute(Tuple<String, List<ICommandArgument>> expanded);

    Stream<String> tabComplete(Tuple<String, List<ICommandArgument>> expanded);

    Stream<String> tabComplete(String prefix);
}
