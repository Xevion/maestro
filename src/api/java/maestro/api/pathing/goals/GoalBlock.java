package maestro.api.pathing.goals;

import maestro.api.utils.BetterBlockPos;
import maestro.api.utils.SettingsUtil;
import maestro.api.utils.interfaces.IGoalRenderPos;
import net.minecraft.core.BlockPos;

/** A specific BlockPos goal */
public class GoalBlock implements Goal, IGoalRenderPos {

    /** The X block position of this goal */
    public final int x;

    /** The Y block position of this goal */
    public final int y;

    /** The Z block position of this goal */
    public final int z;

    public GoalBlock(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ());
    }

    public GoalBlock(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return x == this.x && y == this.y && z == this.z;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        int xDiff = x - this.x;
        int yDiff = y - this.y;
        int zDiff = z - this.z;
        return calculate(xDiff, yDiff, zDiff);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GoalBlock goal = (GoalBlock) o;
        return x == goal.x && y == goal.y && z == goal.z;
    }

    @Override
    public int hashCode() {
        return (int) BetterBlockPos.longHash(x, y, z) * 905165533;
    }

    @Override
    public String toString() {
        return String.format(
                "GoalBlock{x=%s,y=%s,z=%s}",
                SettingsUtil.maybeCensor(x),
                SettingsUtil.maybeCensor(y),
                SettingsUtil.maybeCensor(z));
    }

    /**
     * @return The position of this goal as a {@link BlockPos}
     */
    @Override
    public BlockPos getGoalPos() {
        return new BlockPos(x, y, z);
    }

    public static double calculate(double xDiff, int yDiff, double zDiff) {
        double heuristic = 0;

        // Vertical heuristic: Use swimming cost (3.5) as minimum estimate
        // This provides a better heuristic for underwater goals than terrestrial movement costs
        // TODO: Ideally this would detect if we're actually in water, but using the minimum
        // of swimming vs terrestrial costs ensures the heuristic is admissible (never
        // overestimates)
        double verticalHeuristic = GoalYLevel.calculate(0, yDiff);
        double swimmingVerticalHeuristic = Math.abs(yDiff) * 3.5; // SWIM_UP/DOWN cost
        heuristic += Math.min(verticalHeuristic, swimmingVerticalHeuristic);

        // use the pythagorean and manhattan mixture from GoalXZ
        heuristic += GoalXZ.calculate(xDiff, zDiff);
        return heuristic;
    }
}
