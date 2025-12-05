package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.cache.Waypoint;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.datatypes.ForWaypoints;
import maestro.api.command.exception.CommandException;
import maestro.api.utils.PackedBlockPos;

public class FarmCommand extends Command {

    public FarmCommand(Agent maestro) {
        super(maestro, "farm");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);
        int range = 0;
        PackedBlockPos origin = null;
        // range
        if (args.has(1)) {
            range = args.getAs(Integer.class);
        }
        // waypoint
        if (args.has(1)) {
            Waypoint[] waypoints = args.getDatatypeFor(ForWaypoints.INSTANCE);
            Waypoint waypoint =
                    switch (waypoints.length) {
                        case 0 -> throw new CommandException.InvalidState("No waypoints found");
                        case 1 -> waypoints[0];
                        default ->
                                throw new CommandException.InvalidState(
                                        "Multiple waypoints were found");
                    };
            origin = waypoint.getLocation();
        }

        maestro.getFarmTask().farm(range, origin != null ? origin.toBlockPos() : null);
        log.atInfo().log("Farming started");
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
