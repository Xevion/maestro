package maestro.api.command.datatypes;

import java.util.stream.Stream;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.utils.PackedBlockPos;
import net.minecraft.util.Mth;

public enum RelativeBlockPos implements IDatatypePost<PackedBlockPos, PackedBlockPos> {
    INSTANCE;

    @Override
    public PackedBlockPos apply(IDatatypeContext ctx, PackedBlockPos origin)
            throws CommandException {
        if (origin == null) {
            origin = PackedBlockPos.Companion.getORIGIN();
        }

        final IArgConsumer consumer = ctx.getConsumer();
        return new PackedBlockPos(
                Mth.floor(
                        consumer.getDatatypePost(
                                RelativeCoordinate.INSTANCE, (double) origin.getX())),
                Mth.floor(
                        consumer.getDatatypePost(
                                RelativeCoordinate.INSTANCE, (double) origin.getY())),
                Mth.floor(
                        consumer.getDatatypePost(
                                RelativeCoordinate.INSTANCE, (double) origin.getZ())));
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
