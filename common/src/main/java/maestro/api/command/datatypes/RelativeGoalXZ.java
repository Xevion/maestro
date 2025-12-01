package maestro.api.command.datatypes;

import java.util.stream.Stream;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.pathing.goals.GoalXZ;
import maestro.api.utils.PackedBlockPos;
import net.minecraft.util.Mth;

public enum RelativeGoalXZ implements IDatatypePost<GoalXZ, PackedBlockPos> {
    INSTANCE;

    @Override
    public GoalXZ apply(IDatatypeContext ctx, PackedBlockPos origin) throws CommandException {
        if (origin == null) {
            origin = PackedBlockPos.Companion.getORIGIN();
        }

        final IArgConsumer consumer = ctx.getConsumer();
        return new GoalXZ(
                Mth.floor(
                        consumer.getDatatypePost(
                                RelativeCoordinate.INSTANCE, (double) origin.getX())),
                Mth.floor(
                        consumer.getDatatypePost(
                                RelativeCoordinate.INSTANCE, (double) origin.getZ())));
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) {
        final IArgConsumer consumer = ctx.getConsumer();
        if (consumer.hasAtMost(2)) {
            return consumer.tabCompleteDatatype(RelativeCoordinate.INSTANCE);
        }
        return Stream.empty();
    }
}
