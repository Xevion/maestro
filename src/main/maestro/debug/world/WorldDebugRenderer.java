package maestro.debug.world;

import maestro.Agent;
import maestro.api.event.events.RenderEvent;

/**
 * Renders 3D world-space debug annotations.
 *
 * <p>Displays goal labels, reasoning annotations, and other debug visualizations in 3D space using
 * billboard text and shapes.
 *
 * <p>Rendering is performed during the world render pass via {@link RenderEvent}.
 */
public class WorldDebugRenderer {

    private final Agent agent;

    public WorldDebugRenderer(Agent agent) {
        this.agent = agent;
    }

    /**
     * Render 3D world debug annotations.
     *
     * <p>Called from {@link maestro.debug.MaestroDebugRenderer#onRenderPass(RenderEvent)} when
     * world annotations are enabled.
     *
     * @param event the render event with pose stack and matrices
     */
    public void render(RenderEvent event) {
        // TODO: Implement world rendering in future phases
        // Will render goal labels and transient annotations
    }
}
