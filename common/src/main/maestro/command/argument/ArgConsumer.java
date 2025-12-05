package maestro.command.argument;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.argument.ICommandArgument;
import maestro.api.command.datatypes.IDatatype;
import maestro.api.command.datatypes.IDatatypeContext;
import maestro.api.command.datatypes.IDatatypeFor;
import maestro.api.command.datatypes.IDatatypePost;
import maestro.api.command.exception.CommandException;
import maestro.api.utils.Loggers;
import maestro.command.manager.CommandManager;
import org.slf4j.Logger;

public class ArgConsumer implements IArgConsumer {

    private static final Logger log = Loggers.get("cmd");

    /**
     * The parent {@link CommandManager} for this {@link IArgConsumer}}. Used by {@link #context}.
     */
    private final CommandManager manager;

    /**
     * The {@link IDatatypeContext} instance for this {@link IArgConsumer}}, passed to datatypes
     * when an operation is performed upon them.
     *
     * @see IDatatype
     * @see IDatatypeContext
     */
    private final IDatatypeContext context;

    /** The list of arguments in this ArgConsumer */
    private final List<ICommandArgument> args;

    /**
     * The list of consumed arguments for this ArgConsumer. The most recently consumed argument is
     * the last one
     */
    private final Deque<ICommandArgument> consumed;

    private ArgConsumer(
            CommandManager manager,
            Deque<ICommandArgument> args,
            Deque<ICommandArgument> consumed) {
        this.manager = manager;
        this.context = this.new Context();
        this.args = new ArrayList<>(args);
        this.consumed = new ArrayDeque<>(consumed);
    }

    public ArgConsumer(CommandManager manager, List<ICommandArgument> args) {
        this(manager, new ArrayDeque<>(args), new ArrayDeque<>());
    }

    @Override
    public List<ICommandArgument> getArgs() {
        return this.args;
    }

    @Override
    public Deque<ICommandArgument> getConsumed() {
        return this.consumed;
    }

    @Override
    public boolean has(int num) {
        return args.size() >= num;
    }

    @Override
    public boolean hasAny() {
        return has(1);
    }

    @Override
    public boolean hasAtMost(int num) {
        return args.size() <= num;
    }

    @Override
    public boolean hasAtMostOne() {
        return hasAtMost(1);
    }

    @Override
    public boolean hasExactly(int num) {
        return args.size() == num;
    }

    @Override
    public boolean hasExactlyOne() {
        return hasExactly(1);
    }

    @Override
    public ICommandArgument peek(int index) throws CommandException {
        requireMin(index + 1);
        return args.get(index);
    }

    @Override
    public ICommandArgument peek() throws CommandException {
        return peek(0);
    }

    @Override
    public boolean is(Class<?> type, int index) throws CommandException {
        return peek(index).is(type);
    }

    @Override
    public boolean is(Class<?> type) throws CommandException {
        return is(type, 0);
    }

    @Override
    public String peekString(int index) throws CommandException {
        return peek(index).getValue();
    }

    @Override
    public String peekString() throws CommandException {
        return peekString(0);
    }

    @Override
    public <E extends Enum<?>> E peekEnum(Class<E> enumClass, int index) throws CommandException {
        return peek(index).getEnum(enumClass);
    }

    @Override
    public <E extends Enum<?>> E peekEnum(Class<E> enumClass) throws CommandException {
        return peekEnum(enumClass, 0);
    }

    @Override
    public <E extends Enum<?>> E peekEnumOrNull(Class<E> enumClass, int index)
            throws CommandException {
        try {
            return peekEnum(enumClass, index);
        } catch (CommandException.InvalidArgument.InvalidType e) {
            return null;
        }
    }

    @Override
    public <E extends Enum<?>> E peekEnumOrNull(Class<E> enumClass) throws CommandException {
        return peekEnumOrNull(enumClass, 0);
    }

    @Override
    public <T> T peekAs(Class<T> type, int index) throws CommandException {
        return peek(index).getAs(type);
    }

    @Override
    public <T> T peekAs(Class<T> type) throws CommandException {
        return peekAs(type, 0);
    }

