package maestro.pathing.movement.movements

import maestro.api.IAgent
import maestro.api.pathing.movement.ActionCosts
import maestro.api.pathing.movement.MovementStatus
import maestro.api.utils.IPlayerContext
import maestro.api.utils.PackedBlockPos
import maestro.pathing.movement.CalculationContext
import maestro.pathing.movement.ClickIntent
import maestro.pathing.movement.Intent
import maestro.pathing.movement.LookIntent
import maestro.pathing.movement.Movement
import maestro.pathing.movement.MovementHelper
import maestro.pathing.movement.MovementIntent
import maestro.utils.BlockStateInterface
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks

/**
 * Downward movement by one block (breaking the block below if needed).
 *
 * This movement type handles:
 * - Descending one block by breaking the block below
 * - Climbing down ladders/vines
 */
class MovementDownward(
    maestro: IAgent,
    src: PackedBlockPos,
    dest: PackedBlockPos,
) : Movement(maestro, src, dest) {
    override fun toBreak(bsi: BlockStateInterface): List<BlockPos> {
        // Break the block below our feet (dest position) if it's not already passable
        if (MovementHelper.canWalkThrough(ctx, dest)) {
            return emptyList()
        }
        return listOf(dest.toBlockPos())
    }

    override fun checkCompletion() {
        if (ctx.player().blockPosition() == dest.toBlockPos()) {
            state.setStatus(MovementStatus.SUCCESS)
        } else if (ctx.player().blockPosition() != src.toBlockPos() &&
            ctx.player().blockPosition() != dest.toBlockPos()
        ) {
            state.setStatus(MovementStatus.UNREACHABLE)
        }
    }

    override fun computeIntent(ctx: IPlayerContext): Intent {
        val playerPos = ctx.player().position()
        val destCenter =
            net.minecraft.world.phys
                .Vec3(dest.x + 0.5, dest.y.toDouble(), dest.z + 0.5)

        // Debug: Show player position to destination line
        debug.line("player-dest", playerPos, destCenter, java.awt.Color.GREEN)
        debug.point("dest-center", destCenter, java.awt.Color.YELLOW, 0.15f)

        // Check if we need to break the block below
        val needsBreaking = !MovementHelper.canWalkThrough(ctx, dest)

        // Debug: Distance and velocity metrics
        val distVertical = kotlin.math.abs(playerPos.y - dest.y)
        val velocity = ctx.player().deltaMovement
        debug.metric("dist", distVertical)
        debug.metric("vel", kotlin.math.abs(velocity.y))

        // Debug: Y position status
        val playerY = ctx.player().blockPosition().y
        debug.status("y", if (playerY == dest.y) "✓" else "$playerY→${dest.y}")

        // Debug: Mining and falling status
        if (needsBreaking) {
            debug.block("to-break", dest.toBlockPos(), java.awt.Color.CYAN, 0.5f)
            debug.line(
                "break-block",
                ctx.playerHead(),
                destCenter,
                java.awt.Color.RED,
            )
            debug.status("mine", "block")
        } else {
            debug.status("mine", "none")
        }

        debug.flag("break", needsBreaking)
        debug.flag("fall", velocity.y < -0.01)

        return Intent(
            movement = MovementIntent.Stop, // Stay still and let gravity pull us down
            look = LookIntent.Block(dest.toBlockPos()), // Look at the block we're breaking
            click = if (needsBreaking) ClickIntent.LeftClick else ClickIntent.None,
        )
    }

    override fun calculateCost(context: CalculationContext): Double = cost(context, src.x, src.y, src.z)

    override val validPositions: Set<PackedBlockPos>
        get() = setOf(src, dest)

    companion object {
        @JvmStatic
        fun cost(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
        ): Double {
            if (!context.allowDownward) {
                return ActionCosts.COST_INF
            }
            if (!MovementHelper.canWalkOn(context, x, y - 2, z)) {
                return ActionCosts.COST_INF
            }
            val down = context[x, y - 1, z]
            val downBlock = down.block
            return if (downBlock == Blocks.LADDER || downBlock == Blocks.VINE) {
                ActionCosts.LADDER_DOWN_ONE_COST
            } else {
                // Standing on block that might be falling, but will be air by the time we get here
                ActionCosts.FALL_N_BLOCKS_COST[1] +
                    MovementHelper.getMiningDurationTicks(context, x, y - 1, z, down, false)
            }
        }
    }
}
