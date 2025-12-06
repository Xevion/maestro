package maestro.command.datatypes;

import java.util.stream.Stream;
import maestro.command.exception.CommandException;
import maestro.command.helpers.TabCompleteHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public enum EntityClassById implements IDatatypeFor<EntityType> {
    INSTANCE;

    @Override
    public EntityType get(IDatatypeContext ctx) throws CommandException {
        ResourceLocation id = ResourceLocation.parse(ctx.getConsumer().getString());
        EntityType entity;
        if ((entity = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null)) == null) {
            throw new IllegalArgumentException("no entity found by that id");
        }
        return entity;
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        return new TabCompleteHelper()
                        .append(BuiltInRegistries.ENTITY_TYPE.stream().map(Object::toString))
                        .filterPrefixNamespaced(ctx.getConsumer().getString())
                        .sortAlphabetically()
                        .stream();
    }
}
