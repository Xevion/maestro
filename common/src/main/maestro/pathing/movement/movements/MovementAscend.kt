package maestro.pathing.movement.movements

import maestro.api.IAgent
import maestro.api.pathing.movement.ActionCosts
import maestro.api.pathing.movement.MovementStatus
import maestro.api.utils.IPlayerContext
import maestro.api.utils.PackedBlockPos
import maestro.api.utils.center
import maestro.api.utils.centerXZ
import maestro.api.utils.normalizedDirectionTo
import maestro.pathing.BlockStateInterface
import maestro.pathing.movement.CalculationContext
import maestro.pathing.movement.ClickIntent
import maestro.pathing.movement.Intent
import maestro.pathing.movement.LookIntent
import maestro.pathing.movement.Movement
import maestro.pathing.movement.MovementHelper
import maestro.pathing.movement.MovementIntent
import maestro.pathing.movement.MovementSpeed
import maestro.utils.distanceTo
import maestro.utils.dot
import maestro.utils.horizontalDistanceTo
import maestro.utils.horizontalLength
import maestro.utils.minus
import maestro.utils.perpendicularDistanceTo
import maestro.utils.plus
import maestro.utils.projectOnto
import maestro.utils.times
import maestro.utils.toVec3XZ
import maestro.utils.xz
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FallingBlock
import net.minecraft.world.phys.Vec2
import kotlin.math.abs

/**
 * Jumping up one block vertically while moving forward horizontally.
 *
 * Example:
 * ```
 *   D   <- destination (1 block up)
 * S     <- source
 * ```
 */
