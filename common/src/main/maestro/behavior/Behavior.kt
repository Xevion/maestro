package maestro.behavior

import maestro.Agent
import maestro.api.behavior.IBehavior
import maestro.api.player.PlayerContext

/**
 * Base behavior class that provides [Agent] and [PlayerContext] to subclasses.
 *
 * All behaviors have access to the agent instance and player context through protected fields.
 */
abstract class Behavior protected constructor(
    @JvmField val maestro: Agent,
) : IBehavior {
    @JvmField
    val ctx: PlayerContext = maestro.playerContext
}
