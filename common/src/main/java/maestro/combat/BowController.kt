package maestro.combat

import maestro.api.utils.IPlayerContext
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ArrowItem
import net.minecraft.world.item.BowItem

/**
 * Manages bow charging, releasing, and inventory selection for ranged combat.
 */
class BowController(
    private val ctx: IPlayerContext,
) {
    private var chargingStartTick: Int = -1
    private var currentBowSlot: Int = -1

    /**
     * Check if player is currently holding a bow in main hand.
     */
    fun isHoldingBow(): Boolean {
        val mainHand = ctx.player().mainHandItem
        return mainHand.item is BowItem
    }

    /**
     * Find bow in hotbar and switch to it.
     *
     * @return true if bow was found and selected
     */
    fun selectBow(): Boolean {
        // Check if already holding bow
        if (isHoldingBow()) return true

        // Search hotbar for bow
        for (slot in 0..8) {
            val stack = ctx.player().inventory.getItem(slot)
            if (stack.item is BowItem) {
                ctx.player().inventory.selected = slot
                currentBowSlot = slot
                return true
            }
        }

        return false
    }

    /**
     * Start charging the bow by sending use item packet.
     * Only works if holding a bow.
     */
    fun startCharging() {
        if (!isHoldingBow()) return

        chargingStartTick = ctx.player().tickCount

        // Send use item packet to start charging
        ctx.minecraft().gameMode!!.useItem(
            ctx.player(),
            InteractionHand.MAIN_HAND,
        )
    }

    /**
     * Get current charge progress.
     *
     * @return Charge progress from 0.0 (not charged) to 1.0 (fully charged)
     */
    fun getChargeProgress(): Float {
        if (chargingStartTick < 0) return 0.0f
        if (!ctx.player().isUsingItem) return 0.0f

        // Calculate progress manually using bow charge ticks
        val useTicks = ctx.player().getTicksUsingItem()
        val progress = useTicks.toFloat() / ProjectilePhysics.TICKS_FOR_FULL_CHARGE.toFloat()
        return progress.coerceIn(0.0f, 1.0f)
    }

    /**
     * Get current arrow velocity based on charge progress.
     *
     * @return Arrow velocity in blocks/tick
     */
    fun getChargeVelocity(): Double {
        if (chargingStartTick < 0) return 0.0

        val chargeTicks = ctx.player().tickCount - chargingStartTick
        return ProjectilePhysics.getVelocityFromCharge(chargeTicks)
    }

    /**
     * Check if bow is fully charged (100% charge).
     *
     * @return true if bow is at full charge
     */
    fun isFullyCharged(): Boolean = getChargeProgress() >= 1.0f

    /**
     * Check if bow is at minimum acceptable charge level.
     *
     * @param minimumCharge Minimum charge level (0.0 to 1.0)
     * @return true if bow charge meets or exceeds minimum
     */
    fun hasMinimumCharge(minimumCharge: Float): Boolean = getChargeProgress() >= minimumCharge

    /**
     * Release the arrow by stopping item use.
     * This fires the arrow if bow was being charged.
     */
    fun release() {
        if (chargingStartTick < 0) return

        // Stop using item (releases arrow)
        ctx.minecraft().gameMode!!.releaseUsingItem(ctx.player())

        // Reset charging state
        chargingStartTick = -1
    }

    /**
     * Cancel charging without shooting.
     * Stops using the bow without firing an arrow.
     */
    fun cancelCharge() {
        if (chargingStartTick < 0) return

        ctx.minecraft().gameMode!!.releaseUsingItem(ctx.player())
        chargingStartTick = -1
    }

    /**
     * Check if player has arrows in inventory.
     * In creative mode, arrows are not required.
     *
     * @return true if at least one arrow is available or player is in creative mode
     */
    fun hasArrows(): Boolean {
        // Creative mode doesn't require arrows
        if (ctx.player().abilities.instabuild) {
            return true
        }

        return ctx
            .player()
            .inventory.items
            .any { it.item is ArrowItem }
    }

    /**
     * Check if currently charging the bow.
     *
     * @return true if charging is in progress
     */
    fun isCharging(): Boolean = chargingStartTick >= 0

    /**
     * Get number of ticks the bow has been charging.
     *
     * @return Charge ticks, or 0 if not charging
     */
    fun getChargeTicks(): Int {
        if (chargingStartTick < 0) return 0
        return ctx.player().tickCount - chargingStartTick
    }
}
