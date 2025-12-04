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
import maestro.pathing.movement.MovementSpeed
import maestro.utils.BlockStateInterface
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FallingBlock
import net.minecraft.world.phys.Vec2
import kotlin.math.abs
import kotlin.math.sqrt

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
        val playerXZ = Vec2(playerPos.x.toFloat(), playerPos.z.toFloat())
        cachedDistToTarget =
            sqrt(
                (
                    (cachedTarget!!.x - playerXZ.x) * (cachedTarget!!.x - playerXZ.x) +
                        (cachedTarget!!.y - playerXZ.y) * (cachedTarget!!.y - playerXZ.y)
                ).toDouble(),
            )

        // Debug: Show player position to destination line
        val destCenter =
            net.minecraft.world.phys
                .Vec3(dest.x + 0.5, dest.y.toDouble(), dest.z + 0.5)
        debug.line("player-dest", playerPos, destCenter, java.awt.Color.GREEN)

        // Debug: Show edge target point and velocity-compensated aim point
        val edgeTargetPos =
            net.minecraft.world.phys
                .Vec3(cachedTarget!!.x.toDouble(), dest.y.toDouble(), cachedTarget!!.y.toDouble())
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
        val srcCenter = Vec2(src.x + 0.5f, src.z + 0.5f)
        val destCenter2D = Vec2(dest.x + 0.5f, dest.z + 0.5f)
        val driftDist = calculatePerpendicularDistance(playerXZ, srcCenter, destCenter2D)
        if (driftDist > 0.1) {
            val driftColor =
                when (driftState) {
                    DriftState.SEVERE -> java.awt.Color.RED
                    DriftState.MODERATE -> java.awt.Color.ORANGE
                    DriftState.ALIGNED -> java.awt.Color.GREEN
                }
            // Calculate closest point on path line for drift visualization
            val closestPoint = calculateClosestPointOnPath(playerPos, srcCenter, destCenter2D)
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
                val srcCenterVec3 =
                    net.minecraft.world.phys
                        .Vec3(srcCenter.x.toDouble(), src.y.toDouble(), srcCenter.y.toDouble())
                val distToSrc =
                    sqrt(
                        (playerPos.x - srcCenter.x) * (playerPos.x - srcCenter.x) +
                            (playerPos.z - srcCenter.y) * (playerPos.z - srcCenter.y),
                    )

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
        val velocityXZ = sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
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
                    startPos = Vec2(src.x + 0.5f, src.z + 0.5f),
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
        // Allow for jump height difference (src.y to dest.y = 1 block)
        // But if player falls below src or goes too far above dest, it's severe drift
        val yDrift =
            when {
                playerY < src.y -> abs(playerY - src.y).toDouble() // Fell below source
                playerY > dest.y -> abs(playerY - dest.y).toDouble() // Jumped too high
                else -> 0.0 // Within expected range [src.y, dest.y]
            }

        if (yDrift > 0.95) {
            return DriftState.SEVERE
        }

        // Calculate XZ drift from intended path (line from source to destination)
        val srcCenter = Vec2(src.x + 0.5f, src.z + 0.5f)
        val destCenter = Vec2(dest.x + 0.5f, dest.z + 0.5f)
        val playerXZ = Vec2(playerPos.x.toFloat(), playerPos.z.toFloat())

        // Calculate perpendicular distance from player to line segment (src -> dest)
        val xzDrift = calculatePerpendicularDistance(playerXZ, srcCenter, destCenter)

        return when {
            xzDrift > 2.5 -> DriftState.SEVERE
            xzDrift > 0.5 -> DriftState.MODERATE
            else -> DriftState.ALIGNED
        }
    }

    /**
     * Calculate perpendicular distance from point to line segment.
     *
     * Uses the formula: |((x2-x1)(y1-y0) - (x1-x0)(y2-y1))| / sqrt((x2-x1)^2 + (y2-y1)^2)
     * where (x0,y0) is the point and (x1,y1)-(x2,y2) is the line segment.
     */
    private fun calculatePerpendicularDistance(
        point: Vec2,
        lineStart: Vec2,
        lineEnd: Vec2,
    ): Double {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val lineLengthSquared = dx * dx + dy * dy

        if (lineLengthSquared < 0.0001) {
            // Line segment is essentially a point
            val dpx = point.x - lineStart.x
            val dpy = point.y - lineStart.y
            return sqrt((dpx * dpx + dpy * dpy).toDouble())
        }

        // Calculate cross product magnitude
        val cross = abs(dx * (lineStart.y - point.y) - (lineStart.x - point.x) * dy)
        return cross / sqrt(lineLengthSquared.toDouble())
    }

    /**
     * Calculate closest point on path line for drift visualization.
     *
     * Projects player position onto the line from source to destination.
     */
    private fun calculateClosestPointOnPath(
        playerPos: net.minecraft.world.phys.Vec3,
        srcCenter: Vec2,
        destCenter: Vec2,
    ): net.minecraft.world.phys.Vec3 {
        // Calculate projection onto line segment
        val dx = destCenter.x - srcCenter.x
        val dz = destCenter.y - srcCenter.y
        val lineLengthSquared = dx * dx + dz * dz

        if (lineLengthSquared < 0.0001) {
            // Line is essentially a point
            return net.minecraft.world.phys
                .Vec3(srcCenter.x.toDouble(), playerPos.y, srcCenter.y.toDouble())
        }

        // Calculate t parameter (0 to 1) along line segment
        val t = ((playerPos.x - srcCenter.x) * dx + (playerPos.z - srcCenter.y) * dz) / lineLengthSquared
        val clampedT = t.toFloat().coerceIn(0.0f, 1.0f)

        // Calculate closest point
        val closestX = srcCenter.x + dx * clampedT
        val closestZ = srcCenter.y + dz * clampedT

        return net.minecraft.world.phys
            .Vec3(closestX.toDouble(), playerPos.y, closestZ.toDouble())
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
        val destCenter = Vec2(dest.x + 0.5f, dest.z + 0.5f)
        val distToDest =
            sqrt(
                (playerPos.x - destCenter.x) * (playerPos.x - destCenter.x) +
                    (playerPos.z - destCenter.y) * (playerPos.z - destCenter.y),
            )

        // Calculate direction from source to destination
        val dirX = dest.x - src.x
        val dirZ = dest.z - src.z
        val dirLength = sqrt((dirX * dirX + dirZ * dirZ).toDouble())
        val normalizedDirX = if (dirLength > 0.0001) dirX / dirLength else 0.0
        val normalizedDirZ = if (dirLength > 0.0001) dirZ / dirLength else 0.0

        // When close to destination, target the block edge facing the source
        if (distToDest < 1.5) {
            // Calculate edge point (0.3 blocks from block center in source direction)
            val edgeOffset = 0.3 // Distance from center to edge target
            val edgeX = destCenter.x - (normalizedDirX * edgeOffset).toFloat()
            val edgeZ = destCenter.y - (normalizedDirZ * edgeOffset).toFloat()

            // Compensate for velocity to ensure we land on target
            // Sprint-jumping: ~0.356 blocks/tick, but we'll use actual velocity
            // Jump takes ~6 ticks to reach peak, ~12 ticks total
            // Compensate for roughly half the jump duration (peak to landing)
            val ticksToLanding = 6.0 // Approximate ticks from jump peak to landing

            return Vec2(
                (edgeX + velocity.x.toFloat() * ticksToLanding).toFloat(),
                (edgeZ + velocity.z.toFloat() * ticksToLanding).toFloat(),
            )
        } else {
            // Far away: target center with slight velocity lead
            val ticksToApproach = 3.0 // Look ahead a few ticks
            return Vec2(
                (destCenter.x + velocity.x.toFloat() * ticksToApproach).toFloat(),
                (destCenter.y + velocity.z.toFloat() * ticksToApproach).toFloat(),
            )
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
        val playerXZ = Vec2(player.x.toFloat(), player.z.toFloat())

        // Use cached target (actual target, not dest center)
        val target = cachedTarget ?: Vec2(dest.x + 0.5f, dest.z + 0.5f) // Fallback to center

        // Calculate if velocity is toward target
        val toTargetX = target.x - playerXZ.x
        val toTargetZ = target.y - playerXZ.y
        val dotProduct = velocity.x * toTargetX + velocity.z * toTargetZ

        // If moving away from target (negative dot product), don't jump yet
        if (dotProduct < 0.0 && velocity.horizontalDistanceSqr() > 0.01) {
            return false
        }

        val velocityXZ = sqrt(velocity.x * velocity.x + velocity.z * velocity.z)

        // Jump timing strategy:
        // - If far (>0.4 blocks): require good velocity (0.2+) to use jump arc efficiently
        // - If close (<0.4 blocks): allow jump even with lower velocity to avoid hitting wall
        return if (cachedDistToTarget > 0.52) {
            // Far enough to benefit from velocity - require sprint/walk speed
            velocityXZ > 0.2 && cachedDistToTarget < 0.8
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
