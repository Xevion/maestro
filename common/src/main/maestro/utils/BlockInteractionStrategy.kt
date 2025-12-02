package maestro.utils

import maestro.api.utils.IPlayerContext

/**
 * Strategy interface for block interactions (breaking or placing).
 * Handles the specific logic while BlockInteractionManager handles timing.
 */
interface BlockInteractionStrategy {
    /**
     * Called when interaction conditions are met (timer expired, requested by input).
     *
     * @param ctx Player context for world access
     * @return Delay ticks until next interaction, or null if no interaction occurred
     */
    fun interact(ctx: IPlayerContext): Int?

    /**
     * Called when interaction should be stopped or cleaned up.
     */
    fun stop(ctx: IPlayerContext) {}
}
