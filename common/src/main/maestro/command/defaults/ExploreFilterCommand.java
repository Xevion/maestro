package maestro.command.defaults;

import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.datatypes.RelativeFile;
import maestro.api.command.exception.CommandException;
import maestro.api.utils.MaestroLogger;
import org.slf4j.Logger;

public class ExploreFilterCommand extends Command {

    private static final Logger log = MaestroLogger.get("cmd");

    public ExploreFilterCommand(IAgent maestro) {
        super(maestro, "explorefilter");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);
        File file =
                args.getDatatypePost(
                        RelativeFile.INSTANCE,
                        ctx.minecraft().gameDirectory.getAbsoluteFile().getParentFile());
        boolean invert = false;
        if (args.hasAny()) {
            if (args.getString().equalsIgnoreCase("invert")) {
                invert = true;
            } else {
                throw new CommandException.InvalidArgument.InvalidType(
                        args.consumed(), "either \"invert\" or nothing");
            }
        }
        try {
            maestro.getExploreProcess().applyJsonFilter(file.toPath().toAbsolutePath(), invert);
        } catch (NoSuchFileException e) {
            throw new CommandException.InvalidState("File not found");
        } catch (JsonSyntaxException e) {
            throw new CommandException.InvalidState("Invalid JSON syntax");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        log.atInfo().addKeyValue("inverted", invert).log("Explore filter applied");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return RelativeFile.tabComplete(args, RelativeFile.gameDir(ctx.minecraft()));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Explore chunks from a json";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Apply an explore filter before using explore, which tells the explore process"
                        + " which chunks have been explored/not explored.",
                "",
                "The JSON file will follow this format: [{\"x\":0,\"z\":0},...]",
                "",
                "If 'invert' is specified, the chunks listed will be considered NOT explored,"
                        + " rather than explored.",
                "",
                "Usage:",
                "> explorefilter <path> [invert] - Load the JSON file referenced by the specified"
                        + " path. If invert is specified, it must be the literal word 'invert'.");
    }
}
