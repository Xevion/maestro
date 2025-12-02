package maestro.utils.schematic;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import maestro.api.command.registry.Registry;
import maestro.api.schematic.ISchematicSystem;
import maestro.api.schematic.format.ISchematicFormat;
import maestro.utils.schematic.format.DefaultSchematicFormats;

@SuppressWarnings("ImmutableEnumChecker")
public enum SchematicSystem implements ISchematicSystem {
    INSTANCE;

    private final Registry<ISchematicFormat> registry = new Registry<>();

    SchematicSystem() {
        Arrays.stream(DefaultSchematicFormats.values()).forEach(this.registry::register);
    }

    @Override
    public Registry<ISchematicFormat> getRegistry() {
        return this.registry;
    }

    @Override
    public Optional<ISchematicFormat> getByFile(File file) {
        return this.registry.stream().filter(format -> format.isFileType(file)).findFirst();
    }

    @Override
    public List<String> getFileExtensions() {
        return this.registry.stream()
                .map(ISchematicFormat::getFileExtensions)
                .flatMap(List::stream)
                .toList();
    }
}
