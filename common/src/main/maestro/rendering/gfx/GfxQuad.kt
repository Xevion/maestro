package maestro.rendering.gfx

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.world.phys.Vec3

/**
 * SDF-based filled quad renderer with optional anti-aliased edges.
 *
 * Renders quads as camera-facing billboards with SDF-based edge smoothing.
 * Useful for highlighting areas, rendering overlays, and debug visualization.
 *
 * Usage within GfxRenderer batch:
 * ```kotlin
 * GfxRenderer.begin(poseStack)
 * GfxQuad.quad(center, 2.0, 1.0, color = 0x80FF0000.toInt())
 * GfxQuad.quadHorizontal(center, 3.0, 3.0, color = 0x8000FF00.toInt())
 * GfxRenderer.end()
 * ```
 */
object GfxQuad {
    private val tesselator = Tesselator.getInstance()

    /**
     * Draw a camera-facing (billboard) quad.
     *
     * The quad faces the camera regardless of orientation.
     *
     * @param center World-space center position
     * @param width Width in world units
     * @param height Height in world units
     * @param color ARGB color
     * @param cornerRadius Rounded corner radius (0 for sharp corners)
     */
    fun quad(
        center: Vec3,
        width: Double,
        height: Double,
        color: Int,
        cornerRadius: Float = 0f,
    ) {
        check(GfxRenderer.active) { "Must call GfxRenderer.begin() before quad()" }

        val pose = GfxRenderer.pose
        val cameraPos = GfxRenderer.camera

        // Convert to camera-relative
        val c = center.subtract(cameraPos)

        val halfW = (width / 2).toFloat()
        val halfH = (height / 2).toFloat()

        // Extract color components
        val a = GfxRenderer.alpha(color)
        val r = GfxRenderer.red(color)
        val g = GfxRenderer.green(color)
        val b = GfxRenderer.blue(color)

        // Set shader
        val program =
            net.minecraft.client.Minecraft
                .getInstance()
                .shaderManager
                .getProgram(GfxShaders.QUAD)
        if (program != null) {
            RenderSystem.setShader(program)
        }

        val buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val matrix = pose.last()

        // Billboard quad facing camera
        // We need the camera's right and up vectors
        val camera =
            net.minecraft.client.Minecraft
                .getInstance()
                .gameRenderer.mainCamera
        val cameraDir = Vec3(camera.lookVector)
        val cameraUp = Vec3(camera.upVector)
        val cameraRight = cameraDir.cross(cameraUp).normalize()

        val right = cameraRight.scale(halfW.toDouble())
        val up = cameraUp.normalize().scale(halfH.toDouble())

        // Four corners
        val p0 = c.subtract(right).subtract(up) // bottom-left
        val p1 = c.add(right).subtract(up) // bottom-right
        val p2 = c.add(right).add(up) // top-right
        val p3 = c.subtract(right).add(up) // top-left

        // UV encodes position within quad for SDF calculations
        // U: [-1, 1] horizontal
        // V: [-1, 1] vertical
        // The shader can use these for edge AA or rounded corners
        buffer
            .addVertex(matrix, p0.x.toFloat(), p0.y.toFloat(), p0.z.toFloat())
            .setUv(-1f, -1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, p1.x.toFloat(), p1.y.toFloat(), p1.z.toFloat())
            .setUv(1f, -1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, p2.x.toFloat(), p2.y.toFloat(), p2.z.toFloat())
            .setUv(1f, 1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, p3.x.toFloat(), p3.y.toFloat(), p3.z.toFloat())
            .setUv(-1f, 1f)
            .setColor(r, g, b, a)

        val mesh = buffer.build()
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh)
        }
    }

    /**
     * Draw a horizontal (XZ plane) quad at a given Y level.
     *
     * @param center World-space center position (Y is the plane height)
     * @param width Width in X direction
     * @param depth Depth in Z direction
     * @param color ARGB color
     */
    fun quadHorizontal(
        center: Vec3,
        width: Double,
        depth: Double,
        color: Int,
    ) {
        check(GfxRenderer.active) { "Must call GfxRenderer.begin() before quadHorizontal()" }

        val pose = GfxRenderer.pose
        val cameraPos = GfxRenderer.camera

        val c = center.subtract(cameraPos)
        val halfW = (width / 2).toFloat()
        val halfD = (depth / 2).toFloat()

        val a = GfxRenderer.alpha(color)
        val r = GfxRenderer.red(color)
        val g = GfxRenderer.green(color)
        val b = GfxRenderer.blue(color)

        val program =
            net.minecraft.client.Minecraft
                .getInstance()
                .shaderManager
                .getProgram(GfxShaders.QUAD)
        if (program != null) {
            RenderSystem.setShader(program)
        }

        val buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val matrix = pose.last()

        // Horizontal quad (XZ plane)
        val y = c.y.toFloat()
        val minX = (c.x - halfW).toFloat()
        val maxX = (c.x + halfW).toFloat()
        val minZ = (c.z - halfD).toFloat()
        val maxZ = (c.z + halfD).toFloat()

        // Counter-clockwise winding when viewed from above
        buffer
            .addVertex(matrix, minX, y, minZ)
            .setUv(-1f, -1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, minX, y, maxZ)
            .setUv(-1f, 1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, maxX, y, maxZ)
            .setUv(1f, 1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, maxX, y, minZ)
            .setUv(1f, -1f)
            .setColor(r, g, b, a)

        val mesh = buffer.build()
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh)
        }
    }

    /**
     * Draw a vertical quad (for walls/side faces).
     *
     * @param p1 First corner position
     * @param p2 Second corner position (defines width direction)
     * @param height Height of the quad
     * @param color ARGB color
     */
    fun quadVertical(
        p1: Vec3,
        p2: Vec3,
        height: Double,
        color: Int,
    ) {
        check(GfxRenderer.active) { "Must call GfxRenderer.begin() before quadVertical()" }

        val pose = GfxRenderer.pose
        val cameraPos = GfxRenderer.camera

        val c1 = p1.subtract(cameraPos)
        val c2 = p2.subtract(cameraPos)

        val a = GfxRenderer.alpha(color)
        val r = GfxRenderer.red(color)
        val g = GfxRenderer.green(color)
        val b = GfxRenderer.blue(color)

        val program =
            net.minecraft.client.Minecraft
                .getInstance()
                .shaderManager
                .getProgram(GfxShaders.QUAD)
        if (program != null) {
            RenderSystem.setShader(program)
        }

        val buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val matrix = pose.last()

        val y1 = c1.y.toFloat()
        val y2 = (c1.y + height).toFloat()

        buffer
            .addVertex(matrix, c1.x.toFloat(), y1, c1.z.toFloat())
            .setUv(-1f, -1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, c1.x.toFloat(), y2, c1.z.toFloat())
            .setUv(-1f, 1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, c2.x.toFloat(), y2, c2.z.toFloat())
            .setUv(1f, 1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, c2.x.toFloat(), y1, c2.z.toFloat())
            .setUv(1f, -1f)
            .setColor(r, g, b, a)

        val mesh = buffer.build()
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh)
        }
    }

    /**
     * Draw a quad defined by four arbitrary corners.
     *
     * @param p0 Bottom-left corner
     * @param p1 Bottom-right corner
     * @param p2 Top-right corner
     * @param p3 Top-left corner
     * @param color ARGB color
     */
    fun quadCorners(
        p0: Vec3,
        p1: Vec3,
        p2: Vec3,
        p3: Vec3,
        color: Int,
    ) {
        check(GfxRenderer.active) { "Must call GfxRenderer.begin() before quadCorners()" }

        val pose = GfxRenderer.pose
        val cameraPos = GfxRenderer.camera

        val c0 = p0.subtract(cameraPos)
        val c1 = p1.subtract(cameraPos)
        val c2 = p2.subtract(cameraPos)
        val c3 = p3.subtract(cameraPos)

        val a = GfxRenderer.alpha(color)
        val r = GfxRenderer.red(color)
        val g = GfxRenderer.green(color)
        val b = GfxRenderer.blue(color)

        val program =
            net.minecraft.client.Minecraft
                .getInstance()
                .shaderManager
                .getProgram(GfxShaders.QUAD)
        if (program != null) {
            RenderSystem.setShader(program)
        }

        val buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val matrix = pose.last()

        buffer
            .addVertex(matrix, c0.x.toFloat(), c0.y.toFloat(), c0.z.toFloat())
            .setUv(-1f, -1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, c1.x.toFloat(), c1.y.toFloat(), c1.z.toFloat())
            .setUv(1f, -1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, c2.x.toFloat(), c2.y.toFloat(), c2.z.toFloat())
            .setUv(1f, 1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, c3.x.toFloat(), c3.y.toFloat(), c3.z.toFloat())
            .setUv(-1f, 1f)
            .setColor(r, g, b, a)

        val mesh = buffer.build()
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh)
        }
    }
}
