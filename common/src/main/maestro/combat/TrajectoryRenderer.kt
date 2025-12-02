package maestro.combat

import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.PoseStack
import maestro.Agent
import maestro.api.combat.TrajectoryResult
import maestro.api.event.events.RenderEvent
import maestro.utils.IRenderer
import net.minecraft.world.phys.Vec3

/**
 * Renders arrow trajectory paths for debugging and visualization.
 */
object TrajectoryRenderer : IRenderer {
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

        val color = Agent.settings().trajectoryColor.value
        val lineWidth = 2.0f
        val ignoreDepth = true

        val bufferBuilder = IRenderer.startLines(color, lineWidth, ignoreDepth)
        val stack = event.modelViewStack

        // Render trajectory as connected line segments
        for (i in 0 until points.size - 1) {
            val start = points[i]
            val end = points[i + 1]
            IRenderer.emitLine(bufferBuilder, stack, start, end)
        }

        IRenderer.endLines(bufferBuilder, ignoreDepth)

        // Render hit point marker if trajectory hit a block
        if (trajectory.hitBlock) {
            renderHitMarker(bufferBuilder, stack, trajectory.endPoint)
        }
    }

    /**
     * Render a small marker at the trajectory end point.
     */
    private fun renderHitMarker(
        bufferBuilder: BufferBuilder,
        stack: PoseStack,
        point: Vec3,
    ) {
        val color = Agent.settings().trajectoryColor.value
        val markerSize = 0.15

        val vpX = IRenderer.renderManager.renderPosX()
        val vpY = IRenderer.renderManager.renderPosY()
        val vpZ = IRenderer.renderManager.renderPosZ()

        val x = point.x - vpX
        val y = point.y - vpY
        val z = point.z - vpZ

        val buf = IRenderer.startLines(color, 3.0f, true)

        // Draw a small cross marker
        IRenderer.emitLine(
            buf,
            stack,
            x - markerSize,
            y,
            z,
            x + markerSize,
            y,
            z,
        )
        IRenderer.emitLine(
            buf,
            stack,
            x,
            y - markerSize,
            z,
            x,
            y + markerSize,
            z,
        )
        IRenderer.emitLine(
            buf,
            stack,
            x,
            y,
            z - markerSize,
            x,
            y,
            z + markerSize,
        )

        IRenderer.endLines(buf, true)
    }
}
