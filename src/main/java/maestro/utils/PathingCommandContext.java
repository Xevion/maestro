package maestro.utils;

import maestro.api.pathing.goals.Goal;
import maestro.api.process.PathingCommand;
import maestro.api.process.PathingCommandType;
import maestro.pathing.movement.CalculationContext;

public class PathingCommandContext extends PathingCommand {

    public final CalculationContext desiredCalcContext;

    public PathingCommandContext(
            Goal goal, PathingCommandType commandType, CalculationContext context) {
        super(goal, commandType);
        this.desiredCalcContext = context;
    }
}
