package maestro.behavior

/**
 * Freecam camera modes.
 * - STATIC: Camera position is independent of player (default)
 * - FOLLOW: Camera follows player position deltas, rotation independent
 */
enum class FreecamMode {
    STATIC,
    FOLLOW,
}
