package maestro.pathing.movement.movements

import maestro.Agent
import maestro.api.pathing.movement.ActionCosts
import maestro.api.pathing.movement.MovementStatus
import maestro.api.player.PlayerContext
import maestro.api.utils.PackedBlockPos
import maestro.pathing.BlockStateInterface
import maestro.pathing.movement.CalculationContext
import maestro.pathing.movement.ClickIntent
import maestro.pathing.movement.Intent
import maestro.pathing.movement.LookIntent
import maestro.pathing.movement.Movement
import maestro.pathing.movement.MovementIntent
import maestro.pathing.movement.MovementSpeed
import maestro.pathing.movement.MovementValidation
import maestro.utils.center
import maestro.utils.centerXZ
import maestro.utils.horizontalDistanceTo
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks

/**
 * Downward movement by one block (breaking the block below if needed).
 *
 * This movement type handles:
 * - Descending one block by breaking the block below
 * - Climbing down ladders/vines
 *
 * Implementation notes:
 * - Uses pre-centering to avoid getting stuck on block edges when descending
 * - Centers horizontally before allowing gravity to pull the player down
 * - Pre-centering may not always be valid for blocks with non-typical collision boxes
 *   (e.g., open trapdoors, glass panes, iron bars, end rods)
 */
class MovementDownward(
    maestro: Agent,
    src: PackedBlockPos,
    dest: PackedBlockPos,
) : Movement(maestro, src, dest) {
    override fun toBreak(bsi: BlockStateInterface): List<BlockPos> {
        // Break the block below our feet (dest position) if it's not already passable
        if (MovementValidation.canWalkThrough(ctx, dest)) {
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

    override fun computeIntent(ctx: PlayerContext): Intent {
        val playerPos = ctx.player().position()
        val destCenter = dest.toBlockPos().center

        // Debug: Show player position to destination line
        debug.line("player-dest", playerPos, destCenter, java.awt.Color.GREEN)
        debug.point("dest-center", destCenter, java.awt.Color.YELLOW, 0.15f)

        // Check if we need to break the block below
        val needsBreaking = !MovementValidation.canWalkThrough(ctx, dest)

        // Calculate horizontal distance from dest center
        val centerXZ = dest.toBlockPos().centerXZ
        val distXZ = playerPos.horizontalDistanceTo(centerXZ)

        // Debug: Distance and velocity metrics
        val distVertical = kotlin.math.abs(playerPos.y - dest.y)
        val velocity = ctx.player().deltaMovement
        debug.metric("dist", distVertical)
        debug.metric("distXZ", distXZ)
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

        // Phase 1: Center horizontally before descending to avoid edge-sticking
        // This prevents getting stuck on block edges when falling
        if (distXZ > 0.15) {
            debug.status("phase", "center")
            return Intent(
                movement =
                    MovementIntent.Toward(
                        target = centerXZ,
                        speed = MovementSpeed.WALK,
                        startPos = src.toBlockPos().centerXZ,
                    ),
                look = LookIntent.Block(dest.toBlockPos()),
                click = if (needsBreaking) ClickIntent.LeftClick else ClickIntent.None,
            )
        }

        // Phase 2: Centered - now safe to descend
        debug.status("phase", "fall")
        return Intent(
            movement = MovementIntent.Stop, // Stay still and let gravity pull us down
            look = LookIntent.Block(dest.toBlockPos()),
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
            if (!MovementValidation.canWalkOn(context, x, y - 2, z)) {
                return ActionCosts.COST_INF
            }
            val down = context[x, y - 1, z]
            val downBlock = down.block
            return if (downBlock == Blocks.LADDER || downBlock == Blocks.VINE) {
                ActionCosts.LADDER_DOWN_ONE_COST
            } else {
                // Standing on block that might be falling, but will be air by the time we get here
                ActionCosts.FALL_N_BLOCKS_COST[1] +
                    MovementValidation.getMiningDurationTicks(context, x, y - 1, z, down, false)
            }
        }
    }
}
