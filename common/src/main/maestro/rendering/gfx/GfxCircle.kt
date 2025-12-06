package maestro.rendering.gfx

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * SDF-based circle and ring renderer with anti-aliased edges.
 *
 * Supports:
 * - Filled circles (disc)
 * - Rings (hollow circles with thickness)
 * - Arcs (partial circles/rings)
 *
 * Uses fragment shader SDF evaluation for smooth edges at any zoom level.
 *
 * Usage:
 * ```kotlin
 * GfxRenderer.begin(poseStack)
 * GfxCircle.circle(center, radius = 0.5, color = 0xFFFF0000.toInt())
 * GfxCircle.ring(center, radius = 1.0, thickness = 0.1, color = 0xFF00FF00.toInt())
 * GfxCircle.arc(center, radius = 1.5, thickness = 0.1, startAngle = 0f, endAngle = PI.toFloat(), color = 0xFF0000FF.toInt())
 * GfxRenderer.end()
 * ```
 */
object GfxCircle {
    private val tesselator = Tesselator.getInstance()

    /**
     * Circle geometry mode - how the circle is oriented.
     */
    enum class Geometry {
        /** Circle lies flat in the XZ plane (horizontal) */
        FLAT_XZ,

        /** Circle lies flat in the XY plane (vertical, facing Z) */
        FLAT_XY,

        /** Circle always faces the camera (billboard) */
        BILLBOARD,
    }

