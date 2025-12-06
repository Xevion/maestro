package maestro.pathing.movement.movements

import maestro.Agent
import maestro.api.pathing.movement.ActionCosts
import maestro.api.pathing.movement.MovementStatus
import maestro.api.player.PlayerContext
import maestro.api.utils.PackedBlockPos
import maestro.pathing.BlockStateInterface
import maestro.pathing.MutableMoveResult
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
import maestro.utils.distanceSquaredTo
import maestro.utils.lerp
import maestro.utils.minus
import maestro.utils.plus
import maestro.utils.toVec3XZ
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FallingBlock
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.max

/**
 * Descending one block vertically while moving forward horizontally.
 *
 * Example:
 * ```
 * S     <- source
 *   D   <- destination (1 block down)
 * ```
 */
class MovementDescend(
    maestro: Agent,
    src: PackedBlockPos,
    dest: PackedBlockPos,
) : Movement(maestro, src, dest) {
    var forceSafeMode: Boolean = false
    private var numTicks = 0

    override fun reset() {
        super.reset()
        forceSafeMode = false
        numTicks = 0
    }

    override fun toBreak(bsi: BlockStateInterface): List<BlockPos> {
        val blocks = mutableListOf<BlockPos>()

        if (!MovementValidation.canWalkThrough(ctx, dest.above(2))) {
            blocks.add(dest.above(2).toBlockPos())
        }
        if (!MovementValidation.canWalkThrough(ctx, dest.above())) {
            blocks.add(dest.above().toBlockPos())
        }
        if (!MovementValidation.canWalkThrough(ctx, dest)) {
            blocks.add(dest.toBlockPos())
        }

        return blocks
    }

    /**
     * Force this descend to use safe mode (called by PathExecutor).
     */
    fun forceSafeMode() {
        forceSafeMode = true
    }

    /**
     * Check if safe mode should be enabled.
     *
     * Safe mode prevents overshooting when there are obstacles in our path.
     * Instead of aiming past the edge (fakeDest), we aim closer to the edge (83% of distance).
     */
    fun safeMode(): Boolean {
        if (forceSafeMode) {
            return true
        }

        // Calculate the "into" position: where we'd end up if we overshoot
        // Formula: into = dest - (src.below() - dest) = dest + (dest - src.below())
        val into =
            dest
                .toBlockPos()
                .subtract(src.below().toBlockPos())
                .offset(dest.toBlockPos())

        // If skipToAscend would trigger, use safe mode
        if (skipToAscend()) {
            return true
        }

        // Check if any of the 3 blocks above "into" would be problematic to walk into
        for (y in 0..2) {
            val checkPos = into.above(y)
            val state = ctx.world().getBlockState(checkPos)
            if (MovementValidation.avoidWalkingInto(state)) {
                return true
            }
        }

        return false
    }

    /**
     * Check if we should skip to ascend movement.
     *
     * This checks if overshooting would put us into a 2-block-tall air column
     * above a solid block, which can cause collision glitches.
     */
    fun skipToAscend(): Boolean {
        // Calculate the "into" position: where we'd end up if we overshoot
        // Formula: into = dest + (dest - src.below())
        val into =
            dest
                .toBlockPos()
                .subtract(src.below().toBlockPos())
                .offset(dest.toBlockPos())

        val intoPacked = PackedBlockPos(into.x, into.y, into.z)

        // Return true if: into is solid, but the 2 blocks above are walkable
        // This is the glitch case we want to avoid by using safe mode
        return !MovementValidation.canWalkThrough(ctx, intoPacked) &&
            MovementValidation.canWalkThrough(ctx, intoPacked.above()) &&
            MovementValidation.canWalkThrough(ctx, intoPacked.above(2))
    }

    override fun calculateCost(context: CalculationContext): Double {
        val result = MutableMoveResult()
        MovementDescendHelper.cost(context, src.x, src.y, src.z, dest.x, dest.z, result)
        if (result.y != dest.y) {
            return ActionCosts.COST_INF // This is a fall, not a descent
        }
        return result.cost
    }

    override fun checkCompletion() {
        val playerFeet = ctx.playerFeetBlockPos()
        if (playerFeet == dest.toBlockPos() &&
            (
                MovementValidation.isLiquid(ctx, dest.toBlockPos()) ||
                    ctx.player().position().y - dest.y < 0.5
            )
        ) {
            state.setStatus(MovementStatus.SUCCESS)
        }
    }

    override fun computeIntent(ctx: PlayerContext): Intent {
        val playerPos = ctx.player().position()
        val destCenter = dest.toBlockPos().center

        // Debug: Show player position to destination line
        debug.line("player-dest", playerPos, destCenter, java.awt.Color.GREEN)

        // Mine blocks in order (top to bottom)
        if (!MovementValidation.canWalkThrough(ctx, dest.above(2))) {
            debug.block("dest-top-2", dest.above(2).toBlockPos(), java.awt.Color.RED, 0.6f)
            debug.status("mine", "+2Y")
            return Intent(
                movement = MovementIntent.Stop,
                look = LookIntent.Block(dest.above(2).toBlockPos()),
                click = ClickIntent.LeftClick,
            )
        }
        if (!MovementValidation.canWalkThrough(ctx, dest.above())) {
            debug.block("dest-top", dest.above().toBlockPos(), java.awt.Color.ORANGE, 0.5f)
            debug.status("mine", "+1Y")
            return Intent(
                movement = MovementIntent.Stop,
                look = LookIntent.Block(dest.above().toBlockPos()),
                click = ClickIntent.LeftClick,
            )
        }
        if (!MovementValidation.canWalkThrough(ctx, dest)) {
            debug.block("dest-bottom", dest.toBlockPos(), java.awt.Color.ORANGE, 0.7f)
            debug.status("mine", "dest")
            return Intent(
                movement = MovementIntent.Stop,
                look = LookIntent.Block(dest.toBlockPos()),
                click = ClickIntent.LeftClick,
            )
        }

        debug.status("mine", "none")

        // Calculate look direction
        val direction = dest.toBlockPos().centerXZ - src.toBlockPos().centerXZ
        val targetYaw =
            Math
                .toDegrees(kotlin.math.atan2(-direction.x.toDouble(), direction.y.toDouble()))
                .toFloat()

        // Check safe mode and skipToAscend
        val isSafeMode = safeMode()
        val isSkipToAscend = skipToAscend()
        debug.flag("safe", isSafeMode)
        debug.flag("skip", isSkipToAscend)

        // Debug: Show "into" position when dangerous
        if (isSkipToAscend) {
            val into =
                dest
                    .toBlockPos()
                    .subtract(src.below().toBlockPos())
                    .offset(dest.toBlockPos())
            debug.point(
                "into-pos",
                into.center,
                java.awt.Color.RED,
                0.2f,
            )
            debug.block("into", into, java.awt.Color.RED, 0.3f)
        }

        // Calculate movement based on safe mode
        // Safe mode: reduce overshoot to 83% of distance when there are obstacles
        if (isSafeMode) {
            // Aim at 83% of the way from src to dest (weighted: 17% src + 83% dest)
            val safeTarget = src.toBlockPos().centerXZ.lerp(dest.toBlockPos().centerXZ, 0.83f)
            val safeDest = safeTarget.toVec3XZ(dest.y.toDouble())

            // Debug: Safe mode targeting
            debug.line("player-target", playerPos, safeDest, java.awt.Color.ORANGE)
            debug.point("edge-target", safeDest, java.awt.Color.YELLOW, 0.15f)

            return Intent(
                movement =
                    MovementIntent.Toward(
                        target = safeTarget,
                        speed = MovementSpeed.WALK,
                        startPos = src.toBlockPos().centerXZ,
                    ),
                look =
                    LookIntent.Direction(
                        yaw = targetYaw,
                        pitch = 10f, // Slight downward angle for walking off edge
                    ),
                click = ClickIntent.None,
            )
        }

        val srcCenter = src.toBlockPos().center
        val distFromStart = playerPos.distanceSquaredTo(srcCenter)

        // Debug: Momentum phase visualization
        val momentumPhase = numTicks < 20 || distFromStart < 1.25 * 1.25
        debug.metric("dist", kotlin.math.sqrt(distFromStart))
        debug.metric("tick", numTicks)
        debug.status("mom", if (momentumPhase) "BILD" else "done")

        // For first 20 ticks OR if we haven't traveled far, aim beyond destination for momentum
        // This ensures we walk off the edge instead of stopping at it
        val target =
            if (numTicks++ < 20 || distFromStart < 1.25 * 1.25) {
                // Calculate "fakeDest" - overshoot to ensure we walk off the edge
                val fakeDest = dest.toBlockPos().centerXZ + dest.toBlockPos().centerXZ - src.toBlockPos().centerXZ
                fakeDest
            } else {
                // After building momentum, aim at actual destination
                dest.toBlockPos().centerXZ
            }

        // Debug: Target aiming line
        val targetCenter = target.toVec3XZ(dest.y.toDouble())
        debug.line("player-target", playerPos, targetCenter, java.awt.Color.CYAN)
        debug.point("edge-target", targetCenter, java.awt.Color.YELLOW, 0.15f)

        return Intent(
            movement =
                MovementIntent.Toward(
                    target = target,
                    speed = MovementSpeed.SPRINT,
                    startPos = src.toBlockPos().centerXZ,
                ),
            look =
                LookIntent.Direction(
                    yaw = targetYaw,
                    pitch = 10f, // Slight downward angle for walking off edge
                ),
            click = ClickIntent.None,
        )
    }

    override val validPositions: Set<PackedBlockPos>
        get() = setOf(src, dest.above(), dest)

    companion object {
        @JvmStatic
        fun cost(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
            destX: Int,
            destZ: Int,
            res: MutableMoveResult,
        ) {
            MovementDescendHelper.cost(context, x, y, z, destX, destZ, res)
        }

        @JvmStatic
        fun dynamicFallCost(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
            destX: Int,
            destZ: Int,
            frontBreak: Double,
            below: BlockState,
            res: MutableMoveResult,
        ): Boolean =
            MovementDescendHelper.dynamicFallCost(
                context,
                x,
                y,
                z,
                destX,
                destZ,
                frontBreak,
                below,
                res,
            )
    }
}

