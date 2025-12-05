package maestro.pathing.movement.movements

import maestro.api.IAgent
import maestro.api.pathing.movement.ActionCosts
import maestro.api.pathing.movement.MovementStatus
import maestro.api.utils.IPlayerContext
import maestro.api.utils.PackedBlockPos
import maestro.api.utils.RotationUtils
import maestro.api.utils.center
import maestro.api.utils.centerWithY
import maestro.api.utils.centerXZ
import maestro.pathing.BlockStateInterface
import maestro.pathing.movement.CalculationContext
import maestro.pathing.movement.ClickIntent
import maestro.pathing.movement.Intent
import maestro.pathing.movement.LookIntent
import maestro.pathing.movement.Movement
import maestro.pathing.movement.MovementHelper
import maestro.pathing.movement.MovementIntent
import maestro.pathing.movement.MovementSpeed
import maestro.utils.distanceSquaredTo
import maestro.utils.horizontalLength
import net.minecraft.core.BlockPos

/**
 * Horizontal movement to an adjacent block on the same Y level.
 *
 * This is the most common movement type - walking forward one block.
 * May involve:
 * - Breaking blocks in the path
 * - Placing a bridge block if destination is air
 * - Opening doors/gates
 */
class MovementTraverse(
    maestro: IAgent,
    src: PackedBlockPos,
    dest: PackedBlockPos,
) : Movement(maestro, src, dest) {
    /** Did we have to place a bridge block or was it always there */
    private var wasTheBridgeBlockAlwaysThere = true

    override fun reset() {
        super.reset()
        wasTheBridgeBlockAlwaysThere = true
    }

    override fun toBreak(bsi: BlockStateInterface): List<BlockPos> {
        val blocks = mutableListOf<BlockPos>()

        if (!MovementHelper.canWalkThrough(ctx, dest.above())) {
            blocks.add(dest.above().toBlockPos())
        }
        if (!MovementHelper.canWalkThrough(ctx, dest)) {
            blocks.add(dest.toBlockPos())
        }

        return blocks
    }

    override fun calculateCost(context: CalculationContext): Double =
        MovementTraverseHelper.cost(context, src.x, src.y, src.z, dest.x, dest.z)

    override fun checkCompletion() {
        val feet = ctx.playerFeetBlockPos()

        // Validate Y-level first before checking XZ position
        if (feet.y != dest.y) {
            return // Not at correct height yet
        }

        if (feet == dest.toBlockPos()) {
            state.setStatus(MovementStatus.SUCCESS)
        }
    }

    override fun computeIntent(ctx: IPlayerContext): Intent {
        val playerPos = ctx.player().position()
        val destCenter = dest.center

        // Debug: Show player position to destination line
        debug.line("player-dest", playerPos, destCenter, java.awt.Color.GREEN)
        debug.point("dest-center", destCenter, java.awt.Color.YELLOW, 0.15f)

        // Mine blocks in order (top first, then bottom)
        if (!MovementHelper.canWalkThrough(ctx, dest.above())) {
            debug.block("dest-top", dest.above().toBlockPos(), java.awt.Color.RED, 0.7f)
            debug.status("mine", "top")
            return Intent(
                movement = MovementIntent.Stop,
                look = LookIntent.Block(dest.above().toBlockPos()),
                click = ClickIntent.LeftClick,
            )
        }
        if (!MovementHelper.canWalkThrough(ctx, dest)) {
            debug.block("dest-bottom", dest.toBlockPos(), java.awt.Color.ORANGE, 0.7f)
            debug.status("mine", "dest")
            return Intent(
                movement = MovementIntent.Stop,
                look = LookIntent.Block(dest.toBlockPos()),
                click = ClickIntent.LeftClick,
            )
        }

        // Calculate distance to destination center
        val distToDest = ctx.player().position().distanceSquaredTo(destCenter)

        // Debug: Distance and velocity metrics
        val velocity = ctx.player().deltaMovement
        val velocityXZ = velocity.horizontalLength
        debug.metric("dist", kotlin.math.sqrt(distToDest))
        debug.metric("vel", velocityXZ)
        debug.status("mine", "none")

        // Stop if we're close enough (tightened threshold for better centering)
        if (distToDest < 0.3 * 0.3) {
            debug.status("mode", "stop")
            debug.flag("sprint", false)
            return Intent(
                movement = MovementIntent.Stop,
                look = LookIntent.None,
                click = ClickIntent.None,
            )
        }

        // Calculate yaw toward destination, slight downward pitch for visibility
        val targetYaw =
            RotationUtils
                .calcRotationFromVec3d(
                    ctx.playerHead(),
                    dest.centerWithY(ctx.player().eyeY),
                    ctx.playerRotations(),
                ).yaw

        // Debug: Movement mode
        debug.status("mode", "walk")
        debug.flag("sprint", true)

        // Debug: Show bridge placement if applicable
        if (!wasTheBridgeBlockAlwaysThere) {
            debug.block("bridge-support", dest.below().toBlockPos(), java.awt.Color.LIGHT_GRAY, 0.4f)
            debug.point(
                "bridge-place",
                dest.below().center,
                java.awt.Color.ORANGE,
                0.2f,
            )
        }

        // Move toward destination center with sprinting
        return Intent(
            movement =
                MovementIntent.Toward(
                    target = dest.centerXZ,
                    speed = MovementSpeed.SPRINT,
                    startPos = src.centerXZ,
                ),
            look =
                LookIntent.Direction(
                    yaw = targetYaw,
                    pitch = 15f, // Slight downward angle (natural for walking)
                ),
            click = ClickIntent.None,
        )
    }

    override fun safeToCancel(): Boolean {
        // Safe to cancel if we're not running, or if the bridge block exists
        return state.getStatus() != MovementStatus.RUNNING ||
            MovementHelper.canWalkOn(ctx, dest.below().toBlockPos())
    }

    override val validPositions: Set<PackedBlockPos>
        get() = setOf(src, dest)

    companion object {
        /**
         * Estimate cost of a horizontal traverse movement.
         *
         * This is a simplified version that delegates to the full cost calculation.
         */
        @JvmStatic
        fun cost(
            context: CalculationContext,
            x: Int,
            y: Int,
            z: Int,
            destX: Int,
            destZ: Int,
        ): Double = MovementTraverseHelper.cost(context, x, y, z, destX, destZ)
    }
}

