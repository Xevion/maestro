package maestro.command.manager;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.command.ICommand;
import maestro.api.command.argument.ICommandArgument;
import maestro.api.command.exception.CommandException;
import maestro.api.command.exception.ICommandException;
import maestro.api.command.helpers.TabCompleteHelper;
import maestro.api.command.registry.Registry;
import maestro.api.utils.Loggers;
import maestro.command.argument.ArgConsumer;
import maestro.command.argument.CommandArguments;
import maestro.command.defaults.DefaultCommands;
import net.minecraft.util.Tuple;
import org.slf4j.Logger;

public class CommandManager {

    private static final Logger log = Loggers.get("cmd");
    private final Registry<ICommand> registry = new Registry<>();
    private final Agent maestro;

    public CommandManager(Agent maestro) {
        this.maestro = maestro;
        DefaultCommands.createAll(maestro).forEach(this.registry::register);
    }

    public Agent getMaestro() {
        return this.maestro;
    }

    public Registry<ICommand> getRegistry() {
        return this.registry;
    }

    public ICommand getCommand(String name) {
        for (ICommand command : this.registry.entries) {
            if (command.getNames().contains(name.toLowerCase(Locale.US))) {
                return command;
            }
        }
        return null;
    }

    public boolean execute(String string) {
        return this.execute(expand(string));
    }

    public boolean execute(Tuple<String, List<ICommandArgument>> expanded) {
        ExecutionWrapper execution = this.from(expanded);
        if (execution != null) {
            execution.execute();
        }
        return execution != null;
    }

    public Stream<String> tabComplete(Tuple<String, List<ICommandArgument>> expanded) {
        ExecutionWrapper execution = this.from(expanded);
        return execution == null ? Stream.empty() : execution.tabComplete();
    }

    public Stream<String> tabComplete(String prefix) {
        Tuple<String, List<ICommandArgument>> pair = expand(prefix, true);
        String label = pair.getA();
        List<ICommandArgument> args = pair.getB();
        if (args.isEmpty()) {
            return new TabCompleteHelper()
                    .addCommands(this.maestro.getCommandManager()).filterPrefix(label).stream();
        } else {
            return tabComplete(pair);
        }
    }

    private ExecutionWrapper from(Tuple<String, List<ICommandArgument>> expanded) {
        String label = expanded.getA();
        ArgConsumer args = new ArgConsumer(this, expanded.getB());

        ICommand command = this.getCommand(label);
        return command == null ? null : new ExecutionWrapper(command, label, args);
    }

    private static Tuple<String, List<ICommandArgument>> expand(
            String string, boolean preserveEmptyLast) {
        String label = string.split("\\s", 2)[0];
        List<ICommandArgument> args =
                CommandArguments.from(string.substring(label.length()), preserveEmptyLast);
        return new Tuple<>(label, args);
    }

    public static Tuple<String, List<ICommandArgument>> expand(String string) {
        return expand(string, false);
    }

    private static final class ExecutionWrapper {

        private ICommand command;
        private String label;
        private ArgConsumer args;

        private ExecutionWrapper(ICommand command, String label, ArgConsumer args) {
            this.command = command;
            this.label = label;
            this.args = args;
        }

        private void execute() {
            try {
                this.command.execute(this.label, this.args);
            } catch (Throwable t) {
                // Create a handleable exception, wrap if needed
                ICommandException exception =
                        t instanceof ICommandException
                                ? (ICommandException) t
                                : new CommandException.Unhandled(t);

                exception.handle(
                        command instanceof maestro.api.command.Command
                                ? (maestro.api.command.Command) command
                                : null,
                        args.getArgs());
            }
        }

        private Stream<String> tabComplete() {
            try {
                return this.command.tabComplete(this.label, this.args);
            } catch (CommandException ignored) {
                // NOP
            } catch (Throwable t) {
                log.atError().setCause(t).log("Error during tab completion");
            }
            return Stream.empty();
        }
    }
}
