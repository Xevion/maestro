package maestro.task

import maestro.Agent
import maestro.api.task.PathingCommand
import maestro.api.task.PathingCommandType
import maestro.api.utils.Loggers
import maestro.api.utils.PackedBlockPos
import maestro.input.Input
import maestro.pathing.movement.Movement
import maestro.pathing.movement.MovementState
import maestro.pathing.movement.MovementValidation
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.EmptyLevelChunk
import org.slf4j.Logger

/**
 * Process that tracks blocks broken during pathfinding and attempts to replace them.
 *
 * This process monitors blocks that the bot breaks while pathing and queues them for
 * replacement with dirt blocks when safe to do so. It's designed to "backfill" gaps
 * created during movement, preventing fall damage or navigation issues.
 *
 * Note: Incompatible with parkour movement setting.
 */
class BackfillTask(
    maestro: Agent,
) : TaskHelper(maestro) {
    private val _blocksToReplace = mutableMapOf<BlockPos, BlockState>()

    /** Read-only view of blocks queued for replacement */
    val blocksToReplace: Map<BlockPos, BlockState> get() = _blocksToReplace

    override fun isActive(): Boolean {
        ctx.player() ?: return false
        val world = ctx.world() ?: return false

        if (!Agent
                .getPrimaryAgent()
                .settings.backfill.value
        ) {
            return false
        }

        if (isParkourConflict()) {
            return false
        }

        cleanUpInvalidPositions(world)
        trackCurrentlyBreakingBlock()
        maestro.inputOverrideHandler.clearAllKeys()

        return toFillIn().isNotEmpty()
    }

    /**
     * Checks if parkour setting conflicts with backfill and disables backfill if so.
     * Parkour movements create intentional gaps that should not be filled.
     */
    private fun isParkourConflict(): Boolean {
        if (Agent
                .getPrimaryAgent()
                .settings.allowParkour.value
        ) {
            log
                .atWarn()
                .addKeyValue("reason", "incompatible_settings")
                .addKeyValue("setting_1", "backfill")
                .addKeyValue("setting_2", "allowParkour")
                .log("Backfill disabled due to incompatible settings")
            Agent
                .getPrimaryAgent()
                .settings.backfill.value = false
            return true
        }
        return false
    }

    /**
     * Removes positions from the backfill queue that are no longer valid.
     * A position is invalid if its chunk is unloaded or the block is no longer air.
     */
    private fun cleanUpInvalidPositions(world: net.minecraft.world.level.Level) {
        _blocksToReplace.entries.removeIf { (pos, _) ->
            world.getChunk(pos) is EmptyLevelChunk || world.getBlockState(pos).block != Blocks.AIR
        }
    }

    override fun onTick(
        calcFailed: Boolean,
        isSafeToCancel: Boolean,
    ): PathingCommand {
        if (!isSafeToCancel) {
            return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
        }

        maestro.inputOverrideHandler.clearAllKeys()

        for (toPlace in toFillIn()) {
            val state = MovementState()
            val result = MovementValidation.attemptToPlaceABlock(state, maestro, toPlace, false, false)

            when (result) {
                MovementValidation.PlaceResult.NO_OPTION -> continue

                MovementValidation.PlaceResult.READY_TO_PLACE -> {
                    maestro.inputOverrideHandler.setInputForceState(Input.CLICK_RIGHT, true)
                    return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                }

                MovementValidation.PlaceResult.ATTEMPTING -> {
                    // TODO: Update to use LookIntent after movement system refactor
                    // state.getTarget().getRotation().ifPresent { rotation ->
                    //     maestro.lookBehavior.updateTarget(rotation, true)
                    // }
                    return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                }
            }
        }

        return PathingCommand(null, PathingCommandType.DEFER)
    }

    /**
     * Tracks the block currently being broken by the player if actively pathing.
     * This allows backfill to queue the position for later replacement.
     */
    private fun trackCurrentlyBreakingBlock() {
        val selectedBlock = ctx.getSelectedBlock()
        if (selectedBlock.isEmpty || !maestro.pathingBehavior.isPathing()) {
            return
        }

        val world = ctx.world() ?: return
        selectedBlock.ifPresent { pos ->
            _blocksToReplace[pos] = world.getBlockState(pos)
        }
    }

    /**
     * Returns a list of positions ready to be filled, sorted by distance from player.
     * Uses sequence for lazy evaluation to avoid intermediate collections.
     */
    fun toFillIn(): List<BlockPos> {
        val world = ctx.world() ?: return emptyList()
        val playerFeet = ctx.playerFeet()

        return _blocksToReplace.keys
            .asSequence()
            .filter { pos -> world.getBlockState(pos).block == Blocks.AIR }
            .filter { pos -> maestro.builderTask.placementPlausible(pos, DIRT_STATE) }
            .filter { pos -> !partOfCurrentMovement(pos) }
            .sortedByDescending { pos -> playerFeet.distSqr(PackedBlockPos(pos)) }
            .toList()
    }

    /**
     * Checks if a position is part of the current movement's blocks to break.
     * Returns true if the position should not be filled because it's being actively used.
     */
    private fun partOfCurrentMovement(pos: BlockPos): Boolean {
        val exec = maestro.pathingBehavior.getCurrent() ?: return false

        if (exec.finished() || exec.failed()) {
            return false
        }

        val movements = exec.path.movements()
        val currentPos = exec.position

        if (currentPos < 0 || currentPos >= movements.size) {
            return false
        }

        val movement = movements[currentPos] as? Movement ?: return false
        return movement.toBreakAll().contains(pos)
    }

    override fun onLostControl() {
        _blocksToReplace.clear()
    }

    override fun displayName0(): String = "Backfill"

    override fun isTemporary(): Boolean = true

    override fun priority(): Double = 5.0

    companion object {
        private val log: Logger = Loggers.Move.get()
        private val DIRT_STATE: BlockState = Blocks.DIRT.defaultBlockState()
    }
}