class MovementAscend(
    maestro: IAgent,
    src: PackedBlockPos,
    dest: PackedBlockPos,
) : Movement(maestro, src, dest) {
    private var ticksWithoutPlacement = 0
    private var isRecentering = false

    // Computed once per tick, reused across computeIntent, shouldJump, and getDebugInfo
    private var cachedTarget: Vec2? = null
    private var cachedDistToTarget: Double = 0.0

    override fun reset() {
        super.reset()
        ticksWithoutPlacement = 0
        isRecentering = false
        cachedTarget = null
        cachedDistToTarget = 0.0
    }

    override fun toBreak(bsi: BlockStateInterface): List<BlockPos> {
        val blocks = mutableListOf<BlockPos>()

        // Top-to-bottom order
        if (!MovementHelper.canWalkThrough(ctx, dest.above())) {
            blocks.add(dest.above().toBlockPos())
        }
        if (!MovementHelper.canWalkThrough(ctx, src.above(2))) {
            blocks.add(src.above(2).toBlockPos())
        }
        if (!MovementHelper.canWalkThrough(ctx, dest)) {
            blocks.add(dest.toBlockPos())
        }

        return blocks
    }

    override fun calculateCost(context: CalculationContext): Double =
        MovementAscendHelper.cost(context, src.x, src.y, src.z, dest.x, dest.z)

    override fun checkCompletion() {
        val playerY = ctx.player().blockPosition().y

        // Failed if fell below source
        if (playerY < src.y) {
            state.setStatus(MovementStatus.UNREACHABLE)
            return
        }

        // Success if reached exact destination
        if (ctx.player().blockPosition() == dest.toBlockPos()) {
            state.setStatus(MovementStatus.SUCCESS)
        }
    }

    override fun computeIntent(ctx: IPlayerContext): Intent {
        // Calculate target once per tick and cache for shouldJump()
        cachedTarget = calculateVelocityBasedTarget(ctx)

        val playerPos = ctx.player().position()
        val playerXZ = playerPos.xz
        cachedDistToTarget = playerXZ.distanceTo(cachedTarget!!)

        // Debug: Show player position to destination line
        val destCenter = dest.center
        debug.line("player-dest", playerPos, destCenter, java.awt.Color.GREEN)

        // Debug: Show edge target point and velocity-compensated aim point
        val edgeTargetPos = cachedTarget!!.toVec3XZ(dest.y.toDouble())
        debug.point("edge-target", edgeTargetPos, java.awt.Color.YELLOW, 0.15f)
        debug.line("target-approach", playerPos, edgeTargetPos, java.awt.Color.CYAN)

        // Check blocks to mine (top-to-bottom order)
        val blockAboveDest = dest.above().toBlockPos()
        val blockTwoAbove = src.above(2).toBlockPos()
        val blockAtDest = dest.toBlockPos()

        // Mine blocks in order
        if (!MovementHelper.canWalkThrough(ctx, dest.above())) {
            return Intent(
                movement = MovementIntent.Stop,
                look = LookIntent.Block(blockAboveDest),
                click = ClickIntent.LeftClick,
            )
        }
        if (!MovementHelper.canWalkThrough(ctx, src.above(2))) {
            return Intent(
                movement = MovementIntent.Stop,
                look = LookIntent.Block(blockTwoAbove),
                click = ClickIntent.LeftClick,
            )
        }
        if (!MovementHelper.canWalkThrough(ctx, dest)) {
            return Intent(
                movement = MovementIntent.Stop,
                look = LookIntent.Block(blockAtDest),
                click = ClickIntent.LeftClick,
            )
        }

        // Check drift status and handle accordingly
        val driftState = checkDrift(ctx)

        // Debug: Drift visualization (perpendicular distance from ideal path)
        val srcCenter = src.centerXZ
        val destCenter2D = dest.centerXZ
        val driftDist = playerXZ.perpendicularDistanceTo(srcCenter, destCenter2D)
        if (driftDist > 0.1) {
            val driftColor =
                when (driftState) {
                    DriftState.SEVERE -> java.awt.Color.RED
                    DriftState.MODERATE -> java.awt.Color.ORANGE
                    DriftState.ALIGNED -> java.awt.Color.GREEN
                }
            // Calculate the closest point on path line for drift visualization
            val closestXZ = playerXZ.projectOnto(srcCenter, destCenter2D)
            val closestPoint = closestXZ.toVec3XZ(playerPos.y)
            debug.line("drift", playerPos, closestPoint, driftColor)
        }

        when (driftState) {
            DriftState.SEVERE -> {
                // Give up and let pathfinding recalculate
                state.setStatus(MovementStatus.UNREACHABLE)
                return Intent(
                    movement = MovementIntent.Stop,
                    look = LookIntent.None,
                    click = ClickIntent.None,
                )
            }

            DriftState.MODERATE -> {
                // Return to source to recenter
                isRecentering = true
                val srcCenterVec3 = srcCenter.toVec3XZ(src.y.toDouble())
                val distToSrc = playerPos.horizontalDistanceTo(srcCenter)

                // Debug: Show recentering path
                debug.line("recenter", playerPos, srcCenterVec3, java.awt.Color.ORANGE)

                // If recentered, resume ascent
                if (distToSrc < 0.3) {
                    isRecentering = false
                }

                return Intent(
                    movement =
                        MovementIntent.Toward(
                            target = srcCenter,
                            speed = MovementSpeed.WALK,
                            jump = false,
                            startPos = srcCenter,
                        ),
                    look = LookIntent.Block(src.toBlockPos()),
                    click = ClickIntent.None,
                )
            }

            DriftState.ALIGNED -> {
                // Normal ascent behavior
                isRecentering = false
            }
        }

        // Check if we've reached the target (use cached distance to actual target, not dest center)
        // Stop moving if we're very close and at the right height
        if (cachedDistToTarget < 0.3 && ctx.player().blockPosition().y >= dest.y) {
            return Intent(
                movement = MovementIntent.Stop,
                look = LookIntent.None,
                click = ClickIntent.None,
            )
        }

        // Debug: GUI metrics
        val velocity = ctx.player().deltaMovement
        val velocityXZ = velocity.horizontalLength
        debug.metric("dist", cachedDistToTarget)
        debug.metric("vel", velocityXZ)
        debug.status(
            "drift",
            when (driftState) {
                DriftState.ALIGNED -> "ok"
                DriftState.MODERATE -> if (isRecentering) "rectr" else "mod"
                DriftState.SEVERE -> "SEVERE"
            },
        )
        val playerY = ctx.player().blockPosition().y
        debug.status("y", if (playerY == dest.y) "✓" else "$playerY→${dest.y}")
        debug.flag("jump", shouldJump(ctx))
        val mining =
            when {
                !MovementHelper.canWalkThrough(ctx, dest.above()) -> "+1Y"
                !MovementHelper.canWalkThrough(ctx, src.above(2)) -> "+2Y"
                !MovementHelper.canWalkThrough(ctx, dest) -> "dest"
                else -> "none"
            }
        debug.status("mine", mining)

        // Continue ascending with drift correction (use cached target)
        return Intent(
            movement =
                MovementIntent.Toward(
                    target = cachedTarget!!,
                    speed = MovementSpeed.SPRINT,
                    jump = shouldJump(ctx),
                    startPos = src.centerXZ,
                ),
            look = LookIntent.Block(dest.toBlockPos()),
            click = ClickIntent.None,
        )
    }

    /**
     * Drift states for XZ-plane deviation from intended path.
     */
    private enum class DriftState {
        ALIGNED, // <0.5 blocks XZ drift
        MODERATE, // 0.5-2.5 blocks XZ drift
        SEVERE, // >2.5 blocks XZ drift or >0.95 Y drift
    }

    /**
     * Check player's drift from intended path (source -> dest line).
     *
     * Uses XZ-plane distance for horizontal drift and Y-axis for vertical drift.
     * Note: Y-drift detection excludes the jump itself (0 to 1 block difference is expected).
     */
    private fun checkDrift(ctx: IPlayerContext): DriftState {
        val playerPos = ctx.player().position()
        val playerY = ctx.player().blockPosition().y

        // Check Y-axis drift (vertical deviation)
        val yDrift =
            if (playerY in src.y..dest.y) {
                0.0
            } else {
                // Either fell below the source, or jumped too high
                val bound = if (playerY < src.y) src.y else dest.y
                // Distance from the closest range boundary
                abs(bound - playerY).toDouble()
            }

        // Allow for jump height difference (src.y to dest.y = 1 block)
        if (yDrift > 0.95) {
            return DriftState.SEVERE
        }

        // Calculate perpendicular distance from player to line segment (src -> dest)
        val xzDrift = playerPos.xz.perpendicularDistanceTo(this.src.centerXZ, this.dest.centerXZ)

        return when {
            xzDrift > 2.5 -> DriftState.SEVERE
            xzDrift > 0.5 -> DriftState.MODERATE
            else -> DriftState.ALIGNED
        }
    }

    /**
     * Calculate velocity-based target point for ascent movement.
     *
     * Uses Minecraft physics to predict optimal aiming point:
     * - Jump initial velocity: 0.42 blocks/tick upward
     * - Gravity: -0.08 blocks/tick² (deceleration)
     * - Drag: 0.98 multiplier per tick
     * - Sprint speed: ~0.281 blocks/tick horizontal
     * - Jump duration: 12 ticks to land
     *
     * TODO: Consider blocks with unusual hitboxes (End Rods, Glass Panes, Iron Bars).
     *       Currently assumes 1x1x1 hitbox for all blocks.
     */
    private fun calculateVelocityBasedTarget(ctx: IPlayerContext): Vec2 {
        val playerPos = ctx.player().position()
        val velocity = ctx.player().deltaMovement

        // Calculate distance to destination center
        val destCenter = dest.centerXZ
        val distToDest = playerPos.horizontalDistanceTo(destCenter)

        // Calculate normalized direction from source to destination
        val normalizedDir = src.normalizedDirectionTo(dest)

        // When close to destination, target the block edge facing the source
        if (distToDest < 1.5) {
            // Calculate edge point (0.3 blocks from block center in source direction)
            val edgeOffset = 0.3f
            val edge = destCenter - normalizedDir * edgeOffset

            // Compensate for velocity to ensure we land on target
            // Sprint-jumping: ~0.356 blocks/tick, but we'll use actual velocity
            // Jump takes ~6 ticks to reach peak, ~12 ticks total
            // Compensate for roughly half the jump duration (peak to landing)
            val ticksToLanding = 6.0f
            val velocityXZ = velocity.xz

            return edge + velocityXZ * ticksToLanding
        } else {
            // Far away: target center with slight velocity lead
            val ticksToApproach = 3.0f
            val velocityXZ = velocity.xz

            return destCenter + velocityXZ * ticksToApproach
        }
    }

    private fun shouldJump(ctx: IPlayerContext): Boolean {
        val player = ctx.player()
        val playerY = player.blockPosition().y

        // Don't jump if already at destination height
        if (playerY >= dest.y) {
            return false
        }

        // Must be on ground to jump (can't jump midair)
        if (!player.onGround()) {
            return false
        }

        // Head-bonk detection: Don't jump if there's a block above that would block us
        val playerPos = player.blockPosition()
        val abovePos = playerPos.above()
        val aboveState = ctx.world().getBlockState(abovePos)

        // Check if block above has collision (would cause head-bonking)
        if (!aboveState.getCollisionShape(ctx.world(), abovePos).isEmpty) {
            return false
        }

        // Check velocity: Don't jump if moving away from target
        val velocity = player.deltaMovement
        val playerXZ = player.position().xz

        // Use cached target (actual target, not dest center)
        val target = cachedTarget ?: dest.centerXZ // Fallback to center

        // Calculate if velocity is toward target
        val toTarget = target - playerXZ
        val velocityXZ = velocity.xz
        val dotProduct = velocityXZ dot toTarget

        // If moving away from target (negative dot product), don't jump yet
        if (dotProduct < 0.0 && velocity.horizontalDistanceSqr() > 0.01) {
            return false
        }

        // Jump timing strategy:
        // - If far (>0.4 blocks): require good velocity (0.2+) to use jump arc efficiently
        // - If close (<0.4 blocks): allow jump even with lower velocity to avoid hitting wall
        return if (cachedDistToTarget > 0.52) {
            // Far enough to benefit from velocity - require sprint/walk speed
            velocityXZ.length() > 0.2 && cachedDistToTarget < 0.8
        } else {
            // Very close - jump even if slowing down to avoid wall collision
            cachedDistToTarget <= 0.53
        }
    }

    override fun safeToCancel(): Boolean {
        // Not safe to cancel if we've started placing blocks
        return state.getStatus() != MovementStatus.RUNNING || ticksWithoutPlacement == 0
    }

    override val validPositions: Set<PackedBlockPos>
        get() {
            val dirVec = direction
            val prior =
                PackedBlockPos(
                    src.x - dirVec.x,
                    src.y + 1,
                    src.z - dirVec.z,
                )
            return setOf(src, src.above(), dest, prior, prior.above())
        }

    companion object {
        @JvmStatic
        fun cost(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
            destX: Int,
            destZ: Int,
        ): Double = MovementAscendHelper.cost(context, x, y, z, destX, destZ)
    }
}

