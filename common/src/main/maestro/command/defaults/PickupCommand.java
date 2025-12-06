package maestro.command.defaults;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.command.Command;
import maestro.command.argument.IArgConsumer;
import maestro.command.datatypes.ItemById;
import maestro.command.exception.CommandException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class PickupCommand extends Command {

    public PickupCommand(Agent agent) {
        super(agent, "pickup");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Set<Item> collecting = new HashSet<>();
        while (args.hasAny()) {
            Item item = args.getDatatypeFor(ItemById.INSTANCE);
            collecting.add(item);
        }
        if (collecting.isEmpty()) {
            agent.getFollowTask().pickup(stack -> true);
            log.atInfo().log("Picking up all items");
        } else {
            agent.getFollowTask().pickup(stack -> collecting.contains(stack.getItem()));
            log.atInfo()
                    .addKeyValue("item_count", collecting.size())
                    .log("Picking up specified items");
            collecting.stream()
                    .map(BuiltInRegistries.ITEM::getKey)
                    .map(ResourceLocation::toString)
                    .forEach(item -> log.atInfo().addKeyValue("item", item).log("Item filter"));
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        while (args.has(2)) {
            if (args.peekDatatypeOrNull(ItemById.INSTANCE) == null) {
                return Stream.empty();
            }
            args.get();
        }
        return args.tabCompleteDatatype(ItemById.INSTANCE);
    }

    @Override
    public String getShortDesc() {
        return "Pickup items";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Usage:",
                "> pickup - Pickup anything",
                "> pickup <item1> <item2> <...> - Pickup certain items");
    }
}
