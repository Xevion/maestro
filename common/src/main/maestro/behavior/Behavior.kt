package maestro.behavior

import maestro.Agent
import maestro.player.PlayerContext

/**
 * Base behavior class that provides [Agent] and [PlayerContext] to subclasses.
 *
 * All behaviors have access to the agent instance and player context through protected fields.
 */
abstract class Behavior protected constructor(
    @JvmField val agent: Agent,
) : IBehavior {
    @JvmField
    val ctx: PlayerContext = agent.playerContext
}
