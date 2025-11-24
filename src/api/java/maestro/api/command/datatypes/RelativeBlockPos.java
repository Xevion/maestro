package maestro.api.command.datatypes;

import java.util.stream.Stream;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.utils.BetterBlockPos;

public enum RelativeBlockPos implements IDatatypePost<BetterBlockPos, BetterBlockPos> {
    INSTANCE;

    @Override
    public BetterBlockPos apply(IDatatypeContext ctx, BetterBlockPos origin)
            throws CommandException {
        if (origin == null) {
            origin = BetterBlockPos.ORIGIN;
        }

        final IArgConsumer consumer = ctx.getConsumer();
        return new BetterBlockPos(
                consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.x),
                consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.y),
                consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.z));
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        final IArgConsumer consumer = ctx.getConsumer();
        if (consumer.hasAny() && !consumer.has(4)) {
            while (consumer.has(2)) {
                if (consumer.peekDatatypeOrNull(RelativeCoordinate.INSTANCE) == null) {
                    break;
                }
                consumer.get();
            }
            return consumer.tabCompleteDatatype(RelativeCoordinate.INSTANCE);
        }
        return Stream.empty();
    }
}
