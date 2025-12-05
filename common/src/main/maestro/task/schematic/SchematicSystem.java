package maestro.task.schematic;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import maestro.api.command.registry.Registry;
import maestro.api.schematic.format.ISchematicFormat;
import maestro.task.schematic.format.DefaultSchematicFormats;

@SuppressWarnings("ImmutableEnumChecker")
public enum SchematicSystem {
    INSTANCE;

    private final Registry<ISchematicFormat> registry = new Registry<>();

    SchematicSystem() {
        Arrays.stream(DefaultSchematicFormats.values()).forEach(this.registry::register);
    }

    public Registry<ISchematicFormat> getRegistry() {
        return this.registry;
    }

    public Optional<ISchematicFormat> getByFile(File file) {
        return this.registry.stream().filter(format -> format.isFileType(file)).findFirst();
    }

    public List<String> getFileExtensions() {
        return this.registry.stream()
                .map(ISchematicFormat::getFileExtensions)
                .flatMap(List::stream)
                .toList();
    }
}
