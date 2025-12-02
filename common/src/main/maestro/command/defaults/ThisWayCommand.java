package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.pathing.goals.GoalXZ;
import net.minecraft.world.phys.Vec3;

public class ThisWayCommand extends Command {

    public ThisWayCommand(IAgent maestro) {
        super(maestro, "thisway", "forward");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireExactly(1);

        Vec3 origin;
        float yaw;

        // Use freecam perspective when active, otherwise use player's
        Agent agent = (Agent) maestro;
        if (agent.isFreecamActive()) {
            origin = agent.getFreecamPosition();
            yaw = agent.getFreeLookYaw();
        } else {
            origin = ctx.playerFeetAsVec();
            yaw = ctx.player().getYHeadRot();
        }

        GoalXZ goal = GoalXZ.fromDirection(origin, yaw, args.getAs(Double.class));
        maestro.getCustomGoalProcess().setGoal(goal);
        log.atInfo().addKeyValue("goal", goal).log("Goal set");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Travel in your current direction (or freecam direction)";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Creates a GoalXZ some amount of blocks in the direction you're currently looking.",
                "When freecam is active, uses the camera's direction instead of the bot's"
                        + " direction.",
                "",
                "Usage:",
                "> thisway <distance> - Makes a goal distance blocks in your current direction");
    }
}
