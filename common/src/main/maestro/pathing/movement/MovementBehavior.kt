package maestro.pathing.movement

import maestro.api.utils.IPlayerContext

/**
 * Interface for stateless movement computation.
 *
 * Implementations compute unified intent (movement, look, click) from current game state each tick.
 * This pattern enables:
 * - Pure functions (state → intent) that are easy to test
 * - No scattered input manipulation across movement classes
 * - Clear separation between decision logic and input execution
 * - Single computation point per tick
 *
 * Lifecycle:
 * 1. Movement calls computeIntent(ctx) each tick
 * 2. Intent is applied via InputOverrideHandler (delta-based)
 * 3. State updates based on progress toward goal
 */
interface MovementBehavior {
    /**
     * Compute unified intent for this tick.
     *
     * Called every tick by the Movement orchestrator. Should be pure - same state
     * produces same intent. Use ctx to access player state, world, and utilities.
     *
     * Returns Intent containing movement, look, and click for this tick.
     * The InputOverrideHandler will diff against previous intent and only update
     * changed inputs for efficiency.
     *
     * @param ctx Player context with position, velocity, world access, etc.
     * @return Intent describing all desired inputs this tick
     */
    fun computeIntent(ctx: IPlayerContext): Intent
}
