package maestro.debug

import maestro.Agent
import maestro.debug.hud.HudDebugRenderer
import maestro.debug.pathing.PathfindingDebugHud
import maestro.debug.pathing.PathfindingDebugRenderer
import maestro.event.events.RenderEvent
import maestro.event.listener.AbstractGameEventListener
import net.minecraft.client.gui.GuiGraphics

/**
 * Main coordinator for debug rendering system.
 *
 * Manages both 2D HUD overlays and 3D world annotations, delegating to specialized renderers based
 * on the current debug mode and active layers.
 *
 * Registered as an [AbstractGameEventListener] to receive render events each frame.
 */
class DebugRenderer(
    private val agent: Agent,
) : IDebugRenderer,
    AbstractGameEventListener {
    private val hudRenderer = HudDebugRenderer(agent)

    /** The pathfinding debug renderer (also handles interaction) */
    val pathfindingDebugRenderer = PathfindingDebugRenderer(agent)

    /** HUD overlay for pathfinding debug info */
    private val pathfindingHud = PathfindingDebugHud(pathfindingDebugRenderer)

    override fun onRenderPass(event: RenderEvent) {
        if (!Agent
                .getPrimaryAgent()
                .settings.debugEnabled.value
        ) {
            return
        }

        // Render 3D debug visualization for current movement
        val pathExecutor = agent.pathingBehavior.getCurrent()
        if (pathExecutor != null) {
            val path = pathExecutor.path
            val pos = pathExecutor.position

            // Render debug info for current movement
            if (pos >= 0 && pos < path.movements().size) {
                val movement = path.movements()[pos]

                // Cast to Movement to access debug context
                if (movement is maestro.pathing.movement.Movement) {
                    movement.debug.render3D(event.modelViewStack, event.partialTicks)
                }
            }
        }

        // Render pathfinding debug visualization if enabled
        if (Agent
                .getPrimaryAgent()
                .settings.pathfindingDebugEnabled.value
        ) {
            pathfindingDebugRenderer.onRenderPass(event)
        }
    }

    /**
     * Renders the pathfinding debug HUD overlay.
     *
     * Called from the GUI mixin when pathfinding debug is enabled.
     */
    fun renderPathfindingHud(
        graphics: GuiGraphics,
        tickDelta: Float,
    ) {
        if (Agent
                .getPrimaryAgent()
                .settings.pathfindingDebugEnabled.value
        ) {
            pathfindingHud.render(graphics, tickDelta)
        }
    }

    override fun getHudRenderer(): IHudDebugRenderer = hudRenderer
}
