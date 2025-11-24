package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;
import maestro.api.utils.BetterBlockPos;

public class RenderCommand extends Command {

    public RenderCommand(IAgent maestro) {
        super(maestro, "render");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        BetterBlockPos origin = ctx.playerFeet();
        int renderDistance = (ctx.minecraft().options.renderDistance().get() + 1) * 16;
        ctx.minecraft()
                .levelRenderer
                .setBlocksDirty(
                        origin.x - renderDistance,
                        ctx.world().getMinY(),
                        origin.z - renderDistance,
                        origin.x + renderDistance,
                        ctx.world().getMaxY(),
                        origin.z + renderDistance);
        logDirect("Done");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Fix glitched chunks";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The render command fixes glitched chunk rendering without having to reload all of"
                        + " them.",
                "",
                "Usage:",
                "> render");
    }
}
