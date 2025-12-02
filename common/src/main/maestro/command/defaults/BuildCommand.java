package maestro.command.defaults;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.datatypes.RelativeBlockPos;
import maestro.api.command.datatypes.RelativeFile;
import maestro.api.command.exception.CommandException;
import maestro.api.utils.PackedBlockPos;
import maestro.utils.schematic.SchematicSystem;
import org.apache.commons.io.FilenameUtils;

public class BuildCommand extends Command {

    private final File schematicsDir;

    public BuildCommand(IAgent maestro) {
        super(maestro, "build");
        this.schematicsDir =
                new File(maestro.getPlayerContext().minecraft().gameDirectory, "schematics");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        final File file0 =
                args.getDatatypePost(RelativeFile.INSTANCE, schematicsDir).getAbsoluteFile();
        File file = file0;
        if (FilenameUtils.getExtension(file.getAbsolutePath()).isEmpty()) {
            file =
                    new File(
                            file.getAbsolutePath()
                                    + "."
                                    + Agent.settings().schematicFallbackExtension.value);
        }
        if (!file.exists()) {
            if (file0.exists()) {
                throw new CommandException.InvalidState(
                        String.format(
                                "Cannot load %s because I do not know which schematic format"
                                        + " that is. Please rename the file to include the correct"
                                        + " file extension.",
                                file));
            }
            throw new CommandException.InvalidState("Cannot find " + file);
        }
        if (SchematicSystem.INSTANCE.getByFile(file).isEmpty()) {
            StringJoiner formats = new StringJoiner(", ");
            SchematicSystem.INSTANCE.getFileExtensions().forEach(formats::add);
            throw new CommandException.InvalidState(
                    String.format(
                            "Unsupported schematic format. Recognized file extensions are: %s",
                            formats));
        }
        PackedBlockPos origin = ctx.playerFeet();
        PackedBlockPos buildOrigin;
        if (args.hasAny()) {
            args.requireMax(3);
            buildOrigin = args.getDatatypePost(RelativeBlockPos.INSTANCE, origin);
        } else {
            args.requireMax(0);
            buildOrigin = origin;
        }
        boolean success =
                maestro.getBuilderProcess().build(file.getName(), file, buildOrigin.toBlockPos());
        if (!success) {
            throw new CommandException.InvalidState(
                    "Couldn't load the schematic. Either your schematic is corrupt or this is a"
                            + " bug.");
        }
        log.atInfo().addKeyValue("origin", buildOrigin).log("Schematic loaded for building");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return RelativeFile.tabComplete(args, schematicsDir);
        } else if (args.has(2)) {
            args.get();
            return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Build a schematic";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Build a schematic from a file.",
                "",
                "Usage:",
                "> build <filename> - Loads and builds '<filename>.schematic'",
                "> build <filename> <x> <y> <z> - Custom position");
    }
}
