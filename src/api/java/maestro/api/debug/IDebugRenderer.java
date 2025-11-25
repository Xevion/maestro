package maestro.api.debug;

/**
 * Interface for the debug rendering system.
 *
 * <p>Provides access to specialized renderers for different debug visualization needs.
 */
public interface IDebugRenderer {

    /**
     * Get the HUD debug renderer.
     *
     * @return the HUD debug renderer
     */
    IHudDebugRenderer getHudRenderer();
}
