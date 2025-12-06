package maestro.command.datatypes;

import java.util.stream.Stream;
import maestro.command.exception.CommandException;
import maestro.command.helpers.TabCompleteHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public enum BlockById implements IDatatypeFor<Block> {
    INSTANCE;

    @Override
    public Block get(IDatatypeContext ctx) throws CommandException {
        ResourceLocation id = ResourceLocation.parse(ctx.getConsumer().getString());
        Block block;
        if ((block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null)) == null) {
            throw new IllegalArgumentException("no block found by that id");
        }
        return block;
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        String arg = ctx.getConsumer().getString();

        return new TabCompleteHelper()
                        .append(BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString))
                        .filterPrefixNamespaced(arg)
                        .sortAlphabetically()
                        .stream();
    }
}
