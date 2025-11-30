package maestro.api.command.datatypes;

import java.util.Comparator;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.cache.IWaypoint;
import maestro.api.cache.IWaypointCollection;
import maestro.api.command.exception.CommandException;
import maestro.api.command.helpers.TabCompleteHelper;

public enum ForWaypoints implements IDatatypeFor<IWaypoint[]> {
    INSTANCE;

    @Override
    public IWaypoint[] get(IDatatypeContext ctx) throws CommandException {
        final String input = ctx.getConsumer().getString();
        final IWaypoint.Tag tag = IWaypoint.Tag.getByName(input);

        // If the input doesn't resolve to a valid tag, resolve by name
        return tag == null
                ? getWaypointsByName(ctx.getMaestro(), input)
                : getWaypointsByTag(ctx.getMaestro(), tag);
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        return new TabCompleteHelper()
                        .append(getWaypointNames(ctx.getMaestro()))
                        .sortAlphabetically()
                        .prepend(IWaypoint.Tag.getAllNames())
                        .filterPrefix(ctx.getConsumer().getString())
                        .stream();
    }

    public static IWaypointCollection waypoints(IAgent maestro) {
        return maestro.getWorldProvider().getCurrentWorld().getWaypoints();
    }

    public static IWaypoint[] getWaypoints(IAgent maestro) {
        return waypoints(maestro).getAllWaypoints().stream()
                .sorted(Comparator.comparingLong(IWaypoint::getCreationTimestamp).reversed())
                .toArray(IWaypoint[]::new);
    }

    public static String[] getWaypointNames(IAgent maestro) {
        return Stream.of(getWaypoints(maestro))
                .map(IWaypoint::getName)
                .filter(name -> !name.isEmpty())
                .toArray(String[]::new);
    }

    public static IWaypoint[] getWaypointsByTag(IAgent maestro, IWaypoint.Tag tag) {
        return waypoints(maestro).getByTag(tag).stream()
                .sorted(Comparator.comparingLong(IWaypoint::getCreationTimestamp).reversed())
                .toArray(IWaypoint[]::new);
    }

    public static IWaypoint[] getWaypointsByName(IAgent maestro, String name) {
        return Stream.of(getWaypoints(maestro))
                .filter(waypoint -> waypoint.getName().equalsIgnoreCase(name))
                .toArray(IWaypoint[]::new);
    }
}
