package maestro.command.defaults;

import static maestro.api.AgentAPI.FORCE_COMMAND_PREFIX;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.AgentAPI;
import maestro.api.cache.Waypoint;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.datatypes.ForWaypoints;
import maestro.api.command.datatypes.RelativeBlockPos;
import maestro.api.command.exception.CommandException;
import maestro.api.command.helpers.Paginator;
import maestro.api.command.helpers.TabCompleteHelper;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.goals.GoalBlock;
import maestro.api.utils.PackedBlockPos;
import maestro.cache.WorldData;
import maestro.gui.chat.ChatMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class WaypointsCommand extends Command {

    private Map<WorldData, List<Waypoint>> deletedWaypoints = new HashMap<>();

    public WaypointsCommand(Agent maestro) {
        super(maestro, "waypoints", "waypoint", "wp");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Action action = args.hasAny() ? Action.getByName(args.getString()) : Action.LIST;
        if (action == null) {
            throw new CommandException.InvalidArgument.InvalidType(args.consumed(), "an action");
        }
        BiFunction<Waypoint, Action, Component> toComponent =
                (waypoint, _action) -> {
                    MutableComponent component = Component.literal("");
                    MutableComponent tagComponent =
                            Component.literal(waypoint.getTag().name() + " ");
                    tagComponent.setStyle(tagComponent.getStyle().withColor(ChatFormatting.GRAY));
                    String name = waypoint.getName();
                    MutableComponent nameComponent =
                            Component.literal(!name.isEmpty() ? name : "<empty>");
                    nameComponent.setStyle(
                            nameComponent
                                    .getStyle()
                                    .withColor(
                                            !name.isEmpty()
                                                    ? ChatFormatting.GRAY
                                                    : ChatFormatting.DARK_GRAY));
                    MutableComponent timestamp =
                            Component.literal(
                                    " @ " + Instant.ofEpochMilli(waypoint.getCreationTimestamp()));
                    timestamp.setStyle(timestamp.getStyle().withColor(ChatFormatting.DARK_GRAY));
                    component.append(tagComponent);
                    component.append(nameComponent);
                    component.append(timestamp);
                    component.setStyle(
                            component
                                    .getStyle()
                                    .withHoverEvent(
                                            new HoverEvent(
                                                    HoverEvent.Action.SHOW_TEXT,
                                                    Component.literal("Click to select")))
                                    .withClickEvent(
                                            new ClickEvent(
                                                    ClickEvent.Action.RUN_COMMAND,
                                                    String.format(
                                                            "%s%s %s %s @ %d",
                                                            FORCE_COMMAND_PREFIX,
                                                            label,
                                                            _action.names.get(0),
                                                            waypoint.getTag().getName(),
                                                            waypoint.getCreationTimestamp()))));
                    return component;
                };
        Function<Waypoint, Component> transform =
                waypoint ->
                        toComponent.apply(waypoint, action == Action.LIST ? Action.INFO : action);
        if (action == Action.LIST) {
            Waypoint.Tag tag = args.hasAny() ? Waypoint.Tag.getByName(args.peekString()) : null;
            if (tag != null) {
                args.get();
            }
            Waypoint[] waypoints =
                    tag != null
                            ? ForWaypoints.getWaypointsByTag(this.maestro, tag)
                            : ForWaypoints.getWaypoints(this.maestro);
            if (waypoints.length > 0) {
                args.requireMax(1);
                Paginator.paginate(
                        args,
                        waypoints,
                        () ->
                                log.atInfo().log(
                                        tag != null
                                                ? String.format(
                                                        "All waypoints by tag %s:", tag.name())
                                                : "All waypoints:"),
                        transform,
                        String.format(
                                "%s%s %s%s",
                                FORCE_COMMAND_PREFIX,
                                label,
                                action.names.get(0),
                                tag != null ? " " + tag.getName() : ""));
            } else {
                args.requireMax(0);
                throw new CommandException.InvalidState(
                        tag != null ? "No waypoints found by that tag" : "No waypoints found");
            }
        } else if (action == Action.SAVE) {
            Waypoint.Tag tag = args.hasAny() ? Waypoint.Tag.getByName(args.peekString()) : null;
            if (tag == null) {
                tag = Waypoint.Tag.USER;
            } else {
                args.get();
            }
            String name = (args.hasExactlyOne() || args.hasExactly(4)) ? args.getString() : "";
            PackedBlockPos pos =
                    args.hasAny()
                            ? args.getDatatypePost(RelativeBlockPos.INSTANCE, ctx.playerFeet())
                            : ctx.playerFeet();
            args.requireMax(0);
            Waypoint waypoint = new Waypoint(name, tag, pos);
            ForWaypoints.waypoints(this.maestro).addWaypoint(waypoint);
            MutableComponent component = Component.literal("Waypoint added: ");
            component.setStyle(component.getStyle().withColor(ChatFormatting.GRAY));
            component.append(toComponent.apply(waypoint, Action.INFO));

            MutableComponent prefixed = Component.literal("");
            prefixed.append(ChatMessage.createCategoryPrefix("cmd"));
            prefixed.append(" ");
            prefixed.append(component);

            Minecraft.getInstance()
                    .execute(() -> AgentAPI.getSettings().logger.value.accept(prefixed));
        } else if (action == Action.CLEAR) {
            args.requireMax(1);
            String name = args.getString();
            Waypoint.Tag tag = Waypoint.Tag.getByName(name);
            if (tag == null) {
                throw new CommandException.InvalidState("Invalid tag, \"" + name + "\"");
            }
            Waypoint[] waypoints = ForWaypoints.getWaypointsByTag(this.maestro, tag);
            for (Waypoint waypoint : waypoints) {
                ForWaypoints.waypoints(this.maestro).removeWaypoint(waypoint);
            }
            deletedWaypoints
                    .computeIfAbsent(
                            maestro.getWorldProvider().getCurrentWorld(), k -> new ArrayList<>())
                    .addAll(Arrays.asList(waypoints));
            MutableComponent textComponent =
                    Component.literal(
                            String.format(
                                    "Cleared %d waypoints, click to restore them",
                                    waypoints.length));
            textComponent.setStyle(
                    textComponent
                            .getStyle()
                            .withClickEvent(
                                    new ClickEvent(
                                            ClickEvent.Action.RUN_COMMAND,
                                            String.format(
                                                    "%s%s restore @ %s",
                                                    FORCE_COMMAND_PREFIX,
                                                    label,
                                                    Stream.of(waypoints)
                                                            .map(
                                                                    wp ->
                                                                            Long.toString(
                                                                                    wp
                                                                                            .getCreationTimestamp()))
                                                            .collect(Collectors.joining(" "))))));

            MutableComponent prefixed = Component.literal("");
            prefixed.append(ChatMessage.createCategoryPrefix("cmd"));
            prefixed.append(" ");
            prefixed.append(textComponent);

            Minecraft.getInstance()
                    .execute(() -> AgentAPI.getSettings().logger.value.accept(prefixed));
        } else if (action == Action.RESTORE) {
            List<Waypoint> waypoints = new ArrayList<>();
            List<Waypoint> deletedWaypoints =
                    this.deletedWaypoints.getOrDefault(
                            maestro.getWorldProvider().getCurrentWorld(), Collections.emptyList());
            if (args.peekString().equals("@")) {
                args.get();
                // no args.requireMin(1) because if the user clears an empty tag there is nothing to
                // restore
                while (args.hasAny()) {
                    long timestamp = args.getAs(Long.class);
                    for (Waypoint waypoint : deletedWaypoints) {
                        if (waypoint.getCreationTimestamp() == timestamp) {
                            waypoints.add(waypoint);
                            break;
                        }
                    }
                }
            } else {
                args.requireExactly(1);
                int size = deletedWaypoints.size();
                int amount = Math.min(size, args.getAs(Integer.class));
                waypoints = new ArrayList<>(deletedWaypoints.subList(size - amount, size));
            }
            waypoints.forEach(ForWaypoints.waypoints(this.maestro)::addWaypoint);
            deletedWaypoints.removeIf(waypoints::contains);
            log.atInfo().log(String.format("Restored %d waypoints", waypoints.size()));
        } else {
            Waypoint[] waypoints = args.getDatatypeFor(ForWaypoints.INSTANCE);
            Waypoint waypoint = null;
            if (args.hasAny() && args.peekString().equals("@")) {
                args.requireExactly(2);
                args.get();
                long timestamp = args.getAs(Long.class);
                for (Waypoint iWaypoint : waypoints) {
                    if (iWaypoint.getCreationTimestamp() == timestamp) {
                        waypoint = iWaypoint;
                        break;
                    }
                }
                if (waypoint == null) {
                    throw new CommandException.InvalidState(
                            "Timestamp was specified but no waypoint was found");
                }
            } else {
                switch (waypoints.length) {
                    case 0:
                        throw new CommandException.InvalidState("No waypoints found");
                    case 1:
                        waypoint = waypoints[0];
                        break;
                    default:
                        break;
                }
            }
            if (waypoint == null) {
                args.requireMax(1);
                Paginator.paginate(
                        args,
                        waypoints,
                        () -> log.atInfo().log("Multiple waypoints were found:"),
                        transform,
                        String.format(
                                "%s%s %s %s",
                                FORCE_COMMAND_PREFIX,
                                label,
                                action.names.get(0),
                                args.consumedString()));
            } else {
                if (action == Action.INFO) {
                    MutableComponent waypointInfo = (MutableComponent) transform.apply(waypoint);
                    MutableComponent prefixed1 = Component.literal("");
                    prefixed1.append(ChatMessage.createCategoryPrefix("cmd"));
                    prefixed1.append(" ");
                    prefixed1.append(waypointInfo);
                    Minecraft.getInstance()
                            .execute(() -> AgentAPI.getSettings().logger.value.accept(prefixed1));

                    log.atInfo().log(String.format("Position: %s", waypoint.getLocation()));
                    MutableComponent deleteComponent =
                            Component.literal("Click to delete this waypoint");
                    deleteComponent.setStyle(
                            deleteComponent
                                    .getStyle()
                                    .withClickEvent(
                                            new ClickEvent(
                                                    ClickEvent.Action.RUN_COMMAND,
                                                    String.format(
                                                            "%s%s delete %s @ %d",
                                                            FORCE_COMMAND_PREFIX,
                                                            label,
                                                            waypoint.getTag().getName(),
                                                            waypoint.getCreationTimestamp()))));
                    MutableComponent goalComponent =
                            Component.literal("Click to set goal to this waypoint");
                    goalComponent.setStyle(
                            goalComponent
                                    .getStyle()
                                    .withClickEvent(
                                            new ClickEvent(
                                                    ClickEvent.Action.RUN_COMMAND,
                                                    String.format(
                                                            "%s%s goal %s @ %d",
                                                            FORCE_COMMAND_PREFIX,
                                                            label,
                                                            waypoint.getTag().getName(),
                                                            waypoint.getCreationTimestamp()))));
                    MutableComponent recreateComponent =
                            Component.literal("Click to show a command to recreate this waypoint");
                    recreateComponent.setStyle(
                            recreateComponent
                                    .getStyle()
                                    .withClickEvent(
                                            new ClickEvent(
                                                    ClickEvent.Action.SUGGEST_COMMAND,
                                                    String.format(
                                                            "%s%s save %s %s %s %s %s",
                                                            Agent.settings()
                                                                    .prefix
                                                                    .value, // This uses the normal
                                                            // prefix because it is
                                                            // run by the user.
                                                            label,
                                                            waypoint.getTag().getName(),
                                                            waypoint.getName(),
                                                            waypoint.getLocation().getX(),
                                                            waypoint.getLocation().getY(),
                                                            waypoint.getLocation().getZ()))));
                    MutableComponent backComponent =
                            Component.literal("Click to return to the waypoints list");
                    backComponent.setStyle(
                            backComponent
                                    .getStyle()
                                    .withClickEvent(
                                            new ClickEvent(
                                                    ClickEvent.Action.RUN_COMMAND,
                                                    String.format(
                                                            "%s%s list",
                                                            FORCE_COMMAND_PREFIX, label))));

                    MutableComponent prefixed2 = Component.literal("");
                    prefixed2.append(ChatMessage.createCategoryPrefix("cmd"));
                    prefixed2.append(" ");
                    prefixed2.append(deleteComponent);
                    Minecraft.getInstance()
                            .execute(() -> AgentAPI.getSettings().logger.value.accept(prefixed2));

                    MutableComponent prefixed3 = Component.literal("");
                    prefixed3.append(ChatMessage.createCategoryPrefix("cmd"));
                    prefixed3.append(" ");
                    prefixed3.append(goalComponent);
                    Minecraft.getInstance()
                            .execute(() -> AgentAPI.getSettings().logger.value.accept(prefixed3));

                    MutableComponent prefixed4 = Component.literal("");
                    prefixed4.append(ChatMessage.createCategoryPrefix("cmd"));
                    prefixed4.append(" ");
                    prefixed4.append(recreateComponent);
                    Minecraft.getInstance()
                            .execute(() -> AgentAPI.getSettings().logger.value.accept(prefixed4));

                    MutableComponent prefixed5 = Component.literal("");
                    prefixed5.append(ChatMessage.createCategoryPrefix("cmd"));
                    prefixed5.append(" ");
                    prefixed5.append(backComponent);
                    Minecraft.getInstance()
                            .execute(() -> AgentAPI.getSettings().logger.value.accept(prefixed5));
                } else if (action == Action.DELETE) {
                    ForWaypoints.waypoints(this.maestro).removeWaypoint(waypoint);
                    deletedWaypoints
                            .computeIfAbsent(
                                    maestro.getWorldProvider().getCurrentWorld(),
                                    k -> new ArrayList<>())
                            .add(waypoint);
                    MutableComponent textComponent =
                            Component.literal(
                                    "That waypoint has successfully been deleted, click to restore"
                                            + " it");
                    textComponent.setStyle(
                            textComponent
                                    .getStyle()
                                    .withClickEvent(
                                            new ClickEvent(
                                                    ClickEvent.Action.RUN_COMMAND,
                                                    String.format(
                                                            "%s%s restore @ %s",
                                                            FORCE_COMMAND_PREFIX,
                                                            label,
                                                            waypoint.getCreationTimestamp()))));

                    MutableComponent prefixed6 = Component.literal("");
                    prefixed6.append(ChatMessage.createCategoryPrefix("cmd"));
                    prefixed6.append(" ");
                    prefixed6.append(textComponent);
                    Minecraft.getInstance()
                            .execute(() -> AgentAPI.getSettings().logger.value.accept(prefixed6));
                } else if (action == Action.GOAL) {
                    Goal goal = new GoalBlock(waypoint.getLocation().toBlockPos());
                    maestro.getCustomGoalTask().setGoal(goal);
                    log.atInfo().log(String.format("Goal: %s", goal));
                } else if (action == Action.GOTO) {
                    Goal goal = new GoalBlock(waypoint.getLocation().toBlockPos());
                    maestro.getCustomGoalTask().setGoalAndPath(goal);
                    log.atInfo().log(String.format("Going to: %s", goal));
                }
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny()) {
            if (args.hasExactlyOne()) {
                return new TabCompleteHelper()
                                .append(Action.getAllNames())
                                .sortAlphabetically()
                                .filterPrefix(args.getString())
                                .stream();
            } else {
                Action action = Action.getByName(args.getString());
                if (args.hasExactlyOne()) {
                    if (action == Action.LIST || action == Action.SAVE || action == Action.CLEAR) {
                        return new TabCompleteHelper()
                                        .append(Waypoint.Tag.getAllNames())
                                        .sortAlphabetically()
                                        .filterPrefix(args.getString())
                                        .stream();
                    } else if (action == Action.RESTORE) {
                        return Stream.empty();
                    } else {
                        return args.tabCompleteDatatype(ForWaypoints.INSTANCE);
                    }
                } else if (args.has(3) && action == Action.SAVE) {
                    args.get();
                    args.get();
                    return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
                }
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Manage waypoints";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The waypoint command allows you to manage Maestro's waypoints.",
                "",
                "Waypoints can be used to mark positions for later. Waypoints are each given a tag"
                        + " and an optional name.",
                "",
                "Note that the info, delete, and goal commands let you specify a waypoint by tag."
                    + " If there is more than one waypoint with a certain tag, then they will let"
                    + " you select which waypoint you mean.",
                "",
                "Missing arguments for the save command use the USER tag, creating an unnamed"
                        + " waypoint and your current position as defaults.",
                "",
                "Usage:",
                "> wp [l/list] - List all waypoints.",
                "> wp <l/list> <tag> - List all waypoints by tag.",
                "> wp <s/save> - Save an unnamed USER waypoint at your current position",
                "> wp <s/save> [tag] [name] [pos] - Save a waypoint with the specified tag, name"
                        + " and position.",
                "> wp <i/info/show> <tag/name> - Show info on a waypoint by tag or name.",
                "> wp <d/delete> <tag/name> - Delete a waypoint by tag or name.",
                "> wp <restore> <n> - Restore the last n deleted waypoints.",
                "> wp <c/clear> <tag> - Delete all waypoints with the specified tag.",
                "> wp <g/goal> <tag/name> - Set a goal to a waypoint by tag or name.",
                "> wp <goto> <tag/name> - Set a goal to a waypoint by tag or name and start"
                        + " pathing.");
    }

    private enum Action {
        LIST("list", "get", "l"),
        CLEAR("clear", "c"),
        SAVE("save", "s"),
        INFO("info", "show", "i"),
        DELETE("delete", "d"),
        RESTORE("restore"),
        GOAL("goal", "g"),
        GOTO("goto");
        private final ImmutableList<String> names;

        Action(String... names) {
            this.names = ImmutableList.copyOf(names);
        }

        public static Action getByName(String name) {
            for (Action action : Action.values()) {
                for (String alias : action.names) {
                    if (alias.equalsIgnoreCase(name)) {
                        return action;
                    }
                }
            }
            return null;
        }

        public static String[] getAllNames() {
            Set<String> names = new HashSet<>();
            for (Action action : Action.values()) {
                names.addAll(action.names);
            }
            return names.toArray(new String[0]);
        }
    }
}
