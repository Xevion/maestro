package maestro.api.schematic;

import java.io.File;
import java.util.List;
import java.util.Optional;
import maestro.api.command.registry.Registry;
import maestro.api.schematic.format.ISchematicFormat;

public interface ISchematicSystem {

    /**
     * @return The registry of supported schematic formats
     */
    Registry<ISchematicFormat> getRegistry();

    /**
     * Attempts to find an {@link ISchematicFormat} that supports the specified schematic file.
     *
     * @param file A schematic file
     * @return The corresponding format for the file, {@link Optional#empty()} if no candidates were
     *     found.
     */
    Optional<ISchematicFormat> getByFile(File file);

    /**
     * @return A list of file extensions used by supported formats
     */
    List<String> getFileExtensions();
}
