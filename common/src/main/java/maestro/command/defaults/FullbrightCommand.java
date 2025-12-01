package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;

public class FullbrightCommand extends Command {

    public FullbrightCommand(IAgent maestro) {
        super(maestro, "fullbright", "gamma");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        boolean enabled = Agent.settings().fullbright.value;
        Agent.settings().fullbright.value = !enabled;
        String state = Agent.settings().fullbright.value ? "enabled" : "disabled";
        log.atInfo().log(String.format("Fullbright %s", state));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Toggle fullbright (gamma mode)";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Toggles fullbright by clearing the lightmap texture to full white.",
                "Provides maximum brightness in dark areas without affecting server-side light"
                        + " levels.",
                "",
                "Usage:",
                "> fullbright - Toggle fullbright on/off",
                "> gamma - Alias for fullbright");
    }
}
