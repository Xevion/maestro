package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;

public class SchematicaCommand extends Command {

    public SchematicaCommand(IAgent maestro) {
        super(maestro, "schematica");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        maestro.getBuilderProcess().buildOpenSchematic();
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
                "Builds the schematic currently open in Schematica.", "", "Usage:", "> schematica");
    }
}
