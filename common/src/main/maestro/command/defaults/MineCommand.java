package maestro.command.defaults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.cache.WorldScanner;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.datatypes.ForBlockSelector;
import maestro.command.exception.CommandException;
import maestro.selector.block.BlockCategory;
import maestro.selector.block.BlockSelector;
import maestro.selector.block.BlockSelectorLookup;

public class MineCommand extends Command {

    public MineCommand(Agent agent) {
        super(agent, "mine");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        int quantity = args.getAsOrDefault(Integer.class, 0);
        args.requireMin(1);

        // Parse block selectors (supports @ores, *_ore, #minecraft:logs, etc.)
        List<BlockSelector> selectors = new ArrayList<>();
        while (args.hasAny()) {
            try {
                BlockSelector selector = args.getDatatypeFor(ForBlockSelector.INSTANCE);
                selectors.add(selector);
            } catch (IllegalArgumentException e) {
                throw new InvalidSelectorException(e.getMessage());
            }
        }

        if (selectors.isEmpty()) {
            throw new NoSelectorsException();
        }

        // Create lookup and convert to BlockOptionalMetaLookup for MineProcess
        BlockSelectorLookup lookup = new BlockSelectorLookup(selectors);

        // Warn about individual selectors that matched nothing
        for (BlockSelector selector : selectors) {
            if (selector.resolve().isEmpty()) {
                log.atWarn()
                        .addKeyValue("selector", selector.getRawInput())
                        .log("Selector matched no blocks");
            }
        }

        // Error if ALL selectors combined match nothing
        if (lookup.resolveAll().isEmpty()) {
            throw new NoMatchesException();
        }

        WorldScanner.INSTANCE.repack(ctx);
        log.atInfo().addKeyValue("filter", lookup.toDisplayString()).log("Mining started");
        agent.getMineTask().mine(quantity, lookup.toBlockOptionalMetaLookup());
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        args.getAsOrDefault(Integer.class, 0);
        if (!args.hasExactlyOne()) {
            while (args.has(2)) {
                args.get();
            }
        }
        return args.tabCompleteDatatype(ForBlockSelector.INSTANCE);
    }

    @Override
    public String getShortDesc() {
        return "Mine some blocks";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The mine command allows you to tell Maestro to search for and mine blocks.",
                "",
                "Supports wildcards, categories, and Minecraft tags:",
                "",
                "Usage:",
                "> mine diamond_ore - Mine diamond ore.",
                "> mine @ores - Mine all ore blocks.",
                "> mine *_ore - Mine anything ending in _ore.",
                "> mine #minecraft:logs - Mine blocks in the logs tag.",
                "> mine 64 @ores - Mine until 64 ores collected.",
                "",
                "Categories: " + String.join(", ", BlockCategory.names()),
                "",
                "Also see the legitMine settings (see #set l legitMine).");
    }

    public static class NoSelectorsException extends CommandException.ErrorMessage {
        protected NoSelectorsException() {
            super("No valid block selectors specified");
        }
    }

    public static class InvalidSelectorException extends CommandException.ErrorMessage {
        protected InvalidSelectorException(String message) {
            super("Invalid selector: " + message);
        }
    }

    public static class NoMatchesException extends CommandException.ErrorMessage {
        protected NoMatchesException() {
            super("No blocks matched any selector");
        }
    }
}