/**
 * Helper object for MovementTraverse cost calculation.
 *
 * Separated to keep the movement class clean and focused on intent logic.
 */
internal object MovementTraverseHelper {
    fun cost(
        context: CalculationContext,
        x: Int,
        y: Int,
        z: Int,
        destX: Int,
        destZ: Int,
    ): Double {
        val pb0 = context[destX, y + 1, destZ]
        val pb1 = context[destX, y, destZ]
        val destOn = context[destX, y - 1, destZ]
        val srcDown = context[x, y - 1, z]
        val srcDownBlock = srcDown.block

        val standingOnABlock = MovementHelper.mustBeSolidToWalkOn(context, x, y - 1, z, srcDown)
        val frostWalker =
            standingOnABlock &&
                !context.assumeWalkOnWater &&
                MovementHelper.canUseFrostWalker(context, destOn)

        // Check if this is a walk (not a bridge)
        if (frostWalker || MovementHelper.canWalkOn(context, destX, y - 1, destZ, destOn)) {
            return calculateWalkCost(context, x, y, z, destX, destZ, pb0, pb1, destOn, srcDownBlock, frostWalker)
        } else {
            // This is a bridge - need to place a block
            return calculateBridgeCost(context, x, y, z, destX, destZ, pb0, pb1, destOn, srcDown, srcDownBlock, standingOnABlock)
        }
    }