/**
 * Helper object for MovementDescend cost calculation.
 */
internal object MovementDescendHelper {
    fun cost(
        context: CalculationContext,
        x: Int,
        y: Int,
        z: Int,
        destX: Int,
        destZ: Int,
        res: MutableMoveResult,
    ) {
        var totalCost = 0.0

        // Calculate mining costs for blocks in the way
        val destDown = context[destX, y - 1, destZ]
        totalCost += MovementValidation.getMiningDurationTicks(context, destX, y - 1, destZ, destDown, false)
        if (totalCost >= ActionCosts.COST_INF) {
            return
        }

        totalCost += MovementValidation.getMiningDurationTicks(context, destX, y, destZ, false)
        if (totalCost >= ActionCosts.COST_INF) {
            return
        }

        totalCost += MovementValidation.getMiningDurationTicks(context, destX, y + 1, destZ, true)
        if (totalCost >= ActionCosts.COST_INF) {
            return
        }

        // Check if starting from ladder/vine
        val fromDown = context[x, y - 1, z].block
        if (fromDown == Blocks.LADDER || fromDown == Blocks.VINE) {
            return
        }

        // Check the block two below destination
        val below = context[destX, y - 2, destZ]
        if (!MovementValidation.canWalkOn(context, destX, y - 2, destZ, below)) {
            // This might be a longer fall
            dynamicFallCost(context, x, y, z, destX, destZ, totalCost, below, res)
            return
        }

        // Check if destination is on ladder/vine
        if (destDown.block == Blocks.LADDER || destDown.block == Blocks.VINE) {
            return
        }

        // Check for frost walker
        if (MovementValidation.canUseFrostWalker(context, destDown)) {
            return
        }

        // Calculate walk-off cost
        var walk = ActionCosts.WALK_OFF_BLOCK_COST
        if (fromDown == Blocks.SOUL_SAND) {
            walk *= ActionCosts.WALK_ONE_OVER_SOUL_SAND_COST / ActionCosts.WALK_ONE_BLOCK_COST
        }

        totalCost += walk +
            max(
                ActionCosts.FALL_N_BLOCKS_COST[1],
                ActionCosts.CENTER_AFTER_FALL_COST,
            )

        res.x = destX
        res.y = y - 1
        res.z = destZ
        res.cost = totalCost
    }