    @Override
    public <T> T peekAsOrDefault(Class<T> type, T def, int index) throws CommandException {
        try {
            return peekAs(type, index);
        } catch (CommandException.InvalidArgument.InvalidType e) {
            return def;
        }
    }

    @Override
    public <T> T peekAsOrDefault(Class<T> type, T def) throws CommandException {
        return peekAsOrDefault(type, def, 0);
    }

    @Override
    public <T> T peekAsOrNull(Class<T> type, int index) throws CommandException {
        return peekAsOrDefault(type, null, index);
    }

    @Override
    public <T> T peekAsOrNull(Class<T> type) throws CommandException {
        return peekAsOrNull(type, 0);
    }

    @Override
    public <T> T peekDatatype(IDatatypeFor<T> datatype) throws CommandException {
        return copy().getDatatypeFor(datatype);
    }

    @Override
    public <T, O> T peekDatatype(IDatatypePost<T, O> datatype) throws CommandException {
        return this.peekDatatype(datatype, null);
    }

    @Override
    public <T, O> T peekDatatype(IDatatypePost<T, O> datatype, O original) throws CommandException {
        return copy().getDatatypePost(datatype, original);
    }

    @Override
    public <T> T peekDatatypeOrNull(IDatatypeFor<T> datatype) {
        return copy().getDatatypeForOrNull(datatype);
    }

    @Override
    public <T, O> T peekDatatypeOrNull(IDatatypePost<T, O> datatype) {
        return copy().getDatatypePostOrNull(datatype, null);
    }

    @Override
    public <T, O, D extends IDatatypePost<T, O>> T peekDatatypePost(D datatype, O original)
            throws CommandException {
        return copy().getDatatypePost(datatype, original);
    }

    @Override
    public <T, O, D extends IDatatypePost<T, O>> T peekDatatypePostOrDefault(
            D datatype, O original, T def) {
        return copy().getDatatypePostOrDefault(datatype, original, def);
    }

    @Override
    public <T, O, D extends IDatatypePost<T, O>> T peekDatatypePostOrNull(D datatype, O original) {
        return peekDatatypePostOrDefault(datatype, original, null);
    }

    @Override
    public <T, D extends IDatatypeFor<T>> T peekDatatypeFor(Class<D> datatype) {
        return copy().peekDatatypeFor(datatype);
    }

    @Override
    public <T, D extends IDatatypeFor<T>> T peekDatatypeForOrDefault(Class<D> datatype, T def) {
        return copy().peekDatatypeForOrDefault(datatype, def);
    }

    @Override
    public <T, D extends IDatatypeFor<T>> T peekDatatypeForOrNull(Class<D> datatype) {
        return peekDatatypeForOrDefault(datatype, null);
    }

    @Override
    public ICommandArgument get() throws CommandException {
        requireMin(1);
        ICommandArgument arg = args.remove(0);
        consumed.add(arg);
        return arg;
    }

    @Override
    public String getString() throws CommandException {
        return get().getValue();
    }

    @Override
    public <E extends Enum<?>> E getEnum(Class<E> enumClass) throws CommandException {
        return get().getEnum(enumClass);
    }

    @Override
    public <E extends Enum<?>> E getEnumOrDefault(Class<E> enumClass, E def)
            throws CommandException {
        try {
            peekEnum(enumClass);
            return getEnum(enumClass);
        } catch (CommandException.InvalidArgument.InvalidType e) {
            return def;
        }
    }

    @Override
    public <E extends Enum<?>> E getEnumOrNull(Class<E> enumClass) throws CommandException {
        return getEnumOrDefault(enumClass, null);
    }

    @Override
    public <T> T getAs(Class<T> type) throws CommandException {
        return get().getAs(type);
    }

    @Override
    public <T> T getAsOrDefault(Class<T> type, T def) throws CommandException {
        try {
            T val = peek().getAs(type);
            get();
            return val;
        } catch (CommandException.InvalidArgument.InvalidType e) {
            return def;
        }
    }

    @Override
    public <T> T getAsOrNull(Class<T> type) throws CommandException {
        return getAsOrDefault(type, null);
    }

