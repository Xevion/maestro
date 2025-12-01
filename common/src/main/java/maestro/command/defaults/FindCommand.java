package maestro.command.defaults;

import static maestro.api.command.IMaestroChatControl.FORCE_COMMAND_PREFIX;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import maestro.api.IAgent;
import maestro.api.MaestroAPI;
import maestro.api.command.Command;
import maestro.api.command.argument.IArgConsumer;
import maestro.api.command.datatypes.BlockById;
import maestro.api.command.exception.CommandException;
import maestro.api.command.helpers.TabCompleteHelper;
import maestro.api.utils.PackedBlockPos;
import maestro.cache.CachedChunk;
import maestro.utils.chat.ChatMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;

public class FindCommand extends Command {

    public FindCommand(IAgent maestro) {
        super(maestro, "find");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);
        List<Block> toFind = new ArrayList<>();
        while (args.hasAny()) {
            toFind.add(args.getDatatypeFor(BlockById.INSTANCE));
        }
        PackedBlockPos origin = ctx.playerFeet();
        Component[] components =
                toFind.stream()
                        .flatMap(
                                block ->
                                        ctx
                                                .worldData()
                                                .getCachedWorld()
                                                .getLocationsOf(
                                                        BuiltInRegistries.BLOCK
                                                                .getKey(block)
                                                                .getPath(),
                                                        Integer.MAX_VALUE,
                                                        origin.getX(),
                                                        origin.getY(),
                                                        4)
                                                .stream())
                        .map(pos -> new PackedBlockPos(pos))
                        .map(this::positionToComponent)
                        .toArray(Component[]::new);
        if (components.length > 0) {
            // Send formatted component list to chat
            for (Component component : components) {
                net.minecraft.network.chat.MutableComponent prefixed =
                        net.minecraft.network.chat.Component.literal("");
                prefixed.append(ChatMessage.createCategoryPrefix("cmd"));
                prefixed.append(" ");
                prefixed.append(component);

                net.minecraft.client.Minecraft.getInstance()
                        .execute(() -> MaestroAPI.getSettings().logger.value.accept(prefixed));
            }
        } else {
            log.atInfo().log("No cached positions found for requested blocks");
        }
    }

    private Component positionToComponent(PackedBlockPos pos) {
        String positionText = String.format("%s %s %s", pos.getX(), pos.getY(), pos.getZ());
        String command = String.format("%sgoal %s", FORCE_COMMAND_PREFIX, positionText);
        MutableComponent baseComponent = Component.literal(pos.toString());
        MutableComponent hoverComponent = Component.literal("Click to set goal to this position");
        baseComponent.setStyle(
                baseComponent
                        .getStyle()
                        .withColor(ChatFormatting.GRAY)
                        .withInsertion(positionText)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(
                                new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponent)));
        return baseComponent;
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        return new TabCompleteHelper()
                        .append(
                                CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.stream()
                                        .map(BuiltInRegistries.BLOCK::getKey)
                                        .map(Object::toString))
                        .filterPrefixNamespaced(args.getString())
                        .sortAlphabetically()
                        .stream();
    }

    @Override
    public String getShortDesc() {
        return "Find positions of a certain block";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The find command searches through Maestro's cache and attempts to find the"
                        + " location of the block.",
                "Tab completion will suggest only cached blocks and uncached blocks can not be"
                        + " found.",
                "",
                "Usage:",
                "> find <block> [...] - Try finding the listed blocks");
    }
}
