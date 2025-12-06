package maestro.rendering.gfx

import maestro.Agent
import maestro.api.event.events.RenderEvent
import maestro.api.event.listener.AbstractGameEventListener
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Demo renderer showcasing the SDF rendering API.
 *
 * Demonstrates:
 * - GfxLines: Anti-aliased lines with varying thickness
 * - GfxCircle: Billboard circles for point markers
 * - SdfQuads: Camera-facing filled quads
 * - GfxBoxes: Wireframe and filled AABBs
 * - GfxPolyline: Connected line segments with joins and caps
 *
 * Set DEMO_MODE to switch between full demo (0) and line showcase (1).
 */
class GfxDemo(
    private val agent: Agent,
) : AbstractGameEventListener {
    companion object {
        /**
         * Demo mode selection:
         * 0 = Full demo (all shapes - circles, boxes, quads, polylines)
         * 1 = Line showcase (comprehensive line/polyline feature demonstration)
         */
        private const val DEMO_MODE = 0
    }

    override fun onRenderPass(event: RenderEvent) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val center = player.position().add(0.0, 1.0, 0.0)
        val time = System.currentTimeMillis() / 1000.0

        // Use unified GfxRenderer for batching
        GfxRenderer.draw(event.modelViewStack, ignoreDepth = false) {
            when (DEMO_MODE) {
                1 -> drawLineShowcase(center, time)
                else -> drawFullDemo(center, time)
            }
        }
    }

    private fun drawFullDemo(
        center: Vec3,
        time: Double,
    ) {
        // Demo 1: Rotating star pattern of lines (GfxLines.line)
        drawStarPattern(center, time)

        // Demo 2: Circle of point markers (GfxCircle.circle with BILLBOARD)
        drawCircleOfPoints(center.add(0.0, 2.0, 0.0), time)

        // Demo 3: Horizontal ring (GfxCircle.circle with FLAT_XZ)
        drawHorizontalRing(center.subtract(0.0, 0.5, 0.0), time)

        // Demo 4: Wireframe box (GfxBoxes.wireframe)
        drawWireframeBox(center.add(3.0, 0.0, 0.0), time)

        // Demo 5: Filled box (GfxBoxes.filled)
        drawFilledBox(center.add(-3.0, 0.0, 0.0), time)

        // Demo 6: Billboard quads (SdfQuads.quad)
        drawBillboardQuads(center.add(0.0, 3.0, 0.0), time)

        // Demo 7: Horizontal quad (SdfQuads.quadHorizontal)
        drawHorizontalQuad(center.subtract(0.0, 1.0, 0.0), time)

        // Demo 8: Polyline wave (GfxPolyline.polyline)
        drawPolyline(center.add(0.0, 0.0, 3.0), 0.0) // Static for easier debugging

        // Demo 9: Block highlight (GfxBoxes.block)
        drawBlockHighlight(center, time)

        // Demo 10: Join type comparison (shows MITER, ROUND, BEVEL side by side)
        drawJoinComparison(center.add(0.0, 0.0, -3.0))
    }

    // ========================================================================
    // Line Showcase - Comprehensive line/polyline feature demonstration
    // ========================================================================

    /**
     * Comprehensive showcase of all line rendering features.
     * Shows segments, joins (MITER/ROUND/BEVEL), caps (NONE/ROUND/SQUARE),
     * closed loops, and various edge cases.
     */
    private fun drawLineShowcase(
        center: Vec3,
        time: Double,
    ) {
        val thickness = 0.04f
        val spacing = 2.0

        // Row 1: Single segments with different caps (at z = -2)
        drawCapComparison(center.add(0.0, 0.0, -spacing), thickness)

        // Row 2: Corner joins comparison (at z = 0)
        drawJoinComparison(center)

        // Row 3: Closed shapes comparison (at z = +2)
        drawClosedShapesComparison(center.add(0.0, 0.0, spacing), thickness)

        // Row 4: Multi-segment path with all join types (at z = +4)
        drawMultiSegmentComparison(center.add(0.0, 0.0, spacing * 2), thickness)
    }

    /**
     * Shows end cap types: NONE, ROUND, SQUARE side by side.
     */
    private fun drawCapComparison(
        center: Vec3,
        thickness: Float,
    ) {
        val segmentLength = 1.5
        val spacing = 2.0

        val caps =
            listOf(
                Triple(LineEndCap.NONE, GfxRenderer.Colors.RED, "NONE"),
                Triple(LineEndCap.ROUND, GfxRenderer.Colors.GREEN, "ROUND"),
                Triple(LineEndCap.SQUARE, GfxRenderer.Colors.BLUE, "SQUARE"),
            )

        for ((i, cap) in caps.withIndex()) {
            val offset = center.add((i - 1) * spacing, 0.0, 0.0)
            val points =
                listOf(
                    offset.add(-segmentLength / 2, 0.0, 0.0),
                    offset.add(segmentLength / 2, 0.0, 0.0),
                )

            GfxPolyline.polyline(
                points,
                cap.second,
                thickness,
                joins = PolylineJoins.MITER,
                endCaps = cap.first,
                geometry = LineGeometry.FLAT_XZ,
            )

            // Endpoint markers
            for (p in points) {
                GfxCircle.circle(p, radius = 0.02, color = GfxRenderer.Colors.WHITE, geometry = GfxCircle.Geometry.FLAT_XZ)
            }
        }
    }

    /**
     * Shows closed shapes: triangle, square, hexagon with different join types.
     */
    private fun drawClosedShapesComparison(
        center: Vec3,
        thickness: Float,
    ) {
        val spacing = 2.5

        // Triangle with MITER joins
        val triPoints =
            listOf(
                Vec3(0.0, 0.0, -0.5),
                Vec3(-0.4, 0.0, 0.3),
                Vec3(0.4, 0.0, 0.3),
            ).map { it.add(center.add(-spacing, 0.0, 0.0)) }
        GfxPolyline.loop(triPoints, GfxRenderer.Colors.RED, thickness, PolylineJoins.MITER, LineGeometry.FLAT_XZ)

        // Square with ROUND joins
        val sqPoints =
            listOf(
                Vec3(-0.4, 0.0, -0.4),
                Vec3(0.4, 0.0, -0.4),
                Vec3(0.4, 0.0, 0.4),
                Vec3(-0.4, 0.0, 0.4),
            ).map { it.add(center) }
        GfxPolyline.loop(sqPoints, GfxRenderer.Colors.GREEN, thickness, PolylineJoins.ROUND, LineGeometry.FLAT_XZ)

        // Hexagon with BEVEL joins
        val hexPoints =
            (0 until 6).map { i ->
                val angle = i * PI / 3
                Vec3(cos(angle) * 0.5, 0.0, sin(angle) * 0.5).add(center.add(spacing, 0.0, 0.0))
            }
        GfxPolyline.loop(hexPoints, GfxRenderer.Colors.BLUE, thickness, PolylineJoins.BEVEL, LineGeometry.FLAT_XZ)
    }

    /**
     * Shows multi-segment open paths with vertex markers.
     */
    private fun drawMultiSegmentComparison(
        center: Vec3,
        thickness: Float,
    ) {
        val basePoints =
            listOf(
                Vec3(0.0, 0.0, 0.0),
                Vec3(0.4, 0.0, 0.2),
                Vec3(0.0, 0.0, 0.4),
                Vec3(0.4, 0.0, 0.6),
                Vec3(0.0, 0.0, 0.8),
                Vec3(0.4, 0.0, 1.0),
            )

        val spacing = 1.5
        val joins =
            listOf(
                Triple(PolylineJoins.MITER, LineEndCap.SQUARE, GfxRenderer.Colors.RED),
                Triple(PolylineJoins.ROUND, LineEndCap.ROUND, GfxRenderer.Colors.GREEN),
                Triple(PolylineJoins.BEVEL, LineEndCap.NONE, GfxRenderer.Colors.BLUE),
            )

        for ((i, config) in joins.withIndex()) {
            val offset = center.add((i - 1) * spacing, 0.0, -0.5)
            val points = basePoints.map { it.add(offset) }

            GfxPolyline.polyline(
                points,
                config.third,
                thickness,
                joins = config.first,
                endCaps = config.second,
                geometry = LineGeometry.FLAT_XZ,
            )

            // Vertex markers
            for (p in points) {
                GfxCircle.circle(p, radius = 0.02, color = GfxRenderer.Colors.WHITE, geometry = GfxCircle.Geometry.FLAT_XZ)
            }
        }
    }

    private fun drawStarPattern(
        center: Vec3,
        time: Double,
    ) {
        val numLines = 12
        val length = 3.0

        for (i in 0 until numLines) {
            val angle = (i * 2 * PI / numLines) + time * 0.5
            val endX = center.x + cos(angle) * length
            val endZ = center.z + sin(angle) * length

            // Vary thickness based on line index
            val thickness = 0.02f + (i % 3) * 0.02f

            // Rainbow colors
            val hue = (i.toFloat() / numLines + time.toFloat() * 0.1f) % 1f
            val color = GfxRenderer.hsvToArgb(hue, 0.9f, 1.0f)

            GfxLines.line(
                center,
                Vec3(endX, center.y, endZ),
                color = color,
                thickness = thickness,
            )
        }

        // Draw vertical lines
        for (i in 0 until 4) {
            val angle = i * PI / 2 + time * 0.3
            val offset = 1.5
            val baseX = center.x + cos(angle) * offset
            val baseZ = center.z + sin(angle) * offset

            GfxLines.line(
                Vec3(baseX, center.y - 1.0, baseZ),
                Vec3(baseX, center.y + 2.0, baseZ),
                color = GfxRenderer.Colors.WHITE,
                thickness = 0.03f,
            )
        }
    }

    private fun drawCircleOfPoints(
        center: Vec3,
        time: Double,
    ) {
        val numPoints = 8
        val radius = 1.0

        for (i in 0 until numPoints) {
            val angle = (i * 2 * PI / numPoints) + time * 0.3
            val x = center.x + cos(angle) * radius
            val z = center.z + sin(angle) * radius

            // Vary color around the circle
            val hue = (i.toFloat() / numPoints + time.toFloat() * 0.2f) % 1f
            val color = GfxRenderer.hsvToArgb(hue, 0.8f, 1.0f)

            GfxCircle.circle(
                Vec3(x, center.y, z),
                radius = 0.15,
                color = color,
                geometry = GfxCircle.Geometry.BILLBOARD,
            )
        }
    }

    private fun drawHorizontalRing(
        center: Vec3,
        time: Double,
    ) {
        // Pulsing ring
        val pulseRadius = 2.0 + sin(time * 2) * 0.3
        val color = GfxRenderer.hsvToArgb((time.toFloat() * 0.1f) % 1f, 0.7f, 0.9f)

        GfxCircle.circle(
            center,
            radius = pulseRadius,
            color = color,
            geometry = GfxCircle.Geometry.FLAT_XZ,
        )
    }

    private fun drawWireframeBox(
        center: Vec3,
        time: Double,
    ) {
        // Rotating AABB (technically not rotating, but pulsing size)
        val size = 0.8 + sin(time * 1.5) * 0.2
        val aabb =
            AABB(
                center.x - size,
                center.y - size,
                center.z - size,
                center.x + size,
                center.y + size,
                center.z + size,
            )

        GfxBoxes.wireframe(
            aabb,
            color = GfxRenderer.Colors.CYAN,
            thickness = 0.025f,
        )
    }

    private fun drawFilledBox(
        center: Vec3,
        time: Double,
    ) {
        val size = 0.6 + cos(time * 1.2) * 0.15
        val aabb =
            AABB(
                center.x - size,
                center.y - size,
                center.z - size,
                center.x + size,
                center.y + size,
                center.z + size,
            )

        // Semi-transparent pulsing color
        val hue = (time.toFloat() * 0.15f) % 1f
        val color = GfxRenderer.hsvToArgb(hue, 0.6f, 0.9f, 0.5f)

        GfxBoxes.filled(aabb, color)
    }

    private fun drawBillboardQuads(
        center: Vec3,
        time: Double,
    ) {
        // Orbiting billboard quads
        for (i in 0 until 3) {
            val angle = (i * 2 * PI / 3) + time * 0.7
            val orbitRadius = 1.5
            val x = center.x + cos(angle) * orbitRadius
            val z = center.z + sin(angle) * orbitRadius

            val hue = (i.toFloat() / 3 + time.toFloat() * 0.3f) % 1f
            val color = GfxRenderer.hsvToArgb(hue, 0.7f, 1.0f, 0.8f)

            SdfQuads.quad(
                Vec3(x, center.y, z),
                width = 0.4,
                height = 0.6,
                color = color,
            )
        }
    }

    private fun drawHorizontalQuad(
        center: Vec3,
        time: Double,
    ) {
        // Pulsing ground marker quad
        val size = 1.5 + sin(time * 1.3) * 0.3
        val color = GfxRenderer.withAlpha(GfxRenderer.Colors.PURPLE, 0.4f)

        SdfQuads.quadHorizontal(
            center,
            width = size,
            depth = size,
            color = color,
        )
    }

    private fun drawPolyline(
        center: Vec3,
        time: Double,
    ) {
        // Animated wave polyline with smooth joins
        // High segment count for smooth curves (like Unity Shapes)
        val points = mutableListOf<Vec3>()
        val segments = 64 // Many segments for smooth curves
        val length = 4.0

        for (i in 0..segments) {
            val t = i.toDouble() / segments
            val x = center.x + (t - 0.5) * length
            val y = center.y + sin(t * 4 * PI + time * 3) * 0.3
            val z = center.z

            points.add(Vec3(x, y, z))
        }

        val color = GfxRenderer.hsvToArgb((time.toFloat() * 0.2f) % 1f, 0.8f, 1.0f)
        // Use BILLBOARD geometry - lines face the camera
        GfxPolyline.polyline(
            points,
            color,
            thickness = 0.03f,
            joins = PolylineJoins.ROUND,
            endCaps = LineEndCap.ROUND,
            geometry = LineGeometry.BILLBOARD,
        )
    }

    private fun drawBlockHighlight(
        center: Vec3,
        time: Double,
    ) {
        // Highlight the block the player is standing on
        val blockX = center.x.toInt()
        val blockY = (center.y - 1.5).toInt()
        val blockZ = center.z.toInt()

        // Pulsing highlight
        val pulse = (sin(time * 4) * 0.5 + 0.5).toFloat()
        val color = GfxRenderer.hsvToArgb(0.3f, 0.6f, 0.9f, 0.3f + pulse * 0.3f)

        GfxBoxes.block(
            blockX,
            blockY,
            blockZ,
            color = color,
            thickness = 0.015f,
            mode = GfxBoxes.BoxMode.BOTH,
            expand = 0.01,
        )
    }

    private fun drawJoinComparison(center: Vec3) {
        // Create a zigzag path to showcase different join types
        val zigzagPoints =
            listOf(
                Vec3(0.0, 0.0, 0.0),
                Vec3(0.5, 0.0, 0.3),
                Vec3(0.0, 0.0, 0.6),
                Vec3(0.5, 0.0, 0.9),
                Vec3(0.0, 0.0, 1.2),
            )

        val thickness = 0.04f
        val spacing = 1.5

        // MITER joins (sharp corners)
        val miterOffset = center.add(-spacing, 0.0, 0.0)
        val miterPoints = zigzagPoints.map { it.add(miterOffset) }
        GfxPolyline.polyline(
            miterPoints,
            GfxRenderer.Colors.RED,
            thickness,
            joins = PolylineJoins.MITER,
            endCaps = LineEndCap.SQUARE,
            geometry = LineGeometry.FLAT_XZ,
        )

        // ROUND joins (smooth corners)
        val roundOffset = center
        val roundPoints = zigzagPoints.map { it.add(roundOffset) }
        GfxPolyline.polyline(
            roundPoints,
            GfxRenderer.Colors.GREEN,
            thickness,
            joins = PolylineJoins.ROUND,
            endCaps = LineEndCap.ROUND,
            geometry = LineGeometry.FLAT_XZ,
        )

        // BEVEL joins (cut corners)
        val bevelOffset = center.add(spacing, 0.0, 0.0)
        val bevelPoints = zigzagPoints.map { it.add(bevelOffset) }
        GfxPolyline.polyline(
            bevelPoints,
            GfxRenderer.Colors.BLUE,
            thickness,
            joins = PolylineJoins.BEVEL,
            endCaps = LineEndCap.NONE,
            geometry = LineGeometry.FLAT_XZ,
        )

        // Rectangle comparison using loop
        val rectSize = 0.6
//        val rectY = center.y + 0.5
        val rectPoints =
            listOf(
                Vec3(-rectSize / 2, 0.0, -rectSize / 2),
                Vec3(rectSize / 2, 0.0, -rectSize / 2),
                Vec3(rectSize / 2, 0.0, rectSize / 2),
                Vec3(-rectSize / 2, 0.0, rectSize / 2),
            )

        // MITER rectangle
        val miterRect = rectPoints.map { it.add(miterOffset).add(0.0, 1.5, 0.6) }
        GfxPolyline.loop(miterRect, GfxRenderer.Colors.RED, thickness, PolylineJoins.MITER, LineGeometry.FLAT_XZ)

        // ROUND rectangle
        val roundRect = rectPoints.map { it.add(roundOffset).add(0.0, 1.5, 0.6) }
        GfxPolyline.loop(roundRect, GfxRenderer.Colors.GREEN, thickness, PolylineJoins.ROUND, LineGeometry.FLAT_XZ)

        // BEVEL rectangle
        val bevelRect = rectPoints.map { it.add(bevelOffset).add(0.0, 1.5, 0.6) }
        GfxPolyline.loop(bevelRect, GfxRenderer.Colors.BLUE, thickness, PolylineJoins.BEVEL, LineGeometry.FLAT_XZ)
    }
}
