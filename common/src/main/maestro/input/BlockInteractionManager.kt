package maestro.input

import maestro.player.PlayerContext

/**
 * Manages block interaction timing and delegates to strategies for breaking/placing.
 * Consolidates timer logic previously duplicated in BlockBreakHelper and BlockPlaceHelper.
 */
class BlockInteractionManager(
    private val ctx: PlayerContext,
    private val strategy: BlockInteractionStrategy,
) {
    private var timer = 0

    /**
     * Ticks the interaction manager, handling timer and delegating to strategy.
     *
     * @param requested Whether interaction is requested this tick (e.g., from input)
     */
    fun tick(requested: Boolean) {
        if (timer > 0) {
            timer--
            return
        }

        if (!requested) {
            return
        }

        strategy.interact(ctx)?.let { delay ->
            timer = delay
        }
    }

    /**
     * Stops the current interaction and cleans up state.
     */
    fun stop() {
        strategy.stop(ctx)
    }
}
