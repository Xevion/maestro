package maestro.utils.schematic.format.defaults;

import maestro.utils.schematic.StaticSchematic;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.ItemIdFix;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MCEditSchematic extends StaticSchematic {

    public MCEditSchematic(CompoundTag schematic) {
        String type = schematic.getString("Materials");
        if (!type.equals("Alpha")) {
            throw new IllegalStateException("bad schematic " + type);
        }
        this.x = schematic.getInt("Width");
        this.y = schematic.getInt("Height");
        this.z = schematic.getInt("Length");
        byte[] blocks = schematic.getByteArray("Blocks");
        //        byte[] metadata = schematic.getByteArray("Data");

        byte[] additional = null;
        if (schematic.contains("AddBlocks")) {
            byte[] addBlocks = schematic.getByteArray("AddBlocks");
            additional = new byte[addBlocks.length * 2];
            for (int i = 0; i < addBlocks.length; i++) {
                additional[i * 2] = (byte) ((addBlocks[i] >> 4) & 0xF); // lower nibble
                additional[i * 2 + 1] = (byte) (addBlocks[i] & 0xF); // upper nibble
            }
        }
        this.states = new BlockState[this.x][this.z][this.y];
        for (int y = 0; y < this.y; y++) {
            for (int z = 0; z < this.z; z++) {
                for (int x = 0; x < this.x; x++) {
                    int blockInd = (y * this.z + z) * this.x + x;

                    int blockID = blocks[blockInd] & 0xFF;
                    if (additional != null) {
                        // additional is 0 through 15 inclusive since it's & 0xF above
                        blockID |= additional[blockInd] << 8;
                    }
                    ResourceLocation blockKey =
                            ResourceLocation.tryParse(ItemIdFix.getItem(blockID));
                    Block block =
                            blockKey == null
                                    ? Blocks.AIR
                                    : BuiltInRegistries.BLOCK
                                            .get(blockKey)
                                            .map(Holder.Reference::value)
                                            .orElse(Blocks.AIR);

                    //                    int meta = metadata[blockInd] & 0xFF;
                    //                    this.states[x][z][y] = block.getStateFromMeta(meta);
                    this.states[x][z][y] = block.defaultBlockState();
                }
            }
        }
    }
}
