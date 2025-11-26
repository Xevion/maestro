package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.behavior.IPathingBehavior;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.command.exception.CommandInvalidStateException;
import maestro.api.pathing.calc.IPathingControlManager;
import maestro.api.process.IMaestroProcess;

public class ETACommand extends Command {

    public ETACommand(IAgent maestro) {
        super(maestro, "eta");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        IPathingControlManager pathingControlManager = maestro.getPathingControlManager();
        IMaestroProcess process = pathingControlManager.mostRecentInControl().orElse(null);
        if (process == null) {
            throw new CommandInvalidStateException("No process in control");
        }
        IPathingBehavior pathingBehavior = maestro.getPathingBehavior();

        double ticksRemainingInSegment =
                pathingBehavior.ticksRemainingInSegment().orElse(Double.NaN);
        double ticksRemainingInGoal = pathingBehavior.estimatedTicksToGoal().orElse(Double.NaN);

        log.atInfo()
                .addKeyValue(
                        "segment_eta_sec",
                        String.format(
                                "%.1f",
                                ticksRemainingInSegment
                                        / 20)) // we just assume tps is 20, it isn't worth the
                // effort
                // that is needed to calculate it exactly
                .addKeyValue("segment_eta_ticks", String.format("%.0f", ticksRemainingInSegment))
                .addKeyValue("goal_eta_sec", String.format("%.1f", ticksRemainingInGoal / 20))
                .addKeyValue("goal_eta_ticks", String.format("%.0f", ticksRemainingInGoal))
                .log("Estimated time to arrival");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "View the current ETA";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The ETA command provides information about the estimated time until the next"
                        + " segment.",
                "and the goal",
                "",
                "Be aware that the ETA to your goal is really unprecise",
                "",
                "Usage:",
                "> eta - View ETA, if present");
    }
}
