package maestro.api.schematic.format;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import maestro.api.schematic.ISchematic;
import maestro.api.schematic.IStaticSchematic;

/** The base of a {@link ISchematic} file format */
public interface ISchematicFormat {

    /**
     * @return The parser for creating schematics of this format
     */
    IStaticSchematic parse(InputStream input) throws IOException;

    /**
     * @param file The file to check against
     * @return Whether or not the specified file matches this schematic format
     */
    boolean isFileType(File file);

    /**
     * @return A list of file extensions used by this format
     */
    List<String> getFileExtensions();
}
