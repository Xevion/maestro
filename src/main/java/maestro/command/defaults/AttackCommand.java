package maestro.command.defaults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.datatypes.EntityClassById;
import maestro.api.command.exception.CommandErrorMessageException;
import maestro.api.command.exception.CommandException;
import maestro.api.command.helpers.TabCompleteHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public class AttackCommand extends Command {

    public AttackCommand(IAgent maestro) {
        super(maestro, "attack");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);

        // Check for "entities" keyword to attack all living entities
        if (args.hasExactlyOne()) {
            String arg = args.peekString();
            if (arg.equalsIgnoreCase("entities")) {
                args.getString(); // consume the argument
                maestro.getAttackProcess().attack(LivingEntity.class::isInstance);
                logDirect("Attacking all entities");
                return;
            }
        }

        // Parse entity types
        List<EntityType> entityTypes = new ArrayList<>();
        while (args.hasAny()) {
            EntityType type = args.getDatatypeFor(EntityClassById.INSTANCE);
            if (type != null) {
                entityTypes.add(type);
            }
        }

        if (entityTypes.isEmpty()) {
            throw new NoEntityTypesException();
        }

        // Create predicate matching specified entity types
        maestro.getAttackProcess()
                .attack(e -> entityTypes.stream().anyMatch(t -> e.getType().equals(t)));

        logDirect("Attacking these types of entities:");
        entityTypes.stream()
                .map(BuiltInRegistries.ENTITY_TYPE::getKey)
                .map(Objects::requireNonNull)
                .map(ResourceLocation::toString)
                .forEach(this::logDirect);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                            .append("entities")
                            .append(args.tabCompleteDatatype(EntityClassById.INSTANCE))
                            .filterPrefix(args.getString())
                            .stream();
        } else {
            while (args.has(2)) {
                if (args.peekDatatypeOrNull(EntityClassById.INSTANCE) == null) {
                    return Stream.empty();
                }
                args.get();
            }
            return args.tabCompleteDatatype(EntityClassById.INSTANCE);
        }
    }

    @Override
    public String getShortDesc() {
        return "Attack entities";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The attack command tells Maestro to attack certain kinds of entities.",
                "",
                "Usage:",
                "> attack entities - Attacks all entities.",
                "> attack <entity1> <entity2> <...> - Attack certain entities (for example"
                        + " 'chicken', 'zombie', 'skeleton' etc.)");
    }

    public static class NoEntityTypesException extends CommandErrorMessageException {

        protected NoEntityTypesException() {
            super("No valid entity types specified");
        }
    }
}
