package maestro.behavior

import maestro.Agent
import maestro.api.event.events.TickEvent
import maestro.api.utils.Helper
import maestro.api.utils.Loggers
import maestro.task.ToolSet.Companion.calculateSpeedVsBlock
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.DiggerItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.PickaxeItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.slf4j.Logger
import java.util.OptionalInt

/**
 * Manages bot inventory operations including tool selection and item management.
 *
 * Handles:
 * - Automatic tool selection (pickaxe, throwaway items)
 * - Inventory swapping between hotbar and main inventory
 * - Throttled inventory moves to appear human-like
 */
class InventoryBehavior(
    maestro: Agent,
) : Behavior(maestro),
    Helper {
    var ticksSinceLastInventoryMove: Int = 0
    private var lastTickRequestedMove: IntArray? = null

    override fun onTick(event: TickEvent) {
        if (!Agent.settings().allowInventory.value) {
            return
        }
        if (event.type == TickEvent.Type.OUT) {
            return
        }
        if (ctx.player().containerMenu !== ctx.player().inventoryMenu) {
            // we have a crafting table or a chest or something open
            return
        }
        ticksSinceLastInventoryMove++

        val throwaway = firstValidThrowaway()
        if (throwaway >= 9) { // aka there are none on the hotbar, but there are some in main inventory
            requestSwapWithHotBar(throwaway, 8)
        }

        val pick = bestToolAgainst(Blocks.STONE, PickaxeItem::class.java)
        if (pick >= 9) {
            requestSwapWithHotBar(pick, 0)
        }

        lastTickRequestedMove?.let { (from, to) ->
            log
                .atDebug()
                .addKeyValue("from_slot", from)
                .addKeyValue("to_slot", to)
                .log("Inventory move deferred from previous tick")
            requestSwapWithHotBar(from, to)
        }
    }

    fun attemptToPutOnHotbar(
        inMainInventory: Int,
        disallowedHotbar: (Int) -> Boolean,
    ): Boolean {
        val destination = getTempHotbarSlot(disallowedHotbar)
        return if (destination.isPresent) {
            requestSwapWithHotBar(inMainInventory, destination.asInt)
        } else {
            true
        }
    }

    fun getTempHotbarSlot(disallowedHotbar: (Int) -> Boolean): OptionalInt {
        // we're using 0 and 8 for pickaxe and throwaway
        val inventory = ctx.player().getInventory().items

        // Prefer empty slots
        val emptySlots = (1..7).filter { !disallowedHotbar(it) && inventory[it].isEmpty }
        if (emptySlots.isNotEmpty()) {
            return OptionalInt.of(emptySlots.random())
        }

        // Fall back to any allowed slot
        val allowedSlots = (1..7).filterNot(disallowedHotbar)
        return if (allowedSlots.isNotEmpty()) {
            OptionalInt.of(allowedSlots.random())
        } else {
            OptionalInt.empty()
        }
    }

    private fun requestSwapWithHotBar(
        inInventory: Int,
        inHotbar: Int,
    ): Boolean {
        lastTickRequestedMove = intArrayOf(inInventory, inHotbar)

        if (ticksSinceLastInventoryMove < Agent.settings().ticksBetweenInventoryMoves.value) {
            log
                .atDebug()
                .addKeyValue("ticks_since_move", ticksSinceLastInventoryMove)
                .addKeyValue("min_ticks_required", Agent.settings().ticksBetweenInventoryMoves.value)
                .log("Inventory move throttled")
            return false
        }

        if (Agent.settings().inventoryMoveOnlyIfStationary.value &&
            !maestro.inventoryPauserTask.stationaryForInventoryMove()
        ) {
            log.atDebug().log("Inventory move deferred until stationary")
            return false
        }

        ctx.playerController().windowClick(
            ctx.player().inventoryMenu.containerId,
            if (inInventory < 9) inInventory + 36 else inInventory,
            inHotbar,
            ClickType.SWAP,
            ctx.player(),
        )

        ticksSinceLastInventoryMove = 0
        lastTickRequestedMove = null
        return true
    }

    private fun firstValidThrowaway(): Int {
        val inventory = ctx.player().getInventory().items
        val acceptableItems = Agent.settings().acceptableThrowawayItems.value

        return inventory.indexOfFirst { it.item in acceptableItems }.takeIf { it >= 0 } ?: -1
    }

    private fun bestToolAgainst(
        against: Block,
        toolClass: Class<out DiggerItem>,
    ): Int {
        val inventory = ctx.player().getInventory().items
        var bestInd = -1
        var bestSpeed = -1.0

        for (i in inventory.indices) {
            val stack = inventory[i]
            if (stack.isEmpty) continue

            // Check item saver
            if (Agent.settings().itemSaver.value &&
                (stack.damageValue + Agent.settings().itemSaverThreshold.value) >= stack.maxDamage &&
                stack.maxDamage > 1
            ) {
                continue
            }

            if (toolClass.isInstance(stack.item)) {
                val speed = calculateSpeedVsBlock(stack, against.defaultBlockState())
                if (speed > bestSpeed) {
                    bestSpeed = speed
                    bestInd = i
                }
            }
        }

        return bestInd
    }

    fun hasGenericThrowaway(): Boolean =
        Agent.settings().acceptableThrowawayItems.value.any { item ->
            throwaway(false, { stack -> item == stack.item })
        }

    fun selectThrowawayForLocation(
        select: Boolean,
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val maybe = maestro.builderTask.placeAt(x, y, z, maestro.bsi.get0(x, y, z)) ?: return false

        // Try exact state match first
        if (throwaway(select, { stack ->
                stack.item is BlockItem &&
                    maybe ==
                    (stack.item as BlockItem).block.getStateForPlacement(
                        BlockPlaceContext(
                            object : UseOnContext(
                                ctx.world(),
                                ctx.player(),
                                InteractionHand.MAIN_HAND,
                                stack,
                                BlockHitResult(
                                    Vec3(
                                        ctx.player().position().x,
                                        ctx.player().position().y,
                                        ctx.player().position().z,
                                    ),
                                    Direction.UP,
                                    ctx.playerFeet().toBlockPos(),
                                    false,
                                ),
                            ) {},
                        ),
                    )
            })
        ) {
            return true
        }

        // Try block type match
        if (throwaway(select, { stack ->
                stack.item is BlockItem &&
                    (stack.item as BlockItem).block == maybe.block
            })
        ) {
            return true
        }

        // Fall back to generic throwaway
        return Agent.settings().acceptableThrowawayItems.value.any { item ->
            throwaway(select, { stack -> item == stack.item })
        }
    }

    @JvmOverloads
    fun throwaway(
        select: Boolean,
        desired: (ItemStack) -> Boolean,
        allowInventory: Boolean = Agent.settings().allowInventory.value,
    ): Boolean {
        val player = ctx.player()
        val inventory = player.getInventory().items

        // Check hotbar first
        for (i in 0..8) {
            if (desired(inventory[i])) {
                if (select) {
                    player.getInventory().selected = i
                }
                return true
            }
        }

        // Check offhand
        if (desired(player.getInventory().offhand[0])) {
            // Need to select something inert in main hand
            for (i in 0..8) {
                val item = inventory[i]
                if (item.isEmpty || item.item is PickaxeItem) {
                    if (select) {
                        player.getInventory().selected = i
                    }
                    return true
                }
            }
        }

        // Check main inventory
        if (allowInventory) {
            for (i in 9..35) {
                if (desired(inventory[i])) {
                    if (select) {
                        requestSwapWithHotBar(i, 7)
                        player.getInventory().selected = 7
                    }
                    return true
                }
            }
        }

        return false
    }

    companion object {
        private val log: Logger = Loggers.Inventory.get()
    }
}
