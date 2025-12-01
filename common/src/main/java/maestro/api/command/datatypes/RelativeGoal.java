package maestro.api.command.datatypes;

import java.util.stream.Stream;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.goals.GoalBlock;
import maestro.api.pathing.goals.GoalXZ;
import maestro.api.pathing.goals.GoalYLevel;
import maestro.api.utils.PackedBlockPos;

public enum RelativeGoal implements IDatatypePost<Goal, PackedBlockPos> {
    INSTANCE;

    @Override
    public Goal apply(IDatatypeContext ctx, PackedBlockPos origin) {
        if (origin == null) {
            origin = PackedBlockPos.Companion.getORIGIN();
        }

        final IArgConsumer consumer = ctx.getConsumer();

        GoalBlock goalBlock = consumer.peekDatatypePostOrNull(RelativeGoalBlock.INSTANCE, origin);
        if (goalBlock != null) {
            return goalBlock;
        }

        GoalXZ goalXZ = consumer.peekDatatypePostOrNull(RelativeGoalXZ.INSTANCE, origin);
        if (goalXZ != null) {
            return goalXZ;
        }

        GoalYLevel goalYLevel =
                consumer.peekDatatypePostOrNull(RelativeGoalYLevel.INSTANCE, origin);
        if (goalYLevel != null) {
            return goalYLevel;
        }

        // when the user doesn't input anything, default to the origin
        return new GoalBlock(origin.toBlockPos());
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) {
        return ctx.getConsumer().tabCompleteDatatype(RelativeCoordinate.INSTANCE);
    }
}
