package maestro.command.defaults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.datatypes.ForEntitySelector;
import maestro.command.exception.CommandException;
import maestro.command.helpers.TabCompleteHelper;
import maestro.selector.entity.EntityCategory;
import maestro.selector.entity.EntitySelector;
import maestro.selector.entity.EntitySelectorLookup;

public class ShootCommand extends Command {

    public ShootCommand(Agent maestro) {
        super(maestro, "shoot");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);

        // Handle "stop" subcommand
        if (args.hasExactlyOne()) {
            String arg = args.peekString();
            if (arg.equalsIgnoreCase("stop")) {
                args.getString(); // consume
                maestro.getRangedCombatTask().cancel();
                log.atInfo().log("Shooting stopped");
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

        // Create lookup and start shooting
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

        maestro.getRangedCombatTask().shoot(lookup.toPredicate());

        log.atInfo().addKeyValue("filter", lookup.toDisplayString()).log("Shooting started");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            // Add "stop" subcommand to tab complete
            return new TabCompleteHelper()
                            .append("stop")
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
        return "Shoot entities with bow";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The shoot command tells Maestro to shoot at entities using a bow.",
                "",
                "Features:",
                "- Advanced ballistic trajectory calculations",
                "- Moving target prediction",
                "- Smooth dynamic aiming",
                "",
                "Usage:",
                "> shoot stop - Stop shooting.",
                "> shoot @hostile - Shoot all hostile mobs.",
                "> shoot @passive - Shoot all passive mobs.",
                "> shoot *zombie* - Shoot any entity with 'zombie' in the name.",
                "> shoot #minecraft:raiders - Shoot entities in the raiders tag.",
                "> shoot creeper skeleton - Shoot specific entity types.",
                "",
                "Categories: " + String.join(", ", EntityCategory.names()));
    }

    public static class NoSelectorsException extends CommandException.ErrorMessage {
        protected NoSelectorsException() {
            super("No valid entity selectors specified");
        }
    }

    public static class InvalidSelectorException extends CommandException.ErrorMessage {
        protected InvalidSelectorException(String message) {
            super("Invalid selector: " + message);
        }
    }

    public static class NoMatchesException extends CommandException.ErrorMessage {
        protected NoMatchesException() {
            super("No entities matched any selector");
        }
    }
}
