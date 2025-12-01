package maestro.api.debug

/**
 * Debug visualization layers for different bot subsystems.
 *
 * Each layer can be independently toggled and renders both 2D (HUD) and 3D (world) content related
 * to its domain.
 *
 * Layers are only active when debug mode is enabled.
 */
enum class DebugLayer(
    val displayName: String,
    val description: String,
) {
    /**
     * Process and behavior state visualization.
     *
     * Shows:
     * - Current process (e.g., MineProcess, FarmProcess)
     * - Active goals and their status
     * - Decision reasoning and state transitions
     * - Process queue and priorities
     */
    PROCESS_STATE("Process", "Process and behavior state"),

    /**
     * Combat system visualization (future implementation).
     *
     * Shows:
     * - Detected threats and their danger levels
     * - Current combat tactic (fight, flee, heal)
     * - Target selection reasoning
     * - Health/hunger status
     */
    COMBAT("Combat", "Combat threats and tactics"),

    /**
     * Multi-agent coordination visualization (future implementation).
     *
     * Shows:
     * - Agent roster and status
     * - Task assignments per agent
     * - Block claims and conflicts
     * - Inter-agent communication
     */
    COORDINATION("Coordination", "Multi-agent task distribution"),

    /**
     * Performance metrics and profiling (future implementation).
     *
     * Shows:
     * - Pathfinding algorithm metrics
     * - Tick time measurements
     * - Memory usage (chunk cache, paths)
     * - TPS impact analysis
     */
    PERFORMANCE("Performance", "Performance metrics and profiling"),
    ;

    /**
     * Get the setting name for this layer's toggle.
     *
     * @return setting name (e.g., "debugLayerProcess")
     */
    fun getSettingName(): String = "debugLayer${name.first()}${name.substring(1).lowercase()}"
}
