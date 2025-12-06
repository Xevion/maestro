package maestro.rendering.gfx

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * SDF-based axis-aligned box (AABB) renderer with wireframe and filled modes.
 *
 * Renders cubes as camera-relative geometry with smooth edges.
 *
 * Usage:
 * ```kotlin
 * GfxRenderer.begin(poseStack)
 * GfxCube.wireframe(aabb, color = 0xFFFF0000.toInt(), thickness = 0.02f)
 * GfxCube.filled(aabb, color = 0x8000FF00.toInt())
 * GfxRenderer.end()
 * ```
 */
object GfxCube {
    private val tesselator = Tesselator.getInstance()

    /**
     * Draw a wireframe AABB using SDF anti-aliased polylines with proper corner joins.
     *
     * Uses flat geometry modes for horizontal faces (FLAT_XZ) to ensure consistent
     * line orientation, and billboard lines for vertical edges.
     *
     * @param aabb The axis-aligned bounding box in world coordinates
     * @param color ARGB color
     * @param thickness Line thickness in world units
     * @param joins Join style for corners (MITER works well for 90° corners)
     */
    fun wireframe(
        aabb: AABB,
        color: Int,
        thickness: Float = 0.02f,
        joins: PolylineJoins = PolylineJoins.MITER,
    ) {
        check(GfxRenderer.active) { "Must call GfxRenderer.begin() before wireframe()" }

        val minX = aabb.minX
        val minY = aabb.minY
        val minZ = aabb.minZ
        val maxX = aabb.maxX
        val maxY = aabb.maxY
        val maxZ = aabb.maxZ

        // Bottom face as closed polyline - use FLAT_XZ for horizontal plane
        val bottomFace =
            listOf(
                Vec3(minX, minY, minZ),
                Vec3(maxX, minY, minZ),
                Vec3(maxX, minY, maxZ),
                Vec3(minX, minY, maxZ),
            )
        GfxPolyline.loop(bottomFace, color, thickness, joins, LineGeometry.FLAT_XZ)

        // Top face as closed polyline - use FLAT_XZ for horizontal plane
        val topFace =
            listOf(
                Vec3(minX, maxY, minZ),
                Vec3(maxX, maxY, minZ),
                Vec3(maxX, maxY, maxZ),
                Vec3(minX, maxY, maxZ),
            )
        GfxPolyline.loop(topFace, color, thickness, joins, LineGeometry.FLAT_XZ)

        // Vertical edges (pillars) - use billboard for camera-facing lines
        GfxLines.line(Vec3(minX, minY, minZ), Vec3(minX, maxY, minZ), color, thickness)
        GfxLines.line(Vec3(maxX, minY, minZ), Vec3(maxX, maxY, minZ), color, thickness)
        GfxLines.line(Vec3(maxX, minY, maxZ), Vec3(maxX, maxY, maxZ), color, thickness)
        GfxLines.line(Vec3(minX, minY, maxZ), Vec3(minX, maxY, maxZ), color, thickness)
    }

    /**
     * Draw a wireframe box at a position with given size.
     *
     * @param center World-space center position
     * @param sizeX Width in X direction
     * @param sizeY Height in Y direction
     * @param sizeZ Depth in Z direction
     * @param color ARGB color
     * @param thickness Line thickness
     * @param joins Join style for corners (MITER works well for 90° corners)
     */
    fun wireframe(
        center: Vec3,
        sizeX: Double,
        sizeY: Double,
        sizeZ: Double,
        color: Int,
        thickness: Float = 0.02f,
        joins: PolylineJoins = PolylineJoins.MITER,
    ) {
        val halfX = sizeX / 2
        val halfY = sizeY / 2
        val halfZ = sizeZ / 2
        wireframe(
            AABB(
                center.x - halfX,
                center.y - halfY,
                center.z - halfZ,
                center.x + halfX,
                center.y + halfY,
                center.z + halfZ,
            ),
            color,
            thickness,
            joins,
        )
    }

