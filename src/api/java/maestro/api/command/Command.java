package maestro.api.command;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.utils.IPlayerContext;
import maestro.api.utils.MaestroLogger;
import org.slf4j.Logger;

/**
 * A default implementation of {@link ICommand} which provides easy access to the command's bound
 * {@link IAgent} instance, {@link IPlayerContext} and an easy way to provide multiple valid command
 * execution names through the default constructor.
 *
 * <p>So basically, you should use it because it provides a small amount of boilerplate, but you're
 * not forced to use it.
 *
 * @see ICommand
 */
public abstract class Command implements ICommand {

    protected IAgent maestro;
    protected IPlayerContext ctx;

    /** Logger for command execution messages. Uses the "cmd" category. */
    protected final Logger log;

    /** The names of this command. This is what you put after the command prefix. */
    protected final List<String> names;

    /**
     * Creates a new Maestro control command.
     *
     * @param names The names of this command. This is what you put after the command prefix.
     */
    protected Command(IAgent maestro, String... names) {
        this.names =
                Stream.of(names)
                        .map(s -> s.toLowerCase(Locale.US))
                        .collect(Collectors.toUnmodifiableList());
        this.maestro = maestro;
        this.ctx = maestro.getPlayerContext();
        this.log = MaestroLogger.get("cmd");
    }

    @Override
    public final List<String> getNames() {
        return this.names;
    }
}
