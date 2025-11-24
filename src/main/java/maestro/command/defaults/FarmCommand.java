package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.cache.IWaypoint;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.datatypes.ForWaypoints;
import maestro.api.command.exception.CommandException;
import maestro.api.command.exception.CommandInvalidStateException;
import maestro.api.utils.BetterBlockPos;

public class FarmCommand extends Command {

    public FarmCommand(IAgent maestro) {
        super(maestro, "farm");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);
        int range = 0;
        BetterBlockPos origin = null;
        // range
        if (args.has(1)) {
            range = args.getAs(Integer.class);
        }
        // waypoint
        if (args.has(1)) {
            IWaypoint[] waypoints = args.getDatatypeFor(ForWaypoints.INSTANCE);
            IWaypoint waypoint =
                    switch (waypoints.length) {
                        case 0 -> throw new CommandInvalidStateException("No waypoints found");
                        case 1 -> waypoints[0];
                        default ->
                                throw new CommandInvalidStateException(
                                        "Multiple waypoints were found");
                    };
            origin = waypoint.getLocation();
        }

        maestro.getFarmProcess().farm(range, origin);
        logDirect("Farming");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Farm nearby crops";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The farm command starts farming nearby plants. It harvests mature crops and plants"
                        + " new ones.",
                "",
                "Usage:",
                "> farm - farms every crop it can find.",
                "> farm <range> - farm crops within range from the starting position.",
                "> farm <range> <waypoint> - farm crops within range from waypoint.");
    }
}