    private fun calculateWalkCost(
        context: CalculationContext,
        x: Int,
        y: Int,
        z: Int,
        destX: Int,
        destZ: Int,
        pb0: net.minecraft.world.level.block.state.BlockState,
        pb1: net.minecraft.world.level.block.state.BlockState,
        destOn: net.minecraft.world.level.block.state.BlockState,
        srcDownBlock: net.minecraft.world.level.block.Block,
        frostWalker: Boolean,
    ): Double {
        var walkCost = ActionCosts.WALK_ONE_BLOCK_COST
        var water = false

        if (MovementHelper.isWater(pb0) || MovementHelper.isWater(pb1)) {
            walkCost = context.waterWalkSpeed
            water = true
        } else {
            if (destOn.block == net.minecraft.world.level.block.Blocks.SOUL_SAND) {
                walkCost += (ActionCosts.WALK_ONE_OVER_SOUL_SAND_COST - ActionCosts.WALK_ONE_BLOCK_COST) / 2
            } else if (!frostWalker && destOn.block == net.minecraft.world.level.block.Blocks.WATER) {
                walkCost += context.walkOnWaterOnePenalty
            }

            if (srcDownBlock == net.minecraft.world.level.block.Blocks.SOUL_SAND) {
                walkCost += (ActionCosts.WALK_ONE_OVER_SOUL_SAND_COST - ActionCosts.WALK_ONE_BLOCK_COST) / 2
            }
        }

        val hardness1 = MovementHelper.getMiningDurationTicks(context, destX, y, destZ, pb1, false)
        if (hardness1 >= ActionCosts.COST_INF) {
            return ActionCosts.COST_INF
        }

        val hardness2 = MovementHelper.getMiningDurationTicks(context, destX, y + 1, destZ, pb0, true)

        if (hardness1 == 0.0 && hardness2 == 0.0) {
            if (!water && context.canSprint) {
                walkCost *= ActionCosts.SPRINT_MULTIPLIER
            }
            return walkCost
        }

        var totalHardness = hardness1 + hardness2
        if (srcDownBlock == net.minecraft.world.level.block.Blocks.LADDER ||
            srcDownBlock == net.minecraft.world.level.block.Blocks.VINE
        ) {
            totalHardness *= 5
        }

        return walkCost + totalHardness
    }

    private fun calculateBridgeCost(
        context: CalculationContext,
        x: Int,
        y: Int,
        z: Int,
        destX: Int,
        destZ: Int,
        pb0: net.minecraft.world.level.block.state.BlockState,
        pb1: net.minecraft.world.level.block.state.BlockState,
        destOn: net.minecraft.world.level.block.state.BlockState,
        srcDown: net.minecraft.world.level.block.state.BlockState,
        srcDownBlock: net.minecraft.world.level.block.Block,
        standingOnABlock: Boolean,
    ): Double {
        if (srcDownBlock == net.minecraft.world.level.block.Blocks.LADDER ||
            srcDownBlock == net.minecraft.world.level.block.Blocks.VINE
        ) {
            return ActionCosts.COST_INF
        }

        if (!MovementHelper.isReplaceable(destX, y - 1, destZ, destOn, context.bsi)) {
            return ActionCosts.COST_INF
        }

        val throughWater = MovementHelper.isWater(pb0) || MovementHelper.isWater(pb1)
        if (MovementHelper.isWater(destOn) && throughWater) {
            return ActionCosts.COST_INF
        }

        val placeCost = context.costOfPlacingAt(destX, y - 1, destZ, destOn)
        if (placeCost >= ActionCosts.COST_INF) {
            return ActionCosts.COST_INF
        }

        val hardness1 = MovementHelper.getMiningDurationTicks(context, destX, y, destZ, pb1, false)
        if (hardness1 >= ActionCosts.COST_INF) {
            return ActionCosts.COST_INF
        }

        val hardness2 = MovementHelper.getMiningDurationTicks(context, destX, y + 1, destZ, pb0, true)
        val walkCost = if (throughWater) context.waterWalkSpeed else ActionCosts.WALK_ONE_BLOCK_COST

        // Check for side place option
        for (i in 0 until 5) {
            val dir = Movement.HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i]
            val againstX = destX + dir.stepX
            val againstY = y - 1 + dir.stepY
            val againstZ = destZ + dir.stepZ

            if (againstX == x && againstZ == z) {
                continue // backplace
            }

            if (MovementHelper.canPlaceAgainst(context.bsi, againstX, againstY, againstZ)) {
                return walkCost + placeCost + hardness1 + hardness2
            }
        }

        // Must backplace - check if possible
        if (srcDownBlock == net.minecraft.world.level.block.Blocks.SOUL_SAND ||
            (
                srcDownBlock is net.minecraft.world.level.block.SlabBlock &&
                    srcDown.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) !=
                    net.minecraft.world.level.block.state.properties.SlabType.DOUBLE
            )
        ) {
            return ActionCosts.COST_INF
        }

        if (!standingOnABlock) {
            return ActionCosts.COST_INF
        }

        val blockSrc = context.getBlock(x, y, z)
        if ((
                blockSrc == net.minecraft.world.level.block.Blocks.LILY_PAD ||
                    blockSrc is net.minecraft.world.level.block.CarpetBlock
            ) &&
            !srcDown.fluidState.isEmpty
        ) {
            return ActionCosts.COST_INF
        }

        return walkCost * (ActionCosts.SNEAK_ONE_BLOCK_COST / ActionCosts.WALK_ONE_BLOCK_COST) +
            placeCost + hardness1 + hardness2
    }
}
