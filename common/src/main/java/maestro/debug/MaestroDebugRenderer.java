package maestro.debug;

import maestro.Agent;
import maestro.api.debug.IDebugRenderer;
import maestro.api.debug.IHudDebugRenderer;
import maestro.api.event.events.RenderEvent;
import maestro.api.event.listener.AbstractGameEventListener;
import maestro.debug.hud.HudDebugRenderer;
import maestro.debug.world.WorldDebugRenderer;

/**
 * Main coordinator for debug rendering system.
 *
 * <p>Manages both 2D HUD overlays and 3D world annotations, delegating to specialized renderers
 * based on the current debug mode and active layers.
 *
 * <p>Registered as a {@link AbstractGameEventListener} to receive render events each frame.
 */
public class MaestroDebugRenderer implements IDebugRenderer, AbstractGameEventListener {

    private final Agent agent;
    private final HudDebugRenderer hudRenderer;
    private final WorldDebugRenderer worldRenderer;

    public MaestroDebugRenderer(Agent agent) {
        this.agent = agent;
        this.hudRenderer = new HudDebugRenderer(agent);
        this.worldRenderer = new WorldDebugRenderer(agent);
    }

    @Override
    public void onRenderPass(RenderEvent event) {
        if (!Agent.settings().debugEnabled.value) {}

        // World rendering not yet implemented in this version
        // Will be added in future phases
    }

    @Override
    public IHudDebugRenderer getHudRenderer() {
        return hudRenderer;
    }

    /**
     * Get the world renderer for direct access if needed.
     *
     * @return the world renderer instance
     */
    public WorldDebugRenderer getWorldRenderer() {
        return worldRenderer;
    }
}
