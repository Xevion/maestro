package maestro.combat

import maestro.Agent
import maestro.api.combat.TrajectoryResult
import maestro.api.event.events.RenderEvent
import maestro.rendering.gfx.GfxLines
import maestro.rendering.gfx.GfxRenderer
import maestro.rendering.gfx.GfxRenderer.awtToArgb
import net.minecraft.world.phys.Vec3

/**
 * Renders arrow trajectory paths for debugging and visualization.
 */
object TrajectoryRenderer {
    /**
     * Render a trajectory path.
     *
     * @param event The render event
     * @param trajectory The trajectory to render
     */
    fun render(
        event: RenderEvent,
        trajectory: TrajectoryResult?,
    ) {
        if (trajectory == null) return
        if (!Agent.settings().renderTrajectory.value) return

        val points = trajectory.points
        if (points.size < 2) return

        val awtColor = Agent.settings().trajectoryColor.value
        val color = awtToArgb(awtColor)

        GfxRenderer.begin(event.modelViewStack, ignoreDepth = true)

        // Render trajectory as connected line segments
        for (i in 0 until points.size - 1) {
            val start = points[i]
            val end = points[i + 1]
            GfxLines.line(start, end, color, thickness = 0.02f)
        }

        // Render hit point marker if trajectory hit a block
        if (trajectory.hitBlock) {
            renderHitMarker(trajectory.endPoint, color)
        }

        GfxRenderer.end()
    }

    /**
     * Render a small marker at the trajectory end point.
     */
    private fun renderHitMarker(
        point: Vec3,
        color: Int,
    ) {
        val markerSize = 0.15

        // Draw a small 3D cross marker
        GfxLines.line(
            Vec3(point.x - markerSize, point.y, point.z),
            Vec3(point.x + markerSize, point.y, point.z),
            color,
            thickness = 0.03f,
        )
        GfxLines.line(
            Vec3(point.x, point.y - markerSize, point.z),
            Vec3(point.x, point.y + markerSize, point.z),
            color,
            thickness = 0.03f,
        )
        GfxLines.line(
            Vec3(point.x, point.y, point.z - markerSize),
            Vec3(point.x, point.y, point.z + markerSize),
            color,
            thickness = 0.03f,
        )
    }
}
