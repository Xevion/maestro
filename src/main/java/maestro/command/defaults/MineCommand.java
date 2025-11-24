package maestro.command.defaults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IMaestro;
import maestro.api.MaestroAPI;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.datatypes.ForBlockOptionalMeta;
import maestro.api.command.exception.CommandException;
import maestro.api.utils.BlockOptionalMeta;

public class MineCommand extends Command {

    public MineCommand(IMaestro maestro) {
        super(maestro, "mine");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        int quantity = args.getAsOrDefault(Integer.class, 0);
        args.requireMin(1);
        List<BlockOptionalMeta> boms = new ArrayList<>();
        while (args.hasAny()) {
            boms.add(args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE));
        }
        MaestroAPI.getProvider().getWorldScanner().repack(ctx);
        logDirect(String.format("Mining %s", boms));
        maestro.getMineProcess().mine(quantity, boms.toArray(new BlockOptionalMeta[0]));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        args.getAsOrDefault(Integer.class, 0);
        while (args.has(2)) {
            args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
        }
        return args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
    }

    @Override
    public String getShortDesc() {
        return "Mine some blocks";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The mine command allows you to tell Maestro to search for and mine individual"
                        + " blocks.",
                "",
                "The specified blocks can be ores, or any other block.",
                "",
                "Also see the legitMine settings (see #set l legitMine).",
                "",
                "Usage:",
                "> mine diamond_ore - Mines all diamonds it can find.");
    }
}