    @Override
    public <T, O, D extends IDatatypePost<T, O>> T getDatatypePost(D datatype, O original)
            throws CommandException {
        try {
            return datatype.apply(this.context, original);
        } catch (Exception e) {
            if (Agent.settings().verboseCommandExceptions.value) {
                e.printStackTrace();
            }
            throw new CommandException.InvalidArgument.InvalidType(
                    hasAny() ? peek() : consumed(), datatype.getClass().getSimpleName(), e);
        }
    }

    @Override
    public <T, O, D extends IDatatypePost<T, O>> T getDatatypePostOrDefault(
            D datatype, O original, T _default) {
        final List<ICommandArgument> argsSnapshot = new ArrayList<>(this.args);
        final List<ICommandArgument> consumedSnapshot = new ArrayList<>(this.consumed);
        try {
            return this.getDatatypePost(datatype, original);
        } catch (Exception e) {
            this.args.clear();
            this.args.addAll(argsSnapshot);
            this.consumed.clear();
            this.consumed.addAll(consumedSnapshot);
            return _default;
        }
    }

    @Override
    public <T, O, D extends IDatatypePost<T, O>> T getDatatypePostOrNull(D datatype, O original) {
        return this.getDatatypePostOrDefault(datatype, original, null);
    }

    @Override
    public <T, D extends IDatatypeFor<T>> T getDatatypeFor(D datatype) throws CommandException {
        try {
            return datatype.get(this.context);
        } catch (Exception e) {
            if (Agent.settings().verboseCommandExceptions.value) {
                e.printStackTrace();
            }
            throw new CommandException.InvalidArgument.InvalidType(
                    hasAny() ? peek() : consumed(), datatype.getClass().getSimpleName(), e);
        }
    }

    @Override
    public <T, D extends IDatatypeFor<T>> T getDatatypeForOrDefault(D datatype, T def) {
        final List<ICommandArgument> argsSnapshot = new ArrayList<>(this.args);
        final List<ICommandArgument> consumedSnapshot = new ArrayList<>(this.consumed);
        try {
            return this.getDatatypeFor(datatype);
        } catch (Exception e) {
            this.args.clear();
            this.args.addAll(argsSnapshot);
            this.consumed.clear();
            this.consumed.addAll(consumedSnapshot);
            return def;
        }
    }

    @Override
    public <T, D extends IDatatypeFor<T>> T getDatatypeForOrNull(D datatype) {
        return this.getDatatypeForOrDefault(datatype, null);
    }

    @Override
    public <T extends IDatatype> Stream<String> tabCompleteDatatype(T datatype) {
        try {
            return datatype.tabComplete(this.context);
        } catch (CommandException ignored) {
            // NOP
        } catch (Exception e) {
            log.atError()
                    .setCause(e)
                    .addKeyValue("datatype", datatype.getClass().getSimpleName())
                    .log("Tab completion failed");
        }
        return Stream.empty();
    }

    @Override
    public String rawRest() {
        return !args.isEmpty() ? args.get(0).getRawRest() : "";
    }

    @Override
    public void requireMin(int min) throws CommandException {
        if (args.size() < min) {
            throw new CommandException.NotEnoughArguments(min + consumed.size());
        }
    }

    @Override
    public void requireMax(int max) throws CommandException {
        if (args.size() > max) {
            throw new CommandException.TooManyArguments(max + consumed.size());
        }
    }

    @Override
    public void requireExactly(int args) throws CommandException {
        requireMin(args);
        requireMax(args);
    }

    @Override
    public boolean hasConsumed() {
        return !consumed.isEmpty();
    }

    @Override
    public ICommandArgument consumed() {
        return !consumed.isEmpty() ? consumed.getLast() : CommandArguments.unknown();
    }

    @Override
    public String consumedString() {
        return consumed().getValue();
    }

    @Override
    public ArgConsumer copy() {
        return new ArgConsumer(manager, new ArrayDeque<>(args), consumed);
    }

    /**
     * Implementation of {@link IDatatypeContext} which adapts to the parent {@link IArgConsumer}}
     */
    private final class Context implements IDatatypeContext {

        @Override
        public Agent getMaestro() {
            return ArgConsumer.this.manager.getMaestro();
        }

        @Override
        public ArgConsumer getConsumer() {
            return ArgConsumer.this;
        }
    }
}
