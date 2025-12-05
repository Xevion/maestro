package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.AgentAPI;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.task.CustomGoalTask;

public class PathCommand extends Command {

    public PathCommand(Agent maestro) {
        super(maestro, "path");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        CustomGoalTask customGoalProcess = maestro.getCustomGoalTask();
        args.requireMax(0);
        AgentAPI.getProvider().getWorldScanner().repack(ctx);
        customGoalProcess.path();
        log.atInfo().log("Pathing started");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Start heading towards the goal";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The path command tells Maestro to head towards the current goal.",
                "",
                "Usage:",
                "> path - Start the pathing.");
    }
}
