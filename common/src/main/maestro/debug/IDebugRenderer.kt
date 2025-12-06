package maestro.debug

/**
 * Interface for the debug rendering system.
 *
 * Provides access to specialized renderers for different debug visualization needs.
 */
interface IDebugRenderer {
    /**
     * Get the HUD debug renderer.
     *
     * @return the HUD debug renderer
     */
    fun getHudRenderer(): IHudDebugRenderer
}
