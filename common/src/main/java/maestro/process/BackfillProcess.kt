package maestro.process

import maestro.Agent
import maestro.api.process.PathingCommand
import maestro.api.process.PathingCommandType
import maestro.api.utils.MaestroLogger
import maestro.api.utils.input.Input
import maestro.pathing.movement.Movement
import maestro.pathing.movement.MovementHelper
import maestro.pathing.movement.MovementState
import maestro.utils.MaestroProcessHelper
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.EmptyLevelChunk
import org.slf4j.Logger

class BackfillProcess(
    maestro: Agent,
) : MaestroProcessHelper(maestro) {
    val blocksToReplace = mutableMapOf<BlockPos, BlockState>()

    override fun isActive(): Boolean {
        val player = ctx.player() ?: return false
        val world = ctx.world() ?: return false

        if (!Agent.settings().backfill.value) {
            return false
        }

        if (Agent.settings().allowParkour.value) {
            log
                .atWarn()
                .addKeyValue("reason", "incompatible_settings")
                .addKeyValue("setting_1", "backfill")
                .addKeyValue("setting_2", "allowParkour")
                .log("Backfill disabled due to incompatible settings")
            Agent.settings().backfill.value = false
            return false
        }

        // Clean up invalid positions
        blocksToReplace.keys.toList().forEach { pos ->
            if (world.getChunk(pos) is EmptyLevelChunk ||
                world.getBlockState(pos).block != Blocks.AIR
            ) {
                blocksToReplace.remove(pos)
            }
        }

        amIBreakingABlockHMMMMMMM()
        maestro.getInputOverrideHandler().clearAllKeys()

        return toFillIn().isNotEmpty()
    }

    override fun onTick(
        calcFailed: Boolean,
        isSafeToCancel: Boolean,
    ): PathingCommand {
        if (!isSafeToCancel) {
            return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
        }

        maestro.getInputOverrideHandler().clearAllKeys()

        for (toPlace in toFillIn()) {
            val fake = MovementState()
            when (MovementHelper.attemptToPlaceABlock(fake, maestro, toPlace, false, false)) {
                MovementHelper.PlaceResult.NO_OPTION -> continue

                MovementHelper.PlaceResult.READY_TO_PLACE -> {
                    maestro.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true)
                    return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                }

                MovementHelper.PlaceResult.ATTEMPTING -> {
                    // Patience
                    fake.target.getRotation().ifPresent { rotation ->
                        maestro.getLookBehavior().updateTarget(rotation, true)
                    }
                    return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                }
            }
        }

        return PathingCommand(null, PathingCommandType.DEFER) // Cede to other process
    }

    private fun amIBreakingABlockHMMMMMMM() {
        val selectedBlock = ctx.getSelectedBlock()
        if (selectedBlock.isEmpty || !maestro.getPathingBehavior().isPathing()) {
            return
        }

        selectedBlock.ifPresent { pos ->
            blocksToReplace[pos] = ctx.world().getBlockState(pos)
        }
    }

    fun toFillIn(): List<BlockPos> {
        val playerFeet = ctx.playerFeet()
        return blocksToReplace.keys
            .filter { pos -> ctx.world().getBlockState(pos).block == Blocks.AIR }
            .filter { pos ->
                maestro.getBuilderProcess().placementPlausible(pos, Blocks.DIRT.defaultBlockState())
            }.filter { pos -> !partOfCurrentMovement(pos) }
            .sortedByDescending { pos -> playerFeet.distSqr(pos) }
    }

    private fun partOfCurrentMovement(pos: BlockPos): Boolean {
        val exec = maestro.getPathingBehavior().getCurrent() ?: return false

        if (exec.finished() || exec.failed()) {
            return false
        }

        val movement = exec.path.movements()[exec.position] as Movement
        return movement.toBreakAll().contains(pos)
    }

    override fun onLostControl() {
        blocksToReplace.clear()
    }

    override fun displayName0(): String = "Backfill"

    override fun isTemporary(): Boolean = true

    override fun priority(): Double = 5.0

    companion object {
        private val log: Logger = MaestroLogger.get("move")
    }
}
