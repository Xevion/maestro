package maestro.api.command.datatypes;

import java.util.stream.Stream;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.pathing.goals.GoalBlock;
import maestro.api.utils.BetterBlockPos;
import net.minecraft.util.Mth;

public enum RelativeGoalBlock implements IDatatypePost<GoalBlock, BetterBlockPos> {
    INSTANCE;

    @Override
    public GoalBlock apply(IDatatypeContext ctx, BetterBlockPos origin) throws CommandException {
        if (origin == null) {
            origin = BetterBlockPos.ORIGIN;
        }

        final IArgConsumer consumer = ctx.getConsumer();
        return new GoalBlock(
                Mth.floor(consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.x)),
                Mth.floor(consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.y)),
                Mth.floor(
                        consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.z)));
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
