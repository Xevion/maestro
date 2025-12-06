package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.exception.CommandException;
import maestro.coordination.CoordinationServer;

public class CoordinatorCommand extends Command {

    public CoordinatorCommand(Agent agent) {
        super(agent, "coordinator");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Agent agent = this.agent;
        CoordinationServer server = agent.getCoordinationServer();

        if (server != null && server.isRunning()) {
            server.stop();
            log.atInfo().log("Coordinator stopped");
        } else {
            int goal = args.getAsOrDefault(Integer.class, 100);

            if (server == null) {
                server = new CoordinationServer();
                agent.setCoordinationServer(server);
            }

            server.start(9090, goal);
            log.atInfo()
                    .addKeyValue("port", 9090)
                    .addKeyValue("goal", goal)
                    .log("Coordinator started");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            return Stream.of("50", "100", "200", "500", "1000");
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Start/stop coordination server";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Starts or stops the multi-agent coordination server.",
                "",
                "Usage:",
                "> coordinator - Toggle server (default goal: 100)",
                "> coordinator <goal> - Start with custom goal",
                "",
                "Examples:",
                "> coordinator - Start with goal of 100",
                "> coordinator 200 - Start with goal of 200",
                "",
                "The coordinator listens on port 9090 and manages area claims",
                "and progress tracking for connected worker bots.");
    }
}
