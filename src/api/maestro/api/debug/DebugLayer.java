package maestro.api.debug;

/**
 * Debug visualization layers for different bot subsystems.
 *
 * <p>Each layer can be independently toggled and renders both 2D (HUD) and 3D (world) content
 * related to its domain.
 *
 * <p>Layers are only active when debug mode is enabled.
 */
public enum DebugLayer {
    /**
     * Process and behavior state visualization.
     *
     * <p>Shows:
     *
     * <ul>
     *   <li>Current process (e.g., MineProcess, FarmProcess)
     *   <li>Active goals and their status
     *   <li>Decision reasoning and state transitions
     *   <li>Process queue and priorities
     * </ul>
     */
    PROCESS_STATE("Process", "Process and behavior state"),

    /**
     * Combat system visualization (future implementation).
     *
     * <p>Shows:
     *
     * <ul>
     *   <li>Detected threats and their danger levels
     *   <li>Current combat tactic (fight, flee, heal)
     *   <li>Target selection reasoning
     *   <li>Health/hunger status
     * </ul>
     */
    COMBAT("Combat", "Combat threats and tactics"),

    /**
     * Multi-agent coordination visualization (future implementation).
     *
     * <p>Shows:
     *
     * <ul>
     *   <li>Agent roster and status
     *   <li>Task assignments per agent
     *   <li>Block claims and conflicts
     *   <li>Inter-agent communication
     * </ul>
     */
    COORDINATION("Coordination", "Multi-agent task distribution"),

    /**
     * Performance metrics and profiling (future implementation).
     *
     * <p>Shows:
     *
     * <ul>
     *   <li>Pathfinding algorithm metrics
     *   <li>Tick time measurements
     *   <li>Memory usage (chunk cache, paths)
     *   <li>TPS impact analysis
     * </ul>
     */
    PERFORMANCE("Performance", "Performance metrics and profiling");

    private final String displayName;
    private final String description;

    DebugLayer(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get the setting name for this layer's toggle.
     *
     * @return setting name (e.g., "debugLayerProcess")
     */
    public String getSettingName() {
        return "debugLayer" + name().charAt(0) + name().substring(1).toLowerCase();
    }
}
