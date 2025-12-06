package maestro.command.defaults;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;

public class CommandAlias extends Command {

    private final String shortDesc;
    public final String target;

    public CommandAlias(Agent agent, List<String> names, String shortDesc, String target) {
        super(agent, names.toArray(new String[0]));
        this.shortDesc = shortDesc;
        this.target = target;
    }

    public CommandAlias(Agent agent, String name, String shortDesc, String target) {
        super(agent, name);
        this.shortDesc = shortDesc;
        this.target = target;
    }

    @Override
    public void execute(String label, IArgConsumer args) {
        this.agent.getCommandManager().execute(String.format("%s %s", target, args.rawRest()));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return this.agent
                .getCommandManager()
                .tabComplete(String.format("%s %s", target, args.rawRest()));
    }

    @Override
    public String getShortDesc() {
        return shortDesc;
    }

    @Override
    public List<String> getLongDesc() {
        return Collections.singletonList(
                String.format("This command is an alias, for: %s ...", target));
    }
}
