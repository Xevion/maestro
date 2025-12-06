package maestro.rendering.gfx

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3

/**
 * High-quality line renderer using SDF-based antialiasing.
 *
 * Unlike Minecraft's built-in line rendering which expands lines in the vertex shader,
 * this uses a fragment shader with `fwidth()` for smooth edges at any zoom level.
 *
 * Can be used either standalone or within an GfxRenderer batch:
 *
 * Standalone usage:
 * ```kotlin
 * GfxLines.begin(poseStack, ignoreDepth = true)
 * GfxLines.line(start, end, color = 0xFFFF0000.toInt(), thickness = 0.05f)
 * GfxLines.end()
 * ```
 *
 * Batched with other SDF primitives:
 * ```kotlin
 * GfxRenderer.begin(poseStack, ignoreDepth = true)
 * GfxLines.line(start, end, color = 0xFFFF0000.toInt())
 * GfxQuad.quad(center, width, height, color = 0xFF00FF00.toInt())
 * GfxRenderer.end()
 * ```
 */
object GfxLines {
    private val tesselator = Tesselator.getInstance()
    private var standaloneMode = false
    private var standalonePose: PoseStack? = null
    private var standaloneCameraPos = Vec3.ZERO
    private var standaloneDepthDisabled = false

    /**
     * Begin a standalone line rendering batch.
     *
     * For use outside GfxRenderer.begin/end blocks. If you're already
     * inside an GfxRenderer block, you can call line() directly.
     *
     * @param poseStack The current pose stack (should have camera transforms applied)
     * @param ignoreDepth If true, lines render through blocks
     */
    fun begin(
        poseStack: PoseStack,
        ignoreDepth: Boolean = false,
    ) {
        check(!standaloneMode) { "GfxLines.begin() called while already active" }
        check(!GfxRenderer.active) { "Use GfxLines.line() directly when GfxRenderer is active" }

        val mc = Minecraft.getInstance()
        val camera = mc.gameRenderer.mainCamera
        standaloneCameraPos = camera.position

        standalonePose = poseStack
        standaloneDepthDisabled = ignoreDepth
        standaloneMode = true

        // Set up render state
        GfxRenderer.begin(poseStack, ignoreDepth)
    }

    /**
     * Draw a line segment with SDF anti-aliasing.
     *
     * @param start World-space start position
     * @param end World-space end position
     * @param color ARGB color (e.g., 0xFFFF0000 for red)
     * @param thickness Line thickness in world units
     */
    fun line(
        start: Vec3,
        end: Vec3,
        color: Int,
        thickness: Float = 0.05f,
    ) {
        val pose: PoseStack
        val cameraPos: Vec3

        if (standaloneMode) {
            pose = standalonePose ?: error("Standalone mode but no pose")
            cameraPos = standaloneCameraPos
        } else if (GfxRenderer.active) {
            pose = GfxRenderer.pose
            cameraPos = GfxRenderer.camera
        } else {
            error("Must call GfxLines.begin() or GfxRenderer.begin() before line()")
        }

        // Convert to camera-relative coordinates
        val s = start.subtract(cameraPos)
        val e = end.subtract(cameraPos)

        // Direction along the line
        val dir = e.subtract(s).normalize()

        // Calculate perpendicular vector (for quad expansion)
        val toCamera = s.scale(-1.0).normalize()
        var perp = dir.cross(toCamera)

        // Handle degenerate case where line points directly at camera
        if (perp.lengthSqr() < 0.0001) {
            perp = Vec3(1.0, 0.0, 0.0).cross(dir)
            if (perp.lengthSqr() < 0.0001) {
                perp = Vec3(0.0, 1.0, 0.0).cross(dir)
            }
        }
        perp = perp.normalize().scale(thickness.toDouble())

        // Extract color components
        val a = GfxRenderer.alpha(color)
        val r = GfxRenderer.red(color)
        val g = GfxRenderer.green(color)
        val b = GfxRenderer.blue(color)

        // Set the SDF line shader
        val mc = Minecraft.getInstance()
        val program = mc.shaderManager.getProgram(GfxShaders.LINE)
        if (program != null) {
            RenderSystem.setShader(program)
        }

        // Build quad vertices
        val buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val matrix = pose.last()

        // Four corners of the line quad
        val s0 = s.subtract(perp)
        val s1 = s.add(perp)
        val e0 = e.subtract(perp)
        val e1 = e.add(perp)

        buffer
            .addVertex(matrix, s0.x.toFloat(), s0.y.toFloat(), s0.z.toFloat())
            .setUv(0f, -1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, s1.x.toFloat(), s1.y.toFloat(), s1.z.toFloat())
            .setUv(0f, 1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, e1.x.toFloat(), e1.y.toFloat(), e1.z.toFloat())
            .setUv(1f, 1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, e0.x.toFloat(), e0.y.toFloat(), e0.z.toFloat())
            .setUv(1f, -1f)
            .setColor(r, g, b, a)

        val mesh = buffer.build()
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh)
        }
    }

    /**
     * Draw multiple connected line segments.
     *
     * @param points List of world-space positions
     * @param color ARGB color
     * @param thickness Line thickness in world units
     * @param closed If true, connect last point to first
     */
    fun polyline(
        points: List<Vec3>,
        color: Int,
        thickness: Float = 0.05f,
        closed: Boolean = false,
    ) {
        if (points.size < 2) return

        for (i in 0 until points.size - 1) {
            line(points[i], points[i + 1], color, thickness)
        }

        if (closed && points.size >= 3) {
            line(points.last(), points.first(), color, thickness)
        }
    }

    /**
     * End the standalone line rendering batch.
     */
    fun end() {
        check(standaloneMode) { "GfxLines.end() called without matching begin()" }

        GfxRenderer.end()

        standalonePose = null
        standaloneDepthDisabled = false
        standaloneMode = false
    }
}
