package maestro.behavior;

import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.Random;
import java.util.function.Predicate;
import maestro.Maestro;
import maestro.api.event.events.TickEvent;
import maestro.api.utils.Helper;
import maestro.utils.ToolSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class InventoryBehavior extends Behavior implements Helper {

    int ticksSinceLastInventoryMove;
    int[] lastTickRequestedMove; // not everything asks every tick, so remember the request while

    // coming to a halt

    public InventoryBehavior(Maestro maestro) {
        super(maestro);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!Maestro.settings().allowInventory.value) {
            return;
        }
        if (event.getType() == TickEvent.Type.OUT) {
            return;
        }
        if (ctx.player().containerMenu != ctx.player().inventoryMenu) {
            // we have a crafting table or a chest or something open
            return;
        }
        ticksSinceLastInventoryMove++;
        if (firstValidThrowaway()
                >= 9) { // aka there are none on the hotbar, but there are some in main inventory
            requestSwapWithHotBar(firstValidThrowaway(), 8);
        }
        int pick = bestToolAgainst(Blocks.STONE, PickaxeItem.class);
        if (pick >= 9) {
            requestSwapWithHotBar(pick, 0);
        }
        if (lastTickRequestedMove != null) {
            logDebug(
                    "Remembering to move "
                            + lastTickRequestedMove[0]
                            + " "
                            + lastTickRequestedMove[1]
                            + " from a previous tick");
            requestSwapWithHotBar(lastTickRequestedMove[0], lastTickRequestedMove[1]);
        }
    }

    public boolean attemptToPutOnHotbar(int inMainInvy, Predicate<Integer> disallowedHotbar) {
        OptionalInt destination = getTempHotbarSlot(disallowedHotbar);
        if (destination.isPresent()) {
            return requestSwapWithHotBar(inMainInvy, destination.getAsInt());
        }
        return true;
    }

    public OptionalInt getTempHotbarSlot(Predicate<Integer> disallowedHotbar) {
        // we're using 0 and 8 for pickaxe and throwaway
        ArrayList<Integer> candidates = new ArrayList<>();
        for (int i = 1; i < 8; i++) {
            if (ctx.player().getInventory().items.get(i).isEmpty() && !disallowedHotbar.test(i)) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            for (int i = 1; i < 8; i++) {
                if (!disallowedHotbar.test(i)) {
                    candidates.add(i);
                }
            }
        }
        if (candidates.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(candidates.get(new Random().nextInt(candidates.size())));
    }

    private boolean requestSwapWithHotBar(int inInventory, int inHotbar) {
        lastTickRequestedMove = new int[] {inInventory, inHotbar};
        if (ticksSinceLastInventoryMove < Maestro.settings().ticksBetweenInventoryMoves.value) {
            logDebug(
                    "Inventory move requested but delaying "
                            + ticksSinceLastInventoryMove
                            + " "
                            + Maestro.settings().ticksBetweenInventoryMoves.value);
            return false;
        }
        if (Maestro.settings().inventoryMoveOnlyIfStationary.value
                && !maestro.getInventoryPauserProcess().stationaryForInventoryMove()) {
            logDebug("Inventory move requested but delaying until stationary");
            return false;
        }
        ctx.playerController()
                .windowClick(
                        ctx.player().inventoryMenu.containerId,
                        inInventory < 9 ? inInventory + 36 : inInventory,
                        inHotbar,
                        ClickType.SWAP,
                        ctx.player());
        ticksSinceLastInventoryMove = 0;
        lastTickRequestedMove = null;
        return true;
    }

    private int firstValidThrowaway() { // TODO offhand idk
        NonNullList<ItemStack> invy = ctx.player().getInventory().items;
        for (int i = 0; i < invy.size(); i++) {
            if (Maestro.settings().acceptableThrowawayItems.value.contains(invy.get(i).getItem())) {
                return i;
            }
        }
        return -1;
    }

    private int bestToolAgainst(Block against, Class<? extends DiggerItem> cla$$) {
        NonNullList<ItemStack> invy = ctx.player().getInventory().items;
        int bestInd = -1;
        double bestSpeed = -1;
        for (int i = 0; i < invy.size(); i++) {
            ItemStack stack = invy.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (Maestro.settings().itemSaver.value
                    && (stack.getDamageValue() + Maestro.settings().itemSaverThreshold.value)
                            >= stack.getMaxDamage()
                    && stack.getMaxDamage() > 1) {
                continue;
            }
            if (cla$$.isInstance(stack.getItem())) {
                double speed =
                        ToolSet.calculateSpeedVsBlock(
                                stack, against.defaultBlockState()); // takes into account enchants
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestInd = i;
                }
            }
        }
        return bestInd;
    }

    public boolean hasGenericThrowaway() {
        for (Item item : Maestro.settings().acceptableThrowawayItems.value) {
            if (throwaway(false, stack -> item.equals(stack.getItem()))) {
                return true;
            }
        }
        return false;
    }

    public boolean selectThrowawayForLocation(boolean select, int x, int y, int z) {
        BlockState maybe = maestro.getBuilderProcess().placeAt(x, y, z, maestro.bsi.get0(x, y, z));
        if (maybe != null
                && throwaway(
                        select,
                        stack ->
                                stack.getItem() instanceof BlockItem
                                        && maybe.equals(
                                                ((BlockItem) stack.getItem())
                                                        .getBlock()
                                                        .getStateForPlacement(
                                                                new BlockPlaceContext(
                                                                        new UseOnContext(
                                                                                ctx.world(),
                                                                                ctx.player(),
                                                                                InteractionHand
                                                                                        .MAIN_HAND,
                                                                                stack,
                                                                                new BlockHitResult(
                                                                                        new Vec3(
                                                                                                ctx.player()
                                                                                                        .position()
                                                                                                        .x,
                                                                                                ctx.player()
                                                                                                        .position()
                                                                                                        .y,
                                                                                                ctx.player()
                                                                                                        .position()
                                                                                                        .z),
                                                                                        Direction
                                                                                                .UP,
                                                                                        ctx
                                                                                                .playerFeet(),
                                                                                        false)) {}))))) {
            return true; // gotem
        }
        if (maybe != null
                && throwaway(
                        select,
                        stack ->
                                stack.getItem() instanceof BlockItem
                                        && ((BlockItem) stack.getItem())
                                                .getBlock()
                                                .equals(maybe.getBlock()))) {
            return true;
        }
        for (Item item : Maestro.settings().acceptableThrowawayItems.value) {
            if (throwaway(select, stack -> item.equals(stack.getItem()))) {
                return true;
            }
        }
        return false;
    }

    public boolean throwaway(boolean select, Predicate<? super ItemStack> desired) {
        return throwaway(select, desired, Maestro.settings().allowInventory.value);
    }

    public boolean throwaway(
            boolean select, Predicate<? super ItemStack> desired, boolean allowInventory) {
        LocalPlayer p = ctx.player();
        NonNullList<ItemStack> inv = p.getInventory().items;
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.get(i);
            // this usage of settings() is okay because it's only called once during pathing
            // (while creating the CalculationContext at the very beginning)
            // and then it's called during execution
            // since this function is never called during cost calculation, we don't need to migrate
            // acceptableThrowawayItems to the CalculationContext
            if (desired.test(item)) {
                if (select) {
                    p.getInventory().selected = i;
                }
                return true;
            }
        }
        if (desired.test(p.getInventory().offhand.getFirst())) {
            // main hand takes precedence over off hand
            // that means that if we have block A selected in main hand and block B in off hand,
            // right clicking places block B
            // we've already checked above ^ and the main hand can't possible have an
            // acceptablethrowawayitem
            // so we need to select in the main hand something that doesn't right click
            // so not a shovel, not a hoe, not a block, etc
            for (int i = 0; i < 9; i++) {
                ItemStack item = inv.get(i);
                if (item.isEmpty() || item.getItem() instanceof PickaxeItem) {
                    if (select) {
                        p.getInventory().selected = i;
                    }
                    return true;
                }
            }
        }

        if (allowInventory) {
            for (int i = 9; i < 36; i++) {
                if (desired.test(inv.get(i))) {
                    if (select) {
                        requestSwapWithHotBar(i, 7);
                        p.getInventory().selected = 7;
                    }
                    return true;
                }
            }
        }

        return false;
    }
}
