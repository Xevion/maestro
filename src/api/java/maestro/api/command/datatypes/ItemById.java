package maestro.api.command.datatypes;

import java.util.stream.Stream;
import maestro.api.command.exception.CommandException;
import maestro.api.command.helpers.TabCompleteHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public enum ItemById implements IDatatypeFor<Item> {
    INSTANCE;

    @Override
    public Item get(IDatatypeContext ctx) throws CommandException {
        ResourceLocation id = ResourceLocation.parse(ctx.getConsumer().getString());
        Item item;
        if ((item = BuiltInRegistries.ITEM.getOptional(id).orElse(null)) == null) {
            throw new IllegalArgumentException("No item found by that id");
        }
        return item;
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        return new TabCompleteHelper()
                        .append(
                                BuiltInRegistries.BLOCK.keySet().stream()
                                        .map(ResourceLocation::toString))
                        .filterPrefixNamespaced(ctx.getConsumer().getString())
                        .sortAlphabetically()
                        .stream();
    }
}
