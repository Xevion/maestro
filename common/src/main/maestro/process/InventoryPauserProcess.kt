package maestro.process

import maestro.Agent
import maestro.api.process.PathingCommand
import maestro.api.process.PathingCommandType

class InventoryPauserProcess(
    maestro: Agent,
) : MaestroProcessHelper(maestro) {
    private var pauseRequestedLastTick = false
    private var safeToCancelLastTick = false
    private var ticksOfStationary = 0

    override fun isActive(): Boolean = ctx.player() != null && ctx.world() != null

    private fun motion(): Double =
        ctx
            .player()
            .deltaMovement
            .multiply(1.0, 0.0, 1.0)
            .length()

    private fun stationaryNow(): Boolean = motion() < 0.00001

    fun stationaryForInventoryMove(): Boolean {
        pauseRequestedLastTick = true
        return safeToCancelLastTick && ticksOfStationary > 1
    }

    override fun onTick(
        calcFailed: Boolean,
        isSafeToCancel: Boolean,
    ): PathingCommand {
        safeToCancelLastTick = isSafeToCancel
        if (pauseRequestedLastTick) {
            pauseRequestedLastTick = false
            if (stationaryNow()) {
                ticksOfStationary++
            }
            return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
        }
        ticksOfStationary = 0
        return PathingCommand(null, PathingCommandType.DEFER)
    }

    override fun onLostControl() {}

    override fun displayName0(): String = "inventory pauser"

    override fun priority(): Double = 5.1 // slightly higher than backfill

    override fun isTemporary(): Boolean = true
}
