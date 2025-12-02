package maestro.utils

import maestro.Agent
import maestro.api.utils.IPlayerContext
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

class BlockPlaceHelper internal constructor(
    private val ctx: IPlayerContext,
) {
    private var rightClickTimer = 0

    fun tick(rightClickRequested: Boolean) {
        if (rightClickTimer > 0) {
            rightClickTimer--
            return
        }

        val mouseOver = ctx.objectMouseOver()
        val player = ctx.player() ?: return

        if (!rightClickRequested ||
            player.isHandsBusy ||
            mouseOver == null ||
            mouseOver.type != HitResult.Type.BLOCK
        ) {
            return
        }

        rightClickTimer = Agent.settings().rightClickSpeed.value - BASE_PLACE_DELAY

        for (hand in InteractionHand.entries) {
            if (ctx.playerController().processRightClickBlock(
                    player,
                    ctx.world(),
                    hand,
                    mouseOver as BlockHitResult,
                ) == InteractionResult.SUCCESS
            ) {
                player.swing(hand)
                return
            }

            if (!player.getItemInHand(hand).isEmpty &&
                ctx.playerController().processRightClick(player, ctx.world(), hand)
                == InteractionResult.SUCCESS
            ) {
                return
            }
        }
    }

    companion object {
        // Base ticks between places caused by tick logic
        private const val BASE_PLACE_DELAY = 1
    }
}
