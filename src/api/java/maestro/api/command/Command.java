package maestro.api.command;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import maestro.api.IMaestro;
import maestro.api.utils.IPlayerContext;

/**
 * A default implementation of {@link ICommand} which provides easy access to the command's bound
 * {@link IMaestro} instance, {@link IPlayerContext} and an easy way to provide multiple valid
 * command execution names through the default constructor.
 *
 * <p>So basically, you should use it because it provides a small amount of boilerplate, but you're
 * not forced to use it.
 *
 * @see ICommand
 */
public abstract class Command implements ICommand {

    protected IMaestro maestro;
    protected IPlayerContext ctx;

    /** The names of this command. This is what you put after the command prefix. */
    protected final List<String> names;

    /**
     * Creates a new Maestro control command.
     *
     * @param names The names of this command. This is what you put after the command prefix.
     */
    protected Command(IMaestro maestro, String... names) {
        this.names =
                Collections.unmodifiableList(
                        Stream.of(names)
                                .map(s -> s.toLowerCase(Locale.US))
                                .collect(Collectors.toList()));
        this.maestro = maestro;
        this.ctx = maestro.getPlayerContext();
    }

    @Override
    public final List<String> getNames() {
        return this.names;
    }
}
