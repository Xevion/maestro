package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.exception.CommandException;

public class LitematicaCommand extends Command {

    public LitematicaCommand(Agent maestro) {
        super(maestro, "fi/dy/masa/litematica");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);
        int schematic = args.hasAny() ? args.getAs(Integer.class) - 1 : 0;
        maestro.getBuilderTask().buildOpenLitematic(schematic);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Builds the loaded schematic";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Build a schematic currently open in Litematica.",
                "",
                "Usage:",
                "> litematica",
                "> litematica <#>");
    }
}
