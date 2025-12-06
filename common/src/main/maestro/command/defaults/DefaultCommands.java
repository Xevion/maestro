package maestro.command.defaults;

import java.util.*;
import maestro.Agent;
import maestro.command.ICommand;

public final class DefaultCommands {

    private DefaultCommands() {}

    public static List<ICommand> createAll(Agent agent) {
        Objects.requireNonNull(agent);
        List<ICommand> commands =
                new ArrayList<>(
                        Arrays.asList(
                                new HelpCommand(agent),
                                new SetCommand(agent),
                                new CommandAlias(
                                        agent,
                                        Arrays.asList(
                                                "modified", "mod", "maestro", "modifiedsettings"),
                                        "List modified settings",
                                        "set modified"),
                                new CommandAlias(
                                        agent,
                                        "reset",
                                        "Reset all settings or just one",
                                        "set reset"),
                                new GoalCommand(agent),
                                new GotoCommand(agent),
                                new PathCommand(agent),
                                new ProcCommand(agent),
                                new ETACommand(agent),
                                new VersionCommand(agent),
                                new RepackCommand(agent),
                                new BuildCommand(agent),
                                new LitematicaCommand(agent),
                                new ComeCommand(agent),
                                new AxisCommand(agent),
                                new ForceCancelCommand(agent),
                                new GcCommand(agent),
                                new InvertCommand(agent),
                                new TunnelCommand(agent),
                                new RenderCommand(agent),
                                new FarmCommand(agent),
                                new FollowCommand(agent),
                                new AttackCommand(agent),
                                new ShootCommand(agent),
                                new PickupCommand(agent),
                                new ExploreFilterCommand(agent),
                                new ReloadAllCommand(agent),
                                new SaveAllCommand(agent),
                                new ExploreCommand(agent),
                                new BlacklistCommand(agent),
                                new FindCommand(agent),
                                new MineCommand(agent),
                                new CoordinatorCommand(agent),
                                new ClickCommand(agent),
                                new FullbrightCommand(agent),
                                new SurfaceCommand(agent),
                                new ThisWayCommand(agent),
                                new WaypointsCommand(agent),
                                new CommandAlias(
                                        agent,
                                        "sethome",
                                        "Sets your home waypoint",
                                        "waypoints save home"),
                                new CommandAlias(
                                        agent,
                                        "home",
                                        "Path to your home waypoint",
                                        "waypoints goto home"),
                                new SelCommand(agent),
                                new ElytraCommand(agent)));
        ExecutionControlCommands prc = new ExecutionControlCommands(agent);
        commands.add(prc.pauseCommand);
        commands.add(prc.resumeCommand);
        commands.add(prc.pausedCommand);
        commands.add(prc.cancelCommand);
        return Collections.unmodifiableList(commands);
    }
}
