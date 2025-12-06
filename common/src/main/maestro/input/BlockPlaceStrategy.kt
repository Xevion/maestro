package maestro.input

import maestro.Agent
import maestro.api.player.PlayerContext
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

class BlockPlaceStrategy : BlockInteractionStrategy {
    override fun interact(ctx: PlayerContext): Int? {
        val mouseOver = ctx.objectMouseOver()
        val player = ctx.player() ?: return null

        if (player.isHandsBusy ||
            mouseOver == null ||
            mouseOver.type != HitResult.Type.BLOCK
        ) {
            return null
        }

        for (hand in InteractionHand.entries) {
            if (ctx.playerController().processRightClickBlock(
                    player,
                    ctx.world(),
                    hand,
                    mouseOver as BlockHitResult,
                ) == InteractionResult.SUCCESS
            ) {
                player.swing(hand)
                return Agent
                    .getPrimaryAgent()
                    .settings.rightClickSpeed.value - BASE_PLACE_DELAY
            }

            if (!player.getItemInHand(hand).isEmpty &&
                ctx.playerController().processRightClick(player, ctx.world(), hand)
                == InteractionResult.SUCCESS
            ) {
                return Agent
                    .getPrimaryAgent()
                    .settings.rightClickSpeed.value - BASE_PLACE_DELAY
            }
        }

        return null
    }

    companion object {
        // Base ticks between places caused by tick logic
        private const val BASE_PLACE_DELAY = 1
    }
}
