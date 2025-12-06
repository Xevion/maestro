package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.exception.CommandException;
import maestro.task.GetToBlockTask;

public class BlacklistCommand extends Command {

    public BlacklistCommand(Agent agent) {
        super(agent, "blacklist");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        GetToBlockTask proc = agent.getGetToBlockTask();
        if (!proc.isActive()) {
            throw new CommandException.InvalidState("GetToBlockTask is not currently active");
        }
        if (proc.blacklistClosest()) {
            log.atInfo().log("Blacklisted closest block instances");
        } else {
            throw new CommandException.InvalidState("No known locations, unable to blacklist");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Blacklist closest block";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "While going to a block this command blacklists the closest block so that Maestro"
                        + " won't attempt to get to it.",
                "",
                "Usage:",
                "> blacklist");
    }
}
