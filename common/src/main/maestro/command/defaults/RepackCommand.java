package maestro.command.defaults;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.AgentAPI;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.exception.CommandException;

public class RepackCommand extends Command {

    public RepackCommand(Agent maestro) {
        super(maestro, "repack", "rescan");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        int count = AgentAPI.getProvider().getWorldScanner().repack(ctx);
        log.atInfo().addKeyValue("chunk_count", count).log("Chunks queued for repacking");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Re-cache chunks";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Repack chunks around you. This basically re-caches them.",
                "",
                "Usage:",
                "> repack - Repack chunks.");
    }
}
