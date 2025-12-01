package maestro.utils

import maestro.Agent
import net.minecraft.client.player.LocalPlayer
import net.minecraft.tags.ItemTags
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.SwordItem
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/**
 * A cached list of the best tools on the hotbar for any block.
 *
 * Tracks mining speed with different tools and enchantments, applying potion effects
 * when enabled in settings.
 */
class ToolSet(
    private val player: LocalPlayer,
) {
    /**
     * A cache mapping a [Block] to how long it will take to break with this toolset, given
     * the optimum tool is used.
     */
    private val breakStrengthCache: MutableMap<Block, Double> = HashMap()

    /** My buddy leijurv owned me so we have this to not create a new lambda instance. */
    private val backendCalculation: (Block) -> Double

    init {
        backendCalculation =
            if (Agent.settings().considerPotionEffects.value) {
                { block -> potionAmplifier() * getBestDestructionTime(block) }
            } else {
                ::getBestDestructionTime
            }
    }

    /**
     * Using the best tool on the hotbar, how fast we can mine this block.
     *
     * @param state the blockstate to be mined
     * @return the speed of how fast we'll mine it. 1/(time in ticks)
     */
    fun getStrVsBlock(state: BlockState): Double = breakStrengthCache.computeIfAbsent(state.block, backendCalculation)

    /**
     * Evaluate the material cost of a possible tool. If all else is equal, we want to prefer the
     * tool with the lowest material cost. i.e. we want to prefer a wooden pickaxe over a stone
     * pickaxe, if all else is equal.
     *
     * @param itemStack a possibly empty ItemStack
     * @return values from 0 up
     */
    private fun getMaterialCost(itemStack: ItemStack): Int {
        for (i in materialTagsPriorityList.indices) {
            val tag = materialTagsPriorityList[i]
            if (itemStack.`is`(tag)) return i
        }
        return -1
    }

    fun hasSilkTouch(stack: ItemStack): Boolean {
        val enchantments = stack.enchantments
        for (enchant in enchantments.keySet()) {
            // silk touch enchantment is still special cased as affecting block drops
            // not possible to add custom attribute via datapack
            if (enchant.`is`(Enchantments.SILK_TOUCH) && enchantments.getLevel(enchant) > 0) {
                return true
            }
        }
        return false
    }

    /**
     * Calculate which tool on the hotbar is best for mining, depending on an override setting,
     * related to auto tool movement cost, it will either return current selected slot, or the best
     * slot.
     *
     * @param b the blockstate to be mined
     * @return An int containing the index in the tools array that worked best
     */
    @JvmOverloads
    fun getBestSlot(
        b: Block,
        preferSilkTouch: Boolean,
        pathingCalculation: Boolean = false,
    ): Int {
        /*
        If we actually want know what efficiency our held item has instead of the best one
        possible, this lets us make pathing depend on the actual tool to be used (if auto tool is disabled)
         */
        if (!Agent.settings().autoTool.value && pathingCalculation) {
            return player.inventory.selected
        }

        var best = 0
        var highestSpeed = Double.NEGATIVE_INFINITY
        var lowestCost = Int.MIN_VALUE
        var bestSilkTouch = false
        val blockState = b.defaultBlockState()

        for (i in 0 until 9) {
            val itemStack = player.inventory.getItem(i)
            if (!Agent.settings().useSwordToMine.value && itemStack.item is SwordItem) {
                continue
            }

            if (Agent.settings().itemSaver.value &&
                (itemStack.damageValue + Agent.settings().itemSaverThreshold.value) >= itemStack.maxDamage &&
                itemStack.maxDamage > 1
            ) {
                continue
            }

            val speed = calculateSpeedVsBlock(itemStack, blockState)
            val silkTouch = hasSilkTouch(itemStack)

            if (speed > highestSpeed) {
                highestSpeed = speed
                best = i
                lowestCost = getMaterialCost(itemStack)
                bestSilkTouch = silkTouch
            } else if (speed == highestSpeed) {
                val cost = getMaterialCost(itemStack)
                if ((cost < lowestCost && (silkTouch || !bestSilkTouch)) ||
                    (preferSilkTouch && !bestSilkTouch && silkTouch)
                ) {
                    highestSpeed = speed
                    best = i
                    lowestCost = cost
                    bestSilkTouch = silkTouch
                }
            }
        }
        return best
    }

    /**
     * Calculate how effectively a block can be destroyed.
     *
     * @param b the blockstate to be mined
     * @return A double containing the destruction ticks with the best tool
     */
    private fun getBestDestructionTime(b: Block): Double {
        val stack = player.inventory.getItem(getBestSlot(b, preferSilkTouch = false, pathingCalculation = true))
        return calculateSpeedVsBlock(stack, b.defaultBlockState()) * avoidanceMultiplier(b)
    }

    private fun avoidanceMultiplier(b: Block): Double =
        if (Agent
                .settings()
                .blocksToAvoidBreaking.value
                .contains(b)
        ) {
            Agent.settings().avoidBreakingMultiplier.value
        } else {
            1.0
        }

    /**
     * Calculates any modifier to breaking time based on status effects.
     *
     * @return a double to scale block breaking speed.
     */
    private fun potionAmplifier(): Double {
        var speed = 1.0
        if (player.hasEffect(MobEffects.DIG_SPEED)) {
            speed *= 1 + (player.getEffect(MobEffects.DIG_SPEED)!!.amplifier + 1) * 0.2
        }
        if (player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            speed *=
                when (player.getEffect(MobEffects.DIG_SLOWDOWN)!!.amplifier) {
                    0 -> 0.3
                    1 -> 0.09
                    2 -> 0.0027 // you might think that 0.09*0.3 = 0.027 so that should be next,
                    // that would make too much sense. it's 0.0027.
                    else -> 0.00081
                }
        }
        return speed
    }

    companion object {
        /**
         * Used for evaluating the material cost of a tool. see [getMaterialCost]
         * Prefer tools with lower material cost (lower index in this list).
         */
        private val materialTagsPriorityList =
            listOf(
                ItemTags.WOODEN_TOOL_MATERIALS,
                ItemTags.STONE_TOOL_MATERIALS,
                ItemTags.IRON_TOOL_MATERIALS,
                ItemTags.GOLD_TOOL_MATERIALS,
                ItemTags.DIAMOND_TOOL_MATERIALS,
                ItemTags.NETHERITE_TOOL_MATERIALS,
            )

        /**
         * Calculates how long would it take to mine the specified block given the best tool in this
         * toolset is used. A negative value is returned if the specified block is unbreakable.
         *
         * @param item the item to mine it with
         * @param state the blockstate to be mined
         * @return how long it would take in ticks
         */
        @JvmStatic
        fun calculateSpeedVsBlock(
            item: ItemStack,
            state: BlockState,
        ): Double {
            val hardness: Float =
                try {
                    state.getDestroySpeed(
                        null as net.minecraft.world.level.BlockGetter?,
                        null as net.minecraft.core.BlockPos?,
                    )
                } catch (npe: NullPointerException) {
                    // can't easily determine the hardness so treat it as unbreakable
                    return -1.0
                }

            if (hardness < 0) {
                return -1.0
            }

            var speed = item.getDestroySpeed(state)
            if (speed > 1) {
                val itemEnchantments = item.enchantments
                run enchantLoop@{
                    for (enchant in itemEnchantments.keySet()) {
                        val effects = enchant.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES)
                        for (e in effects) {
                            if (e.attribute().`is`(Attributes.MINING_EFFICIENCY.unwrapKey().get())) {
                                speed += e.amount().calculate(itemEnchantments.getLevel(enchant))
                                return@enchantLoop
                            }
                        }
                    }
                }
            }

            speed /= hardness
            return if (!state.requiresCorrectToolForDrops() || (!item.isEmpty && item.isCorrectToolForDrops(state))) {
                (speed / 30).toDouble()
            } else {
                (speed / 100).toDouble()
            }
        }
    }
}
