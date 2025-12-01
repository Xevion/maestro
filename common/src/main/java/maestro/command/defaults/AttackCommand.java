package maestro.command.defaults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.datatypes.ForEntitySelector;
import maestro.api.command.exception.CommandErrorMessageException;
import maestro.api.command.exception.CommandException;
import maestro.api.command.helpers.TabCompleteHelper;
import maestro.api.selector.entity.EntityCategory;
import maestro.api.selector.entity.EntitySelector;
import maestro.api.selector.entity.EntitySelectorLookup;
import net.minecraft.world.entity.LivingEntity;

public class AttackCommand extends Command {

    public AttackCommand(IAgent maestro) {
        super(maestro, "attack");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);

        // Check for legacy "entities" keyword to attack all living entities
        if (args.hasExactlyOne()) {
            String arg = args.peekString();
            if (arg.equalsIgnoreCase("entities")) {
                args.getString(); // consume the argument
                maestro.getAttackProcess().attack(LivingEntity.class::isInstance);
                log.atInfo().log("Attacking all entities");
                return;
            }
        }

        // Parse entity selectors (supports @hostile, *zombie*, #minecraft:raiders, etc.)
        List<EntitySelector> selectors = new ArrayList<>();
        while (args.hasAny()) {
            try {
                EntitySelector selector = args.getDatatypeFor(ForEntitySelector.INSTANCE);
                selectors.add(selector);
            } catch (IllegalArgumentException e) {
                throw new InvalidSelectorException(e.getMessage());
            }
        }

        if (selectors.isEmpty()) {
            throw new NoSelectorsException();
        }

        // Create lookup and start attacking
        EntitySelectorLookup lookup = new EntitySelectorLookup(selectors);

        // Warn about individual selectors that matched nothing
        for (EntitySelector selector : selectors) {
            if (selector.resolve().isEmpty()) {
                log.atWarn()
                        .addKeyValue("selector", selector.getRawInput())
                        .log("Selector matched no entities");
            }
        }

        // Error if ALL selectors combined match nothing
        if (lookup.resolveAll().isEmpty()) {
            throw new NoMatchesException();
        }

        maestro.getAttackProcess().attack(lookup.toPredicate());

        log.atInfo().addKeyValue("filter", lookup.toDisplayString()).log("Attacking started");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            // Datatype provides entity IDs and categories; we just add legacy "entities" keyword
            return new TabCompleteHelper()
                            .append("entities") // Legacy support
                            .append(args.tabCompleteDatatype(ForEntitySelector.INSTANCE))
                            .filterPrefix(args.peekString())
                            .stream();
        } else {
            while (args.has(2)) {
                args.get();
            }
            return args.tabCompleteDatatype(ForEntitySelector.INSTANCE);
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
                "> attack entities - Attacks all entities (legacy).",
                "> attack @hostile - Attack all hostile mobs.",
                "> attack @passive - Attack all passive mobs.",
                "> attack @undead - Attack zombies, skeletons, etc.",
                "> attack *zombie* - Attack any entity with 'zombie' in the name.",
                "> attack #minecraft:raiders - Attack entities in the raiders tag.",
                "> attack zombie skeleton - Attack specific entity types.",
                "",
                "Categories: " + String.join(", ", EntityCategory.names()));
    }

    public static class NoSelectorsException extends CommandErrorMessageException {
        protected NoSelectorsException() {
            super("No valid entity selectors specified");
        }
    }

    public static class InvalidSelectorException extends CommandErrorMessageException {
        protected InvalidSelectorException(String message) {
            super("Invalid selector: " + message);
        }
    }

    public static class NoMatchesException extends CommandErrorMessageException {
        protected NoMatchesException() {
            super("No entities matched any selector");
        }
    }
}
