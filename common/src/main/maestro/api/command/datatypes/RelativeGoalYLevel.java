package maestro.api.command.datatypes;

import java.util.stream.Stream;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.pathing.goals.GoalYLevel;
import maestro.api.utils.PackedBlockPos;
import net.minecraft.util.Mth;

public enum RelativeGoalYLevel implements IDatatypePost<GoalYLevel, PackedBlockPos> {
    INSTANCE;

    @Override
    public GoalYLevel apply(IDatatypeContext ctx, PackedBlockPos origin) throws CommandException {
        if (origin == null) {
            origin = PackedBlockPos.Companion.getORIGIN();
        }

        return new GoalYLevel(
                Mth.floor(
                        ctx.getConsumer()
                                .getDatatypePost(
                                        RelativeCoordinate.INSTANCE, (double) origin.getY())));
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) {
        final IArgConsumer consumer = ctx.getConsumer();
        if (consumer.hasAtMost(1)) {
            return consumer.tabCompleteDatatype(RelativeCoordinate.INSTANCE);
        }
        return Stream.empty();
    }
}
