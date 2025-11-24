package maestro.command.defaults;

import java.util.*;
import maestro.api.IAgent;
import maestro.api.command.ICommand;

public final class DefaultCommands {

    private DefaultCommands() {}

    public static List<ICommand> createAll(IAgent maestro) {
        Objects.requireNonNull(maestro);
        List<ICommand> commands =
                new ArrayList<>(
                        Arrays.asList(
                                new HelpCommand(maestro),
                                new SetCommand(maestro),
                                new CommandAlias(
                                        maestro,
                                        Arrays.asList(
                                                "modified", "mod", "maestro", "modifiedsettings"),
                                        "List modified settings",
                                        "set modified"),
                                new CommandAlias(
                                        maestro,
                                        "reset",
                                        "Reset all settings or just one",
                                        "set reset"),
                                new GoalCommand(maestro),
                                new GotoCommand(maestro),
                                new PathCommand(maestro),
                                new ProcCommand(maestro),
                                new ETACommand(maestro),
                                new VersionCommand(maestro),
                                new RepackCommand(maestro),
                                new BuildCommand(maestro),
                                // new SchematicaCommand(maestro),
                                new LitematicaCommand(maestro),
                                new ComeCommand(maestro),
                                new AxisCommand(maestro),
                                new ForceCancelCommand(maestro),
                                new GcCommand(maestro),
                                new InvertCommand(maestro),
                                new TunnelCommand(maestro),
                                new RenderCommand(maestro),
                                new FarmCommand(maestro),
                                new FollowCommand(maestro),
                                new AttackCommand(maestro),
                                new PickupCommand(maestro),
                                new ExploreFilterCommand(maestro),
                                new ReloadAllCommand(maestro),
                                new SaveAllCommand(maestro),
                                new ExploreCommand(maestro),
                                new BlacklistCommand(maestro),
                                new FindCommand(maestro),
                                new MineCommand(maestro),
                                new ClickCommand(maestro),
                                new SurfaceCommand(maestro),
                                new ThisWayCommand(maestro),
                                new WaypointsCommand(maestro),
                                new CommandAlias(
                                        maestro,
                                        "sethome",
                                        "Sets your home waypoint",
                                        "waypoints save home"),
                                new CommandAlias(
                                        maestro,
                                        "home",
                                        "Path to your home waypoint",
                                        "waypoints goto home"),
                                new SelCommand(maestro),
                                new ElytraCommand(maestro)));
        ExecutionControlCommands prc = new ExecutionControlCommands(maestro);
        commands.add(prc.pauseCommand);
        commands.add(prc.resumeCommand);
        commands.add(prc.pausedCommand);
        commands.add(prc.cancelCommand);
        return Collections.unmodifiableList(commands);
    }
}