/**
 * Helper object for MovementAscend cost calculation.
 */
internal object MovementAscendHelper {
    fun cost(
        context: CalculationContext,
        x: Int,
        y: Int,
        z: Int,
        destX: Int,
        destZ: Int,
    ): Double {
        val toPlace = context[destX, y, destZ]
        var additionalPlacementCost = 0.0

        // Check if we need to place a block
        if (!MovementHelper.canWalkOn(context, destX, y, destZ, toPlace)) {
            additionalPlacementCost = context.costOfPlacingAt(destX, y, destZ, toPlace)
            if (additionalPlacementCost >= ActionCosts.COST_INF) {
                return ActionCosts.COST_INF
            }

            if (!MovementHelper.isReplaceable(destX, y, destZ, toPlace, context.bsi)) {
                return ActionCosts.COST_INF
            }

            // Check if we can place against something
            var foundPlaceOption = false
            for (i in 0 until 5) {
                val dir = Movement.HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i]
                val againstX = destX + dir.stepX
                val againstY = y + dir.stepY
                val againstZ = destZ + dir.stepZ

                if (againstX == x && againstZ == z) {
                    continue // backplace - will be broken
                }

                if (MovementHelper.canPlaceAgainst(context.bsi, againstX, againstY, againstZ)) {
                    foundPlaceOption = true
                    break
                }
            }

            if (!foundPlaceOption) {
                return ActionCosts.COST_INF
            }
        }

