package maestro.debug

import maestro.Agent
import maestro.api.debug.IDebugRenderer
import maestro.api.debug.IHudDebugRenderer
import maestro.api.event.events.RenderEvent
import maestro.api.event.listener.AbstractGameEventListener
import maestro.debug.hud.HudDebugRenderer
import maestro.debug.world.WorldDebugRenderer

/**
 * Main coordinator for debug rendering system.
 *
 * Manages both 2D HUD overlays and 3D world annotations, delegating to specialized renderers based
 * on the current debug mode and active layers.
 *
 * Registered as an [AbstractGameEventListener] to receive render events each frame.
 */
class MaestroDebugRenderer(
    private val agent: Agent,
) : IDebugRenderer,
    AbstractGameEventListener {
    private val hudRenderer = HudDebugRenderer(agent)
    private val worldRenderer = WorldDebugRenderer(agent)

    override fun onRenderPass(event: RenderEvent) {
        if (!Agent.settings().debugEnabled.value) return

        // World rendering not yet implemented in this version
        // Will be added in future phases
    }

    override fun getHudRenderer(): IHudDebugRenderer = hudRenderer

    /**
     * Get the world renderer for direct access if needed.
     *
     * @return the world renderer instance
     */
    fun getWorldRenderer(): WorldDebugRenderer = worldRenderer
}
