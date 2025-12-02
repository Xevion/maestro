package maestro.utils.schematic.format;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import maestro.api.schematic.IStaticSchematic;
import maestro.api.schematic.format.ISchematicFormat;
import maestro.utils.schematic.format.defaults.LitematicaSchematic;
import maestro.utils.schematic.format.defaults.MCEditSchematic;
import maestro.utils.schematic.format.defaults.SpongeSchematic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.apache.commons.io.FilenameUtils;

/** Default implementations of {@link ISchematicFormat} */
public enum DefaultSchematicFormats implements ISchematicFormat {

    /** The MCEdit schematic specification. Commonly denoted by the ".schematic" file extension. */
    MCEDIT("schematic") {
        @Override
        public IStaticSchematic parse(InputStream input) throws IOException {
            return new MCEditSchematic(NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()));
        }
    },

    /**
     * The SpongePowered Schematic Specification. Commonly denoted by the ".schem" file extension.
     *
     * @see <a href="https://github.com/SpongePowered/Schematic-Specification">Sponge Schematic
     *     Specification</a>
     */
    SPONGE("schem") {
        @Override
        public IStaticSchematic parse(InputStream input) throws IOException {
            CompoundTag nbt = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            int version = nbt.getInt("Version");
            return switch (version) {
                case 1, 2 -> new SpongeSchematic(nbt);
                default ->
                        throw new UnsupportedOperationException(
                                "Unsupported Version of a Sponge Schematic");
            };
        }
    },

    /**
     * The Litematica schematic specification. Commonly denoted by the ".litematic" file extension.
     */
    LITEMATICA("litematic") {
        @Override
        public IStaticSchematic parse(InputStream input) throws IOException {
            CompoundTag nbt = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            int version = nbt.getInt("Version");
            // 1.13-1.17
            // 1.18-1.20
            return switch (version) { // 1.12
                case 4, 5 ->
                        throw new UnsupportedOperationException(
                                "This litematic Version is too old.");
                case 6 ->
                        throw new UnsupportedOperationException(
                                "This litematic Version is too old.");
                case 7 -> // 1.21+
                        new LitematicaSchematic(nbt);
                default ->
                        throw new UnsupportedOperationException(
                                "Unsuported Version of a Litematica Schematic");
            };
        }
    };

    private final String extension;

    DefaultSchematicFormats(String extension) {
        this.extension = extension;
    }

    @Override
    public boolean isFileType(File file) {
        return this.extension.equalsIgnoreCase(FilenameUtils.getExtension(file.getAbsolutePath()));
    }

    @Override
    public List<String> getFileExtensions() {
        return Collections.singletonList(this.extension);
    }
}
