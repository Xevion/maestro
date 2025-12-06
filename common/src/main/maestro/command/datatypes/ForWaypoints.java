package maestro.command.datatypes;

import java.util.Comparator;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.cache.Waypoint;
import maestro.cache.WaypointCollection;
import maestro.command.exception.CommandException;
import maestro.command.helpers.TabCompleteHelper;

public enum ForWaypoints implements IDatatypeFor<Waypoint[]> {
    INSTANCE;

    @Override
    public Waypoint[] get(IDatatypeContext ctx) throws CommandException {
        final String input = ctx.getConsumer().getString();
        final Waypoint.Tag tag = Waypoint.Tag.getByName(input);

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
                        .prepend(Waypoint.Tag.getAllNames())
                        .filterPrefix(ctx.getConsumer().getString())
                        .stream();
    }

    public static WaypointCollection waypoints(Agent maestro) {
        return maestro.getWorldProvider().getCurrentWorld().getWaypoints();
    }

    public static Waypoint[] getWaypoints(Agent maestro) {
        return waypoints(maestro).getAllWaypoints().stream()
                .sorted(Comparator.comparingLong(Waypoint::getCreationTimestamp).reversed())
                .toArray(Waypoint[]::new);
    }

    public static String[] getWaypointNames(Agent maestro) {
        return Stream.of(getWaypoints(maestro))
                .map(Waypoint::getName)
                .filter(name -> !name.isEmpty())
                .toArray(String[]::new);
    }

    public static Waypoint[] getWaypointsByTag(Agent maestro, Waypoint.Tag tag) {
        return waypoints(maestro).getByTag(tag).stream()
                .sorted(Comparator.comparingLong(Waypoint::getCreationTimestamp).reversed())
                .toArray(Waypoint[]::new);
    }

    public static Waypoint[] getWaypointsByName(Agent maestro, String name) {
        return Stream.of(getWaypoints(maestro))
                .filter(waypoint -> waypoint.getName().equalsIgnoreCase(name))
                .toArray(Waypoint[]::new);
    }
}
