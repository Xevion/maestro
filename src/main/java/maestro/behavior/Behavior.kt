package maestro.behavior

import maestro.Agent
import maestro.api.behavior.IBehavior
import maestro.api.utils.IPlayerContext

/**
 * Base behavior class that provides [Agent] and [IPlayerContext] to subclasses.
 *
 * All behaviors have access to the agent instance and player context through protected fields.
 *
 * @author Brady
 * @since 8/1/2018
 */
abstract class Behavior protected constructor(
    @JvmField val maestro: Agent
) : IBehavior {
    @JvmField
    val ctx: IPlayerContext = maestro.getPlayerContext()
}
