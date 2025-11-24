package maestro.api.command.datatypes;

import java.util.Locale;
import java.util.stream.Stream;
import maestro.api.command.exception.CommandException;
import maestro.api.command.helpers.TabCompleteHelper;
import net.minecraft.core.Direction;

public enum ForDirection implements IDatatypeFor<Direction> {
    INSTANCE;

    @Override
    public Direction get(IDatatypeContext ctx) throws CommandException {
        return Direction.valueOf(ctx.getConsumer().getString().toUpperCase(Locale.US));
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        return new TabCompleteHelper()
                        .append(
                                Stream.of(Direction.values())
                                        .map(Direction::getName)
                                        .map(String::toLowerCase))
                        .filterPrefix(ctx.getConsumer().getString())
                        .stream();
    }
}
