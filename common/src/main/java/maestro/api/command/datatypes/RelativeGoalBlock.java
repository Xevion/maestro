package maestro.api.command.datatypes;

import java.util.stream.Stream;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.pathing.goals.GoalBlock;
import maestro.api.utils.PackedBlockPos;
import net.minecraft.util.Mth;

public enum RelativeGoalBlock implements IDatatypePost<GoalBlock, PackedBlockPos> {
    INSTANCE;

    @Override
    public GoalBlock apply(IDatatypeContext ctx, PackedBlockPos origin) throws CommandException {
        if (origin == null) {
            origin = PackedBlockPos.Companion.getORIGIN();
        }

        final IArgConsumer consumer = ctx.getConsumer();
        return new GoalBlock(
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
    public Stream<String> tabComplete(IDatatypeContext ctx) {
        final IArgConsumer consumer = ctx.getConsumer();
        if (consumer.hasAtMost(3)) {
            return consumer.tabCompleteDatatype(RelativeCoordinate.INSTANCE);
        }
        return Stream.empty();
    }
}
