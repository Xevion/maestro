package maestro.command.argparser;

import maestro.api.command.argparser.IArgParser;
import maestro.api.command.argparser.IArgParserManager;
import maestro.api.command.argument.ICommandArgument;
import maestro.api.command.exception.CommandException;
import maestro.api.command.registry.Registry;

public enum ArgParserManager implements IArgParserManager {
    INSTANCE;

    public final Registry<IArgParser> registry = new Registry<>();

    ArgParserManager() {
        DefaultArgParsers.ALL.forEach(this.registry::register);
    }

    @Override
    public <T> IArgParser.Stateless<T> getParserStateless(Class<T> type) {
        //noinspection unchecked
        return this.registry
                .descendingStream()
                .filter(IArgParser.Stateless.class::isInstance)
                .map(IArgParser.Stateless.class::cast)
                .filter(parser -> parser.getTarget().isAssignableFrom(type))
                .findFirst()
                .orElse(null);
    }

    @Override
    public <T, S> IArgParser.Stated<T, S> getParserStated(Class<T> type, Class<S> stateKlass) {
        //noinspection unchecked
        return this.registry
                .descendingStream()
                .filter(IArgParser.Stated.class::isInstance)
                .map(IArgParser.Stated.class::cast)
                .filter(parser -> parser.getTarget().isAssignableFrom(type))
                .filter(parser -> parser.getStateType().isAssignableFrom(stateKlass))
                .findFirst()
                .orElse(null);
    }

    @Override
    public <T> T parseStateless(Class<T> type, ICommandArgument arg) throws CommandException {
        IArgParser.Stateless<T> parser = this.getParserStateless(type);
        if (parser == null) {
            throw new CommandException.NoParserForType(type);
        }
        try {
            return parser.parseArg(arg);
        } catch (Exception exc) {
            throw new CommandException.InvalidArgument.InvalidType(arg, type.getSimpleName());
        }
    }

    @Override
    public <T, S> T parseStated(Class<T> type, Class<S> stateKlass, ICommandArgument arg, S state)
            throws CommandException {
        IArgParser.Stated<T, S> parser = this.getParserStated(type, stateKlass);
        if (parser == null) {
            throw new CommandException.NoParserForType(type);
        }
        try {
            return parser.parseArg(arg, state);
        } catch (Exception exc) {
            throw new CommandException.InvalidArgument.InvalidType(arg, type.getSimpleName());
        }
    }

    @Override
    public Registry<IArgParser> getRegistry() {
        return this.registry;
    }
}
