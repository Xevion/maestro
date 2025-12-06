package maestro.command.datatypes;

import java.util.stream.Stream;
import maestro.command.argument.IArgConsumer;
import maestro.pathing.goals.Goal;
import maestro.pathing.goals.GoalBlock;
import maestro.pathing.goals.GoalXZ;
import maestro.pathing.goals.GoalYLevel;
import maestro.utils.PackedBlockPos;

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
