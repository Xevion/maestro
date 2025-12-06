package maestro.task.schematic.format.defaults;

import com.google.common.base.Splitter;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import maestro.task.schematic.StaticSchematic;
import maestro.utils.Loggers;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;

public final class SpongeSchematic extends StaticSchematic {

    private static final Logger log = Loggers.Build.get();

    public SpongeSchematic(CompoundTag nbt) {
        this.x = nbt.getInt("Width");
        this.y = nbt.getInt("Height");
        this.z = nbt.getInt("Length");
        this.states = new BlockState[this.x][this.z][this.y];

        Int2ObjectArrayMap<BlockState> palette = new Int2ObjectArrayMap<>();
        CompoundTag paletteTag = nbt.getCompound("Palette");
        for (String tag : paletteTag.getAllKeys()) {
            int index = paletteTag.getInt(tag);

            SerializedBlockState serializedState = SerializedBlockState.getFromString(tag);
            if (serializedState == null) {
                throw new IllegalArgumentException("Unable to parse palette tag");
            }

            BlockState state = serializedState.deserialize();
            if (state == null) {
                throw new IllegalArgumentException("Unable to deserialize palette tag");
            }

            palette.put(index, state);
        }

        // BlockData is stored as an NBT byte[], however, the actual data that is represented is a
        // varint[]
        byte[] rawBlockData = nbt.getByteArray("BlockData");
        int[] blockData = new int[this.x * this.y * this.z];
        int offset = 0;
        for (int i = 0; i < blockData.length; i++) {
            if (offset >= rawBlockData.length) {
                throw new IllegalArgumentException(
                        "No remaining bytes in BlockData for complete schematic");
            }

            // Decode VarInt manually (protocol buffer style encoding)
            int value = 0;
            int size = 0;
            while (true) {
                byte b = rawBlockData[offset++];
                value |= (b & 0x7F) << (size++ * 7);

                if (size > 5) {
                    throw new IllegalArgumentException("VarInt size exceeds 5 bytes");
                }

                // Most significant bit indicates another byte follows
                if ((b & 0x80) == 0) {
                    break;
                }
            }
            blockData[i] = value;
        }

        for (int y = 0; y < this.y; y++) {
            for (int z = 0; z < this.z; z++) {
                for (int x = 0; x < this.x; x++) {
                    int index = (y * this.z + z) * this.x + x;
                    BlockState state = palette.get(blockData[index]);
                    if (state == null) {
                        throw new IllegalArgumentException("Invalid Palette Index " + index);
                    }

                    this.states[x][z][y] = state;
                }
            }
        }
    }

    private static final class SerializedBlockState {

        private static final Pattern REGEX =
                Pattern.compile(
                        "(?<location>(\\w+:)?\\w+)(\\[(?<properties>\\w+=\\w+(,\\w+=\\w+)*)])?");

        private final ResourceLocation resourceLocation;
        private final Map<String, String> properties;
        private BlockState blockState;

        private SerializedBlockState(
                ResourceLocation resourceLocation, Map<String, String> properties) {
            this.resourceLocation = resourceLocation;
            this.properties = properties;
        }

        private BlockState deserialize() {
            if (this.blockState == null) {
                Block block =
                        BuiltInRegistries.BLOCK
                                .get(this.resourceLocation)
                                .map(Holder.Reference::value)
                                .orElse(Blocks.AIR);
                this.blockState = block.defaultBlockState();

                this.properties.keySet().stream()
                        .sorted(String::compareTo)
                        .forEachOrdered(
                                key -> {
                                    Property<?> property =
                                            block.getStateDefinition().getProperty(key);
                                    if (property != null) {
                                        this.blockState =
                                                setPropertyValue(
                                                        this.blockState,
                                                        property,
                                                        this.properties.get(key));
                                    }
                                });
            }
            return this.blockState;
        }

        private static SerializedBlockState getFromString(String s) {
            Matcher m = REGEX.matcher(s);
            if (!m.matches()) {
                return null;
            }

            try {
                String location = m.group("location");
                String properties = m.group("properties");

                ResourceLocation resourceLocation = ResourceLocation.parse(location);
                Map<String, String> propertiesMap = new HashMap<>();
                if (properties != null) {
                    for (String property : Splitter.on(',').split(properties)) {
                        List<String> split = Splitter.on('=').splitToList(property);
                        propertiesMap.put(split.get(0), split.get(1));
                    }
                }

                return new SerializedBlockState(resourceLocation, propertiesMap);
            } catch (Exception e) {
                log.atError()
                        .setCause(e)
                        .addKeyValue("block_state", s)
                        .log("Failed to deserialize block state");
                return null;
            }
        }

        private static <T extends Comparable<T>> BlockState setPropertyValue(
                BlockState state, Property<T> property, String value) {
            Optional<T> parsed = property.getValue(value);
            if (parsed.isPresent()) {
                return state.setValue(property, parsed.get());
            } else {
                throw new IllegalArgumentException("Invalid value for property " + property);
            }
        }
    }
}