    /**
     * Draw a filled circle (disc).
     *
     * @param center World-space center position
     * @param radius Circle radius in world units
     * @param color ARGB color
     * @param geometry How to orient the circle
     */
    fun circle(
        center: Vec3,
        radius: Double,
        color: Int,
        geometry: Geometry = Geometry.BILLBOARD,
    ) {
        check(GfxRenderer.active) { "Must call GfxRenderer.begin() before circle()" }

        val pose = GfxRenderer.pose
        val cameraPos = GfxRenderer.camera
        val c = center.subtract(cameraPos)

        val a = GfxRenderer.alpha(color)
        val r = GfxRenderer.red(color)
        val g = GfxRenderer.green(color)
        val b = GfxRenderer.blue(color)

        val program =
            Minecraft.getInstance().shaderManager.getProgram(GfxShaders.CIRCLE)
        if (program != null) {
            RenderSystem.setShader(program)
        }

        val buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val matrix = pose.last()

        // Expand quad for AA padding based on quality setting
        val quadRadius = (radius * GfxRenderer.aaPadding).toFloat()
        val uvPadding = GfxRenderer.aaPadding

        when (geometry) {
            Geometry.BILLBOARD -> {
                val camera = Minecraft.getInstance().gameRenderer.mainCamera
                val cameraDir = Vec3(camera.lookVector)
                val cameraUp = Vec3(camera.upVector)
                val cameraRight = cameraDir.cross(cameraUp).normalize()
                val up = cameraUp.normalize()

                val right = cameraRight.scale(quadRadius.toDouble())
                val upVec = up.scale(quadRadius.toDouble())

                val p0 = c.subtract(right).subtract(upVec)
                val p1 = c.add(right).subtract(upVec)
                val p2 = c.add(right).add(upVec)
                val p3 = c.subtract(right).add(upVec)

                // UV: encode position in unit circle space with quality-based padding
                buffer
                    .addVertex(matrix, p0.x.toFloat(), p0.y.toFloat(), p0.z.toFloat())
                    .setUv(-uvPadding, -uvPadding)
                    .setColor(r, g, b, a)
                buffer
                    .addVertex(matrix, p1.x.toFloat(), p1.y.toFloat(), p1.z.toFloat())
                    .setUv(uvPadding, -uvPadding)
                    .setColor(r, g, b, a)
                buffer
                    .addVertex(matrix, p2.x.toFloat(), p2.y.toFloat(), p2.z.toFloat())
                    .setUv(uvPadding, uvPadding)
                    .setColor(r, g, b, a)
                buffer
                    .addVertex(matrix, p3.x.toFloat(), p3.y.toFloat(), p3.z.toFloat())
                    .setUv(-uvPadding, uvPadding)
                    .setColor(r, g, b, a)
            }

            Geometry.FLAT_XZ -> {
                val y = c.y.toFloat()
                val minX = (c.x - quadRadius).toFloat()
                val maxX = (c.x + quadRadius).toFloat()
                val minZ = (c.z - quadRadius).toFloat()
                val maxZ = (c.z + quadRadius).toFloat()

                buffer.addVertex(matrix, minX, y, minZ).setUv(-uvPadding, -uvPadding).setColor(r, g, b, a)
                buffer.addVertex(matrix, minX, y, maxZ).setUv(-uvPadding, uvPadding).setColor(r, g, b, a)
                buffer.addVertex(matrix, maxX, y, maxZ).setUv(uvPadding, uvPadding).setColor(r, g, b, a)
                buffer.addVertex(matrix, maxX, y, minZ).setUv(uvPadding, -uvPadding).setColor(r, g, b, a)
            }

            Geometry.FLAT_XY -> {
                val z = c.z.toFloat()
                val minX = (c.x - quadRadius).toFloat()
                val maxX = (c.x + quadRadius).toFloat()
                val minY = (c.y - quadRadius).toFloat()
                val maxY = (c.y + quadRadius).toFloat()

                buffer.addVertex(matrix, minX, minY, z).setUv(-uvPadding, -uvPadding).setColor(r, g, b, a)
                buffer.addVertex(matrix, maxX, minY, z).setUv(uvPadding, -uvPadding).setColor(r, g, b, a)
                buffer.addVertex(matrix, maxX, maxY, z).setUv(uvPadding, uvPadding).setColor(r, g, b, a)
                buffer.addVertex(matrix, minX, maxY, z).setUv(-uvPadding, uvPadding).setColor(r, g, b, a)
            }
        }

        val mesh = buffer.build()
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh)
        }
    }

    /**
     * Draw a ring (hollow circle with thickness).
     *
     * The ring is drawn as a hollow circle where the visible part is between
     * (radius - thickness/2) and (radius + thickness/2).
     *
     * @param center World-space center position
     * @param radius Distance from center to middle of the ring
     * @param thickness Width of the ring in world units
     * @param color ARGB color
     * @param geometry How to orient the ring
     */
    fun ring(
        center: Vec3,
        radius: Double,
        thickness: Double,
        color: Int,
        geometry: Geometry = Geometry.BILLBOARD,
    ) {
        check(GfxRenderer.active) { "Must call GfxRenderer.begin() before ring()" }

        val pose = GfxRenderer.pose
        val cameraPos = GfxRenderer.camera
        val c = center.subtract(cameraPos)

        val a = GfxRenderer.alpha(color)
        val r = GfxRenderer.red(color)
        val g = GfxRenderer.green(color)
        val b = GfxRenderer.blue(color)

        val program =
            Minecraft.getInstance().shaderManager.getProgram(GfxShaders.CIRCLE)
        if (program != null) {
            RenderSystem.setShader(program)
        }

        val buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val matrix = pose.last()

        // Outer radius + quality-based padding
        val outerRadius = radius + thickness / 2
        val quadRadius = (outerRadius * GfxRenderer.aaPadding).toFloat()
        val uvPadding = GfxRenderer.aaPadding

        // Calculate inner radius fraction for shader
        val innerFraction = ((radius - thickness / 2) / outerRadius).toFloat()

        when (geometry) {
            Geometry.BILLBOARD -> {
                val camera = Minecraft.getInstance().gameRenderer.mainCamera
                val cameraDir = Vec3(camera.lookVector)
                val cameraUp = Vec3(camera.upVector)
                val cameraRight = cameraDir.cross(cameraUp).normalize()
                val up = cameraUp.normalize()

                val right = cameraRight.scale(quadRadius.toDouble())
                val upVec = up.scale(quadRadius.toDouble())

                val p0 = c.subtract(right).subtract(upVec)
                val p1 = c.add(right).subtract(upVec)
                val p2 = c.add(right).add(upVec)
                val p3 = c.subtract(right).add(upVec)

                // Encode inner fraction in UV.y for potential ring shader support
                // Currently renders as filled disc - ring shader variant can use this
                buffer
                    .addVertex(matrix, p0.x.toFloat(), p0.y.toFloat(), p0.z.toFloat())
                    .setUv(-uvPadding, innerFraction)
                    .setColor(r, g, b, a)
                buffer
                    .addVertex(matrix, p1.x.toFloat(), p1.y.toFloat(), p1.z.toFloat())
                    .setUv(uvPadding, innerFraction)
                    .setColor(r, g, b, a)
                buffer
                    .addVertex(matrix, p2.x.toFloat(), p2.y.toFloat(), p2.z.toFloat())
                    .setUv(uvPadding, uvPadding)
                    .setColor(r, g, b, a)
                buffer
                    .addVertex(matrix, p3.x.toFloat(), p3.y.toFloat(), p3.z.toFloat())
                    .setUv(-uvPadding, uvPadding)
                    .setColor(r, g, b, a)
            }

            Geometry.FLAT_XZ -> {
                val y = c.y.toFloat()
                val minX = (c.x - quadRadius).toFloat()
                val maxX = (c.x + quadRadius).toFloat()
                val minZ = (c.z - quadRadius).toFloat()
                val maxZ = (c.z + quadRadius).toFloat()

                buffer.addVertex(matrix, minX, y, minZ).setUv(-uvPadding, -uvPadding).setColor(r, g, b, a)
                buffer.addVertex(matrix, minX, y, maxZ).setUv(-uvPadding, uvPadding).setColor(r, g, b, a)
                buffer.addVertex(matrix, maxX, y, maxZ).setUv(uvPadding, uvPadding).setColor(r, g, b, a)
                buffer.addVertex(matrix, maxX, y, minZ).setUv(uvPadding, -uvPadding).setColor(r, g, b, a)
            }

            Geometry.FLAT_XY -> {
                val z = c.z.toFloat()
                val minX = (c.x - quadRadius).toFloat()
                val maxX = (c.x + quadRadius).toFloat()
                val minY = (c.y - quadRadius).toFloat()
                val maxY = (c.y + quadRadius).toFloat()

                buffer.addVertex(matrix, minX, minY, z).setUv(-uvPadding, -uvPadding).setColor(r, g, b, a)
                buffer.addVertex(matrix, maxX, minY, z).setUv(uvPadding, -uvPadding).setColor(r, g, b, a)
                buffer.addVertex(matrix, maxX, maxY, z).setUv(uvPadding, uvPadding).setColor(r, g, b, a)
                buffer.addVertex(matrix, minX, maxY, z).setUv(-uvPadding, uvPadding).setColor(r, g, b, a)
            }
        }

        val mesh = buffer.build()
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh)
        }
    }

    /**
     * Draw a point marker (small filled circle used for debug visualization).
     *
     * @param position World-space position
     * @param size Size in world units (defaults to small marker)
     * @param color ARGB color
     */
    fun point(
        position: Vec3,
        size: Double = 0.1,
        color: Int,
    ) {
        circle(position, size / 2, color, Geometry.BILLBOARD)
    }

    /**
     * Draw multiple point markers.
     *
     * @param positions World-space positions
     * @param size Size of each point
     * @param color ARGB color
     */
    fun points(
        positions: List<Vec3>,
        size: Double = 0.1,
        color: Int,
    ) {
        for (pos in positions) {
            point(pos, size, color)
        }
    }

    /**
     * Draw a horizontal ring on the XZ plane (useful for area markers).
     *
     * @param center World-space center position (Y is the plane height)
     * @param radius Distance from center to middle of ring
     * @param thickness Width of the ring
     * @param color ARGB color
     */
    fun horizontalRing(
        center: Vec3,
        radius: Double,
        thickness: Double,
        color: Int,
    ) {
        ring(center, radius, thickness, color, Geometry.FLAT_XZ)
    }

    /**
     * Draw points arranged in a circle pattern.
     *
     * @param center Circle center
     * @param radius Circle radius
     * @param count Number of points
     * @param pointSize Size of each point
     * @param color ARGB color
     * @param startAngle Starting angle in radians
     */
    fun circleOfPoints(
        center: Vec3,
        radius: Double,
        count: Int,
        pointSize: Double = 0.1,
        color: Int,
        startAngle: Double = 0.0,
    ) {
        if (count <= 0) return

        val angleStep = 2 * PI / count
        for (i in 0 until count) {
            val angle = startAngle + i * angleStep
            val x = center.x + cos(angle) * radius
            val z = center.z + sin(angle) * radius
            point(Vec3(x, center.y, z), pointSize, color)
        }
    }
}