        // Check for falling blocks that would suffocate us
        val srcUp2 = context[x, y + 2, z]
        if (context[x, y + 3, z].block is FallingBlock &&
            (MovementHelper.canWalkThrough(context, x, y + 1, z) || srcUp2.block !is FallingBlock)
        ) {
            return ActionCosts.COST_INF
        }

        // Check if we're on a ladder/vine
        val srcDown = context[x, y - 1, z]
        if (srcDown.block == Blocks.LADDER || srcDown.block == Blocks.VINE) {
            return ActionCosts.COST_INF
        }

        // Handle slab-to-slab jumps
        val jumpingFromBottomSlab = MovementHelper.isBottomSlab(srcDown)
        val jumpingToBottomSlab = MovementHelper.isBottomSlab(toPlace)

        if (jumpingFromBottomSlab && !jumpingToBottomSlab) {
            return ActionCosts.COST_INF
        }

        val walk =
            if (jumpingToBottomSlab) {
                if (jumpingFromBottomSlab) {
                    ActionCosts.JUMP_ONE_BLOCK_COST.coerceAtLeast(ActionCosts.WALK_ONE_BLOCK_COST) + context.jumpPenalty
                } else {
                    ActionCosts.WALK_ONE_BLOCK_COST
                }
            } else {
                val baseCost =
                    if (toPlace.block == Blocks.SOUL_SAND) {
                        ActionCosts.WALK_ONE_OVER_SOUL_SAND_COST
                    } else {
                        ActionCosts.JUMP_ONE_BLOCK_COST.coerceAtLeast(ActionCosts.WALK_ONE_BLOCK_COST)
                    }
                baseCost + context.jumpPenalty
            }

        var totalCost = walk + additionalPlacementCost

        // Add block breaking costs
        totalCost += MovementHelper.getMiningDurationTicks(context, x, y + 2, z, srcUp2, false)
        if (totalCost >= ActionCosts.COST_INF) {
            return ActionCosts.COST_INF
        }

        totalCost += MovementHelper.getMiningDurationTicks(context, destX, y + 1, destZ, false)
        if (totalCost >= ActionCosts.COST_INF) {
            return ActionCosts.COST_INF
        }

        totalCost += MovementHelper.getMiningDurationTicks(context, destX, y + 2, destZ, true)
        return totalCost
    }
}