    /**
     * Draw a filled AABB with SDF anti-aliased edges.
     *
     * Renders all 6 faces of the box with optional transparency.
     *
     * @param aabb The axis-aligned bounding box in world coordinates
     * @param color ARGB color (alpha controls transparency)
     */
    fun filled(
        aabb: AABB,
        color: Int,
    ) {
        check(GfxRenderer.active) { "Must call GfxRenderer.begin() before filled()" }

        val pose = GfxRenderer.pose
        val cameraPos = GfxRenderer.camera

        val a = GfxRenderer.alpha(color)
        val r = GfxRenderer.red(color)
        val g = GfxRenderer.green(color)
        val b = GfxRenderer.blue(color)

        // Camera-relative coordinates
        val minX = (aabb.minX - cameraPos.x).toFloat()
        val minY = (aabb.minY - cameraPos.y).toFloat()
        val minZ = (aabb.minZ - cameraPos.z).toFloat()
        val maxX = (aabb.maxX - cameraPos.x).toFloat()
        val maxY = (aabb.maxY - cameraPos.y).toFloat()
        val maxZ = (aabb.maxZ - cameraPos.z).toFloat()

        val program =
            Minecraft.getInstance().shaderManager.getProgram(GfxShaders.QUAD)
        if (program != null) {
            RenderSystem.setShader(program)
        }

        val buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val matrix = pose.last()

        // Bottom face (Y-)
        buffer.addVertex(matrix, minX, minY, minZ).setUv(-1f, -1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, minY, maxZ).setUv(-1f, 1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, minY, maxZ).setUv(1f, 1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, minY, minZ).setUv(1f, -1f).setColor(r, g, b, a)

        // Top face (Y+)
        buffer.addVertex(matrix, minX, maxY, minZ).setUv(-1f, -1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, maxY, minZ).setUv(1f, -1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, maxY, maxZ).setUv(1f, 1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, maxY, maxZ).setUv(-1f, 1f).setColor(r, g, b, a)

        // North face (Z-)
        buffer.addVertex(matrix, minX, minY, minZ).setUv(-1f, -1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, minY, minZ).setUv(1f, -1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, maxY, minZ).setUv(1f, 1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, maxY, minZ).setUv(-1f, 1f).setColor(r, g, b, a)

        // South face (Z+)
        buffer.addVertex(matrix, minX, minY, maxZ).setUv(-1f, -1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, maxY, maxZ).setUv(-1f, 1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, maxY, maxZ).setUv(1f, 1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, minY, maxZ).setUv(1f, -1f).setColor(r, g, b, a)

        // West face (X-)
        buffer.addVertex(matrix, minX, minY, minZ).setUv(-1f, -1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, maxY, minZ).setUv(-1f, 1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, maxY, maxZ).setUv(1f, 1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, minX, minY, maxZ).setUv(1f, -1f).setColor(r, g, b, a)

        // East face (X+)
        buffer.addVertex(matrix, maxX, minY, minZ).setUv(-1f, -1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, minY, maxZ).setUv(1f, -1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, maxY, maxZ).setUv(1f, 1f).setColor(r, g, b, a)
        buffer.addVertex(matrix, maxX, maxY, minZ).setUv(-1f, 1f).setColor(r, g, b, a)

        val mesh = buffer.build()
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh)
        }
    }

    /**
     * Draw a filled box at a position with given size.
     *
     * @param center World-space center position
     * @param sizeX Width in X direction
     * @param sizeY Height in Y direction
     * @param sizeZ Depth in Z direction
     * @param color ARGB color
     */
    fun filled(
        center: Vec3,
        sizeX: Double,
        sizeY: Double,
        sizeZ: Double,
        color: Int,
    ) {
        val halfX = sizeX / 2
        val halfY = sizeY / 2
        val halfZ = sizeZ / 2
        filled(
            AABB(
                center.x - halfX,
                center.y - halfY,
                center.z - halfZ,
                center.x + halfX,
                center.y + halfY,
                center.z + halfZ,
            ),
            color,
        )
    }

    /**
     * Draw a unit-cube block highlight (useful for block selection).
     *
     * @param blockX Block X coordinate
     * @param blockY Block Y coordinate
     * @param blockZ Block Z coordinate
     * @param color ARGB color
     * @param thickness Line thickness for wireframe
     * @param mode Render mode (wireframe, filled, or both)
     * @param expand Expansion amount (negative shrinks, positive grows)
     * @param joins Join style for corners (MITER works well for 90° corners)
     */
    fun block(
        blockX: Int,
        blockY: Int,
        blockZ: Int,
        color: Int,
        thickness: Float = 0.02f,
        mode: BoxMode = BoxMode.WIREFRAME,
        expand: Double = 0.0,
        joins: PolylineJoins = PolylineJoins.MITER,
    ) {
        val aabb =
            AABB(
                blockX - expand,
                blockY - expand,
                blockZ - expand,
                blockX + 1.0 + expand,
                blockY + 1.0 + expand,
                blockZ + 1.0 + expand,
            )

        when (mode) {
            BoxMode.WIREFRAME -> wireframe(aabb, color, thickness, joins)
            BoxMode.FILLED -> filled(aabb, color)
            BoxMode.BOTH -> {
                filled(aabb, GfxRenderer.withAlpha(color, 0.3f))
                wireframe(aabb, color, thickness, joins)
            }
        }
    }

    /**
     * Render mode for box drawing.
     */
    enum class BoxMode {
        WIREFRAME,
        FILLED,
        BOTH,
    }
}
