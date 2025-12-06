package maestro.command;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.player.PlayerContext;
import maestro.utils.Loggers;
import org.slf4j.Logger;

/**
 * A default implementation of {@link ICommand} which provides easy access to the command's bound
 * {@link Agent} instance, {@link PlayerContext} and an easy way to provide multiple valid command
 * execution names through the default constructor.
 *
 * <p>So basically, you should use it because it provides a small amount of boilerplate, but you're
 * not forced to use it.
 *
 * @see ICommand
 */
public abstract class Command implements ICommand {

    protected Agent maestro;
    protected PlayerContext ctx;

    /** Logger for command execution messages. Uses the "cmd" category. */
    protected final Logger log;

    /** The names of this command. This is what you put after the command prefix. */
    protected final List<String> names;

    /**
     * Creates a new Maestro control command.
     *
     * @param names The names of this command. This is what you put after the command prefix.
     */
    protected Command(Agent maestro, String... names) {
        this.names = Stream.of(names).map(s -> s.toLowerCase(Locale.US)).toList();
        this.maestro = maestro;
        this.ctx = maestro.getPlayerContext();
        this.log = Loggers.Cmd.get();
    }

    @Override
    public final List<String> getNames() {
        return this.names;
    }
}
