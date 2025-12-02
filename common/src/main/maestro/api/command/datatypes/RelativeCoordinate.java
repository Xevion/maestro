package maestro.api.command.datatypes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;

public enum RelativeCoordinate implements IDatatypePost<Double, Double> {
    INSTANCE;
    private static final String ScalesAliasRegex = "[kKmM]";
    private static final Pattern PATTERN =
            Pattern.compile(
                    "^(~?)([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(" + ScalesAliasRegex + "?)|)$");

    @Override
    public Double apply(IDatatypeContext ctx, Double origin) throws CommandException {
        if (origin == null) {
            origin = 0.0D;
        }

        Matcher matcher = PATTERN.matcher(ctx.getConsumer().getString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("pattern doesn't match");
        }

        boolean isRelative = !matcher.group(1).isEmpty();

        double offset =
                matcher.group(2).isEmpty()
                        ? 0
                        : Double.parseDouble(matcher.group(2).replaceAll(ScalesAliasRegex, ""));

        if (matcher.group(2).toLowerCase(java.util.Locale.ROOT).contains("k")) {
            offset *= 1000;
        }
        if (matcher.group(2).toLowerCase(java.util.Locale.ROOT).contains("m")) {
            offset *= 1000000;
        }

        if (isRelative) {
            return origin + offset;
        }
        return offset;
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        final IArgConsumer consumer = ctx.getConsumer();
        if (!consumer.has(2) && consumer.getString().matches("^(~|$)")) {
            return Stream.of("~");
        }
        return Stream.empty();
    }
}
