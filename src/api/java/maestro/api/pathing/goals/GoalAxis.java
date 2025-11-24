package maestro.api.pathing.goals;

import maestro.api.MaestroAPI;

public class GoalAxis implements Goal {

    private static final double SQRT_2_OVER_2 = Math.sqrt(2) / 2;

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return y == MaestroAPI.getSettings().axisHeight.value
                && (x == 0 || z == 0 || Math.abs(x) == Math.abs(z));
    }

    @Override
    public double heuristic(int x0, int y, int z0) {
        int x = Math.abs(x0);
        int z = Math.abs(z0);

        int shrt = Math.min(x, z);
        int lng = Math.max(x, z);
        int diff = lng - shrt;

        double flatAxisDistance = Math.min(x, Math.min(z, diff * SQRT_2_OVER_2));

        return flatAxisDistance * MaestroAPI.getSettings().costHeuristic.value
                + GoalYLevel.calculate(MaestroAPI.getSettings().axisHeight.value, y);
    }

    @Override
    public boolean equals(Object o) {
        return o.getClass() == GoalAxis.class;
    }

    @Override
    public int hashCode() {
        return 201385781;
    }

    @Override
    public String toString() {
        return "GoalAxis";
    }
}