    fun dynamicFallCost(
        context: CalculationContext,
        x: Int,
        y: Int,
        z: Int,
        destX: Int,
        destZ: Int,
        frontBreak: Double,
        below: BlockState,
        res: MutableMoveResult,
    ): Boolean {
        // Check if we'd cause falling blocks to fall
        if (frontBreak != 0.0 && context[destX, y + 2, destZ].block is FallingBlock) {
            return false
        }

        if (!MovementValidation.canWalkThrough(context, destX, y - 2, destZ, below)) {
            return false
        }

        var costSoFar = 0.0
        var effectiveStartHeight = y

        for (fallHeight in 3..256) {
            val newY = y - fallHeight
            if (newY < context.world.minY) {
                return false
            }

            val reachedMinimum = fallHeight >= context.minFallHeight
            val ontoBlock = context[destX, newY, destZ]
            val unprotectedFallHeight = fallHeight - (y - effectiveStartHeight)

            val tentativeCost =
                ActionCosts.WALK_OFF_BLOCK_COST +
                    ActionCosts.FALL_N_BLOCKS_COST[unprotectedFallHeight] +
                    frontBreak + costSoFar

            // Check for water landing
            if (reachedMinimum && MovementValidation.isWater(ontoBlock)) {
                if (!MovementValidation.canWalkThrough(context, destX, newY, destZ, ontoBlock)) {
                    return false
                }
                if (context.assumeWalkOnWater) {
                    return false
                }
                if (MovementValidation.isFlowing(destX, newY, destZ, ontoBlock, context.bsi)) {
                    return false
                }
                if (!MovementValidation.canWalkOn(context, destX, newY - 1, destZ)) {
                    return false
                }

                res.x = destX
                res.y = newY
                res.z = destZ
                res.cost = tentativeCost
                return false
            }

            // Check for lava landing
            if (reachedMinimum && context.allowFallIntoLava && MovementValidation.isLava(ontoBlock)) {
                res.x = destX
                res.y = newY
                res.z = destZ
                res.cost = tentativeCost
                return false
            }

            // Check for ladder/vine grab
            if (unprotectedFallHeight <= 11 &&
                (ontoBlock.block == Blocks.VINE || ontoBlock.block == Blocks.LADDER)
            ) {
                costSoFar += ActionCosts.FALL_N_BLOCKS_COST[unprotectedFallHeight - 1]
                costSoFar += ActionCosts.LADDER_DOWN_ONE_COST
                effectiveStartHeight = newY
                continue
            }

            if (MovementValidation.canWalkThrough(context, destX, newY, destZ, ontoBlock)) {
                continue
            }

            if (!MovementValidation.canWalkOn(context, destX, newY, destZ, ontoBlock)) {
                return false
            }

            if (MovementValidation.isBottomSlab(ontoBlock)) {
                return false
            }

            // Check if fall is acceptable
            if (reachedMinimum && unprotectedFallHeight <= context.maxFallHeightNoWater + 1) {
                res.x = destX
                res.y = newY + 1
                res.z = destZ
                res.cost = tentativeCost
                return false
            }

            // Check for water bucket landing
            if (reachedMinimum &&
                context.hasWaterBucket &&
                unprotectedFallHeight <= context.maxFallHeightBucket + 1
            ) {
                res.x = destX
                res.y = newY + 1
                res.z = destZ
                res.cost = tentativeCost + context.placeBucketCost()
                return true
            }

            return false
        }

        return false
    }
}
