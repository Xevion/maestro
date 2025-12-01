package maestro.utils

import maestro.api.MaestroAPI
import maestro.api.utils.IPlayerContext
import maestro.utils.accessor.IPlayerControllerMP
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

class BlockBreakHelper internal constructor(
    private val ctx: IPlayerContext,
) {
    private var wasHitting = false
    private var breakDelayTimer = 0

    fun stopBreakingBlock() {
        // The player controller will never be null, but the player can be
        ctx.player()?.let {
            if (wasHitting) {
                ctx.playerController().setHittingBlock(false)
                ctx.playerController().resetBlockRemoving()
                wasHitting = false
            }
        }
    }

    fun tick(isLeftClick: Boolean) {
        if (breakDelayTimer > 0) {
            breakDelayTimer--
            return
        }

        val trace = ctx.objectMouseOver()
        val isBlockTrace = trace != null && trace.type == HitResult.Type.BLOCK

        if (isLeftClick && isBlockTrace && trace is BlockHitResult) {
            ctx.playerController().setHittingBlock(wasHitting)
            if (ctx.playerController().hasBrokenBlock()) {
                ctx.playerController().syncHeldItem()
                ctx.playerController().clickBlock(trace.blockPos, trace.direction)
                ctx.player()?.swing(InteractionHand.MAIN_HAND)
            } else {
                if (ctx.playerController().onPlayerDamageBlock(trace.blockPos, trace.direction)) {
                    ctx.player()?.swing(InteractionHand.MAIN_HAND)
                }
                if (ctx.playerController().hasBrokenBlock()) {
                    // Block broken this tick
                    // Break delay timer only applies for multi-tick block breaks like vanilla
                    breakDelayTimer = MaestroAPI.getSettings().blockBreakSpeed.value - BASE_BREAK_DELAY
                    // Must reset controllers destroy delay to prevent the client from delaying itself unnecessarily
                    (ctx.minecraft().gameMode as IPlayerControllerMP).setDestroyDelay(0)
                }
            }
            // If true, we're breaking a block. If false, we broke the block this tick
            wasHitting = !ctx.playerController().hasBrokenBlock()
            // This value will be reset by the MC client handling mouse keys
            // Since we're not spoofing the click keybind to the client, the client will stop the
            // break if isDestroyingBlock is true
            // We store and restore this value on the next tick to determine if we're breaking a block
            ctx.playerController().setHittingBlock(false)
        } else {
            wasHitting = false
        }
    }

    companion object {
        // Base ticks between block breaks caused by tick logic
        private const val BASE_BREAK_DELAY = 1
    }
}
