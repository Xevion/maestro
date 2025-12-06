package maestro.rendering.gfx

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * SDF-based polyline renderer with proper corner join handling.
 *
 * Each line segment is rendered as two quads (one for each side).
 * At corners, the "outer" side (outside of the turn) uses the miter offset while
 * the "inner" side (inside of the turn) uses simple perpendiculars.
 *
 * Supports various join styles (miter, bevel, round), end caps, per-vertex colors,
 * and different geometry modes.
 *
 * Usage:
 * ```kotlin
 * GfxRenderer.begin(poseStack)
 *
 * GfxPolyline.polyline(
 *     points = listOf(p1, p2, p3, p4),
 *     color = 0xFFFF0000.toInt(),
 *     thickness = 0.05f,
 *     joins = PolylineJoins.ROUND,
 *     geometry = LineGeometry.BILLBOARD,
 *     closed = true
 * )
 *
 * GfxRenderer.end()
 * ```
 */
object GfxPolyline {
    private val tesselator = Tesselator.getInstance()

    /** Segments for round joins/caps */
    private const val ROUND_SEGMENTS = 8

    /** UV.x offset to signal round geometry to the shader (joins/caps use radial SDF) */
    private const val ROUND_GEOMETRY_UV_OFFSET = 2.0f

    /** UV.x offset to signal bevel/triangle geometry to the shader (edge-distance SDF) */
    private const val BEVEL_GEOMETRY_UV_OFFSET = 4.0f

    // ========================================================================
    // Main Rendering Methods
    // ========================================================================

    /**
     * Draw a polyline from a PolylinePath with full feature support.
     */
    fun path(
        path: PolylinePath,
        thickness: Float = 0.05f,
        joins: PolylineJoins = PolylineJoins.MITER,
        endCaps: LineEndCap = LineEndCap.ROUND,
        geometry: LineGeometry = LineGeometry.BILLBOARD,
        closed: Boolean = false,
        miterLimit: Float? = null,
    ) {
        if (!path.isDrawable) return
        drawPolylinePoints(path.points, thickness, joins, endCaps, geometry, closed, miterLimit)
    }

    /**
     * Draw a polyline from a list of positions with uniform color.
     */
    fun polyline(
        points: List<Vec3>,
        color: Int,
        thickness: Float = 0.05f,
        joins: PolylineJoins = PolylineJoins.MITER,
        endCaps: LineEndCap = LineEndCap.ROUND,
        geometry: LineGeometry = LineGeometry.BILLBOARD,
        closed: Boolean = false,
        miterLimit: Float? = null,
    ) {
        if (points.size < 2) return
        val polylinePoints = points.map { PolylinePoint(it, color) }
        drawPolylinePoints(polylinePoints, thickness, joins, endCaps, geometry, closed, miterLimit)
    }

    /**
     * Draw a closed loop polyline (convenience method).
     */
    fun loop(
        points: List<Vec3>,
        color: Int,
        thickness: Float = 0.05f,
        joins: PolylineJoins = PolylineJoins.MITER,
        geometry: LineGeometry = LineGeometry.BILLBOARD,
    ) {
        polyline(points, color, thickness, joins, LineEndCap.NONE, geometry, closed = true)
    }

    /**
     * Draw a closed loop from a PolylinePath.
     */
    fun loop(
        path: PolylinePath,
        thickness: Float = 0.05f,
        joins: PolylineJoins = PolylineJoins.MITER,
        geometry: LineGeometry = LineGeometry.BILLBOARD,
    ) {
        path(path, thickness, joins, LineEndCap.NONE, geometry, closed = true)
    }

    // ========================================================================
    // Shape Helpers
    // ========================================================================

    /**
     * Draw a rectangle outline with proper corner joins.
     */
    fun rectangle(
        center: Vec3,
        width: Double,
        height: Double,
        color: Int,
        thickness: Float = 0.05f,
        geometry: LineGeometry = LineGeometry.FLAT_XZ,
        joins: PolylineJoins = PolylineJoins.MITER,
    ) {
        val halfW = width / 2
        val halfH = height / 2

        val points =
            when (geometry) {
                LineGeometry.FLAT_XZ -> {
                    listOf(
                        Vec3(center.x - halfW, center.y, center.z - halfH),
                        Vec3(center.x + halfW, center.y, center.z - halfH),
                        Vec3(center.x + halfW, center.y, center.z + halfH),
                        Vec3(center.x - halfW, center.y, center.z + halfH),
                    )
                }
                LineGeometry.FLAT_XY -> {
                    listOf(
                        Vec3(center.x - halfW, center.y - halfH, center.z),
                        Vec3(center.x + halfW, center.y - halfH, center.z),
                        Vec3(center.x + halfW, center.y + halfH, center.z),
                        Vec3(center.x - halfW, center.y + halfH, center.z),
                    )
                }
                LineGeometry.FLAT_YZ -> {
                    listOf(
                        Vec3(center.x, center.y - halfH, center.z - halfW),
                        Vec3(center.x, center.y - halfH, center.z + halfW),
                        Vec3(center.x, center.y + halfH, center.z + halfW),
                        Vec3(center.x, center.y + halfH, center.z - halfW),
                    )
                }
                LineGeometry.BILLBOARD -> {
                    listOf(
                        Vec3(center.x - halfW, center.y, center.z - halfH),
                        Vec3(center.x + halfW, center.y, center.z - halfH),
                        Vec3(center.x + halfW, center.y, center.z + halfH),
                        Vec3(center.x - halfW, center.y, center.z + halfH),
                    )
                }
            }

        loop(points, color, thickness, joins, geometry)
    }

    /**
     * Draw a circle outline.
     */
    fun circle(
        center: Vec3,
        radius: Double,
        color: Int,
        thickness: Float = 0.05f,
        segments: Int = 32,
        normal: Vec3 = Vec3(0.0, 1.0, 0.0),
    ) {
        val path = PolylinePath.circle(center, radius, normal, segments, color)
        val geometry =
            when {
                abs(normal.y) > 0.9 -> LineGeometry.FLAT_XZ
                abs(normal.z) > 0.9 -> LineGeometry.FLAT_XY
                abs(normal.x) > 0.9 -> LineGeometry.FLAT_YZ
                else -> LineGeometry.BILLBOARD
            }
        loop(path, thickness, PolylineJoins.ROUND, geometry)
    }

    /**
     * Draw a regular polygon outline.
     */
    fun regularPolygon(
        center: Vec3,
        radius: Double,
        sides: Int,
        color: Int,
        thickness: Float = 0.05f,
        startAngle: Double = 0.0,
        normal: Vec3 = Vec3(0.0, 1.0, 0.0),
        joins: PolylineJoins = PolylineJoins.MITER,
    ) {
        val path = PolylinePath.regularPolygon(center, radius, sides, normal, startAngle, color)
        val geometry =
            when {
                abs(normal.y) > 0.9 -> LineGeometry.FLAT_XZ
                abs(normal.z) > 0.9 -> LineGeometry.FLAT_XY
                abs(normal.x) > 0.9 -> LineGeometry.FLAT_YZ
                else -> LineGeometry.BILLBOARD
            }
        loop(path, thickness, joins, geometry)
    }

    // ========================================================================
    // Core Rendering Implementation
    // ========================================================================

    private fun drawPolylinePoints(
        points: List<PolylinePoint>,
        baseThickness: Float,
        joins: PolylineJoins,
        endCaps: LineEndCap,
        geometry: LineGeometry,
        closed: Boolean,
        miterLimit: Float?,
    ) {
        val effectiveMiterLimit = miterLimit ?: GfxRenderer.miterLimit
        val effectiveBaseThickness = GfxRenderer.constrainThickness(baseThickness)

        if (points.size < 2) return

        // Simple joins delegate to basic implementation
        if (joins == PolylineJoins.SIMPLE) {
            drawSimplePolyline(points, effectiveBaseThickness, endCaps, closed)
            return
        }

        check(GfxRenderer.active) { "Must call GfxRenderer.begin() before polyline()" }

        val pose = GfxRenderer.pose
        val cameraPos = GfxRenderer.camera

        // Convert points to camera-relative with all attributes
        data class CameraPoint(
            val pos: Vec3,
            val color: Int,
            val thickness: Float,
        )

        val cameraPoints =
            points.map {
                CameraPoint(
                    it.position.subtract(cameraPos),
                    it.color,
                    effectiveBaseThickness * it.thickness,
                )
            }

        val mc = Minecraft.getInstance()
        val program = mc.shaderManager.getProgram(GfxShaders.POLYLINE)
        if (program != null) {
            RenderSystem.setShader(program)
        } else {
            val lineProgram = mc.shaderManager.getProgram(GfxShaders.LINE)
            if (lineProgram != null) {
                RenderSystem.setShader(lineProgram)
            }
        }

        val buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR)
        val matrix = pose.last()

        val n = cameraPoints.size
        val segmentCount = if (closed) n else n - 1

        // Each segment has TWO quads - one for positive side, one for negative side
        // At corners, we need different offsets for inner vs outer vertices
        for (index in 0 until segmentCount) {
            val nextIdx = (index + 1) % n

            val curr = cameraPoints[index]
            val next = cameraPoints[nextIdx]

            // Compute direction to camera for this segment (billboard mode)
            val segmentMidpoint = curr.pos.add(next.pos).scale(0.5)
            val toCamera =
                if (geometry == LineGeometry.BILLBOARD) {
                    val dir = segmentMidpoint.scale(-1.0)
                    if (dir.lengthSqr() < 0.0001) Vec3(0.0, 0.0, 1.0) else dir.normalize()
                } else {
                    Vec3(0.0, 1.0, 0.0)
                }

            // Tangent of this segment
            val tangent = next.pos.subtract(curr.pos).normalize()

            // Perpendicular for this segment
            val perp = calculatePerpendicular(tangent, toCamera, geometry)

            // Get neighbor info for miter calculations
            val prevIdx = (index - 1 + n) % n
            val followIdx = (index + 2) % n

            // Track whether we're at an endpoint (no real neighbor exists)
            val isFirstSegment = (index == 0 && !closed)
            val isLastSegment = (index == segmentCount - 1 && !closed)

            val prev =
                if (isFirstSegment) {
                    // Mirror: extrapolate backwards (used only to compute perpendicular direction)
                    CameraPoint(curr.pos.scale(2.0).subtract(next.pos), curr.color, curr.thickness)
                } else {
                    cameraPoints[prevIdx]
                }

            val following =
                if (isLastSegment) {
                    // Mirror: extrapolate forwards (used only to compute perpendicular direction)
                    CameraPoint(next.pos.scale(2.0).subtract(curr.pos), next.color, next.thickness)
                } else {
                    cameraPoints[followIdx]
                }

            // Calculate perpendiculars for adjacent segments (for miter at corners)
            val tangentPrev = curr.pos.subtract(prev.pos).normalize()
            val tangentNext = following.pos.subtract(next.pos).normalize()

            // For billboard, compute toCamera at each vertex
            val toCameraCurr =
                if (geometry == LineGeometry.BILLBOARD) {
                    val dir = curr.pos.scale(-1.0)
                    if (dir.lengthSqr() < 0.0001) Vec3(0.0, 0.0, 1.0) else dir.normalize()
                } else {
                    toCamera
                }
            val toCameraNext =
                if (geometry == LineGeometry.BILLBOARD) {
                    val dir = next.pos.scale(-1.0)
                    if (dir.lengthSqr() < 0.0001) Vec3(0.0, 0.0, 1.0) else dir.normalize()
                } else {
                    toCamera
                }

            // Perpendiculars at curr point (incoming and outgoing segments)
            val perpPrevAtCurr = calculatePerpendicular(tangentPrev, toCameraCurr, geometry)
            val perpCurrAtCurr = calculatePerpendicular(tangent, toCameraCurr, geometry)

            // Perpendiculars at next point (this segment and outgoing)
            val perpCurrAtNext = calculatePerpendicular(tangent, toCameraNext, geometry)
            val perpNextAtNext = calculatePerpendicular(tangentNext, toCameraNext, geometry)

            val halfThickCurr = curr.thickness.toDouble()
            val halfThickNext = next.thickness.toDouble()

            // Determine turn direction at each vertex
            val planeNormal = geometry.getPlaneNormal() ?: toCameraCurr
            val turnDirCurr = tangentPrev.cross(tangent).dot(planeNormal)
            val turnDirNext = tangent.cross(tangentNext).dot(planeNormal)

            // Compute miter offsets
            // At endpoints, use simple perpendicular (no corner to miter)
            // At curr: miter between prev segment and this segment
            val miterCurr =
                if (isFirstSegment) {
                    // First point of open polyline: no incoming segment, use simple perp
                    perpCurrAtCurr.scale(halfThickCurr)
                } else {
                    computeMiterOffset(
                        perpPrevAtCurr,
                        perpCurrAtCurr,
                        halfThickCurr,
                        effectiveMiterLimit.toDouble(),
                        joins,
                    )
                }

            // At next: miter between this segment and next segment
            val miterNext =
                if (isLastSegment) {
                    // Last point of open polyline: no outgoing segment, use simple perp
                    perpCurrAtNext.scale(halfThickNext)
                } else {
                    computeMiterOffset(
                        perpCurrAtNext,
                        perpNextAtNext,
                        halfThickNext,
                        effectiveMiterLimit.toDouble(),
                        joins,
                    )
                }

            // For round/bevel joins, we use different geometry:
            // - On the OUTER side of the turn: use simple perpendicular (miter handled by join mesh)
            // - On the INNER side of the turn: use miter offset
            // For miter joins, both sides use miter offset
            // At ENDPOINTS (first/last point of open polyline): always use simple perpendicular

            val useAsymmetricJoins = joins == PolylineJoins.ROUND || joins == PolylineJoins.BEVEL

            // Positive side offsets (perp direction)
            val posCurr: Vec3
            val posNext: Vec3
            // Negative side offsets (-perp direction)
            val negCurr: Vec3
            val negNext: Vec3

            // At first segment's start point: always use simple perpendicular (no corner)
            if (isFirstSegment) {
                posCurr = perpCurrAtCurr.scale(halfThickCurr)
                negCurr = perpCurrAtCurr.scale(-halfThickCurr)
            } else if (useAsymmetricJoins) {
                // For round/bevel joins: OUTER side uses simple perpendicular, INNER side uses miter
                if (turnDirCurr > 0.001) {
                    // Left turn (CCW): positive side is OUTER, negative side is INNER
                    posCurr = perpCurrAtCurr.scale(halfThickCurr) // outer: simple perp
                    negCurr = miterCurr.scale(-1.0) // inner: miter
                } else if (turnDirCurr < -0.001) {
                    // Right turn (CW): positive side is INNER, negative side is OUTER
                    posCurr = miterCurr // inner: miter
                    negCurr = perpCurrAtCurr.scale(-halfThickCurr) // outer: simple perp
                } else {
                    // Straight: use simple perp
                    posCurr = perpCurrAtCurr.scale(halfThickCurr)
                    negCurr = perpCurrAtCurr.scale(-halfThickCurr)
                }
            } else {
                // Miter joins: symmetric miters on both sides
                posCurr = miterCurr
                negCurr = miterCurr.scale(-1.0)
            }

            // At last segment's end point: always use simple perpendicular (no corner)
            if (isLastSegment) {
                posNext = perpCurrAtNext.scale(halfThickNext)
                negNext = perpCurrAtNext.scale(-halfThickNext)
            } else if (useAsymmetricJoins) {
                // For round/bevel joins: OUTER side uses simple perpendicular, INNER side uses miter
                if (turnDirNext > 0.001) {
                    // Left turn: positive side is OUTER, negative side is INNER
                    posNext = perpCurrAtNext.scale(halfThickNext) // outer: simple perp
                    negNext = miterNext.scale(-1.0) // inner: miter
                } else if (turnDirNext < -0.001) {
                    // Right turn: positive side is INNER, negative side is OUTER
                    posNext = miterNext // inner: miter
                    negNext = perpCurrAtNext.scale(-halfThickNext) // outer: simple perp
                } else {
                    // Straight: use simple perp
                    posNext = perpCurrAtNext.scale(halfThickNext)
                    negNext = perpCurrAtNext.scale(-halfThickNext)
                }
            } else {
                // Miter joins: symmetric miters on both sides
                posNext = miterNext
                negNext = miterNext.scale(-1.0)
            }

            // Build the quad vertices
            val v0 = curr.pos.add(posCurr) // curr +
            val v1 = curr.pos.add(negCurr) // curr -
            val v2 = next.pos.add(posNext) // next +
            val v3 = next.pos.add(negNext) // next -

            // UV.y encoding for SDF:
            // The shader expects UV.y = ±1 at the LINE EDGE (where alpha should fade to 0)
            // For a uniform-width line, all edge vertices are at the same visual "edge"
            // regardless of whether they're at a miter corner or a straight section.
            // So UV.y should always be ±1 at vertices, not the actual geometric distance.
            val uvYPosCurr = 1.0f
            val uvYNegCurr = -1.0f
            val uvYPosNext = 1.0f
            val uvYNegNext = -1.0f

            // Color extraction
            val (ar, rr, gr, br) = extractColorComponents(curr.color)
            val (ae, re, ge, be) = extractColorComponents(next.color)

            // Two triangles for the quad (v0, v1, v2, v3)
            // Quad layout:
            //   v0 (start,+) -------- v2 (end,+)
            //        |                    |
            //        |                    |
            //   v1 (start,-) -------- v3 (end,-)
            //
            // Triangle 1: v0, v1, v3 (bottom-left half)
            buffer
                .addVertex(matrix, v0.x.toFloat(), v0.y.toFloat(), v0.z.toFloat())
                .setUv(0f, uvYPosCurr)
                .setColor(rr, gr, br, ar)
            buffer
                .addVertex(matrix, v1.x.toFloat(), v1.y.toFloat(), v1.z.toFloat())
                .setUv(0f, uvYNegCurr)
                .setColor(rr, gr, br, ar)
            buffer
                .addVertex(matrix, v3.x.toFloat(), v3.y.toFloat(), v3.z.toFloat())
                .setUv(1f, uvYNegNext)
                .setColor(re, ge, be, ae)

            // Triangle 2: v0, v3, v2 (top-right half)
            buffer
                .addVertex(matrix, v0.x.toFloat(), v0.y.toFloat(), v0.z.toFloat())
                .setUv(0f, uvYPosCurr)
                .setColor(rr, gr, br, ar)
            buffer
                .addVertex(matrix, v3.x.toFloat(), v3.y.toFloat(), v3.z.toFloat())
                .setUv(1f, uvYNegNext)
                .setColor(re, ge, be, ae)
            buffer
                .addVertex(matrix, v2.x.toFloat(), v2.y.toFloat(), v2.z.toFloat())
                .setUv(1f, uvYPosNext)
                .setColor(re, ge, be, ae)
        }

        // Draw round/bevel joins at corners
        if (joins.hasJoinMesh()) {
            val joinCount = if (closed) n else n - 2
            for (i in 0 until joinCount) {
                val joinIdx = if (closed) i else i + 1
                val prevIdx = (joinIdx - 1 + n) % n
                val nextIdx = (joinIdx + 1) % n

                val prev = cameraPoints[prevIdx]
                val curr = cameraPoints[joinIdx]
                val next = cameraPoints[nextIdx]

                // Use full thickness as half-width (matches segment rendering)
                val halfThick = curr.thickness.toDouble()
                val (a, r, g, b) = extractColorComponents(curr.color)

                // Compute toCamera at join point
                val toCameraJoin =
                    if (geometry == LineGeometry.BILLBOARD) {
                        val dir = curr.pos.scale(-1.0)
                        if (dir.lengthSqr() < 0.0001) Vec3(0.0, 0.0, 1.0) else dir.normalize()
                    } else {
                        Vec3(0.0, 1.0, 0.0)
                    }

                when (joins) {
                    PolylineJoins.ROUND -> {
                        drawRoundJoin(buffer, matrix, prev.pos, curr.pos, next.pos, halfThick, r, g, b, a, geometry, toCameraJoin)
                    }
                    PolylineJoins.BEVEL -> {
                        drawBevelJoin(buffer, matrix, prev.pos, curr.pos, next.pos, halfThick, r, g, b, a, geometry, toCameraJoin)
                    }
                    else -> {}
                }
            }
        }

        // Draw end caps for open polylines
        if (!closed && endCaps != LineEndCap.NONE) {
            val firstPoint = cameraPoints.first()
            val secondPoint = cameraPoints[1]
            val lastPoint = cameraPoints.last()
            val secondLastPoint = cameraPoints[n - 2]

            val (af, rf, gf, bf) = extractColorComponents(firstPoint.color)
            val (al, rl, gl, bl) = extractColorComponents(lastPoint.color)

            // Compute toCamera at endpoints
            val toCameraFirst =
                if (geometry == LineGeometry.BILLBOARD) {
                    val dir = firstPoint.pos.scale(-1.0)
                    if (dir.lengthSqr() < 0.0001) Vec3(0.0, 0.0, 1.0) else dir.normalize()
                } else {
                    Vec3(0.0, 1.0, 0.0)
                }
            val toCameraLast =
                if (geometry == LineGeometry.BILLBOARD) {
                    val dir = lastPoint.pos.scale(-1.0)
                    if (dir.lengthSqr() < 0.0001) Vec3(0.0, 0.0, 1.0) else dir.normalize()
                } else {
                    Vec3(0.0, 1.0, 0.0)
                }

            // Use full thickness as half-width (matches segment rendering)
            val halfThickFirst = firstPoint.thickness.toDouble()
            val halfThickLast = lastPoint.thickness.toDouble()

            when (endCaps) {
                LineEndCap.ROUND -> {
                    drawRoundCap(
                        buffer,
                        matrix,
                        firstPoint.pos,
                        secondPoint.pos,
                        halfThickFirst,
                        rf,
                        gf,
                        bf,
                        af,
                        isStart = true,
                        geometry = geometry,
                        toCamera = toCameraFirst,
                    )
                    drawRoundCap(
                        buffer,
                        matrix,
                        lastPoint.pos,
                        secondLastPoint.pos,
                        halfThickLast,
                        rl,
                        gl,
                        bl,
                        al,
                        isStart = false,
                        geometry = geometry,
                        toCamera = toCameraLast,
                    )
                }
                LineEndCap.SQUARE -> {
                    drawSquareCap(
                        buffer,
                        matrix,
                        firstPoint.pos,
                        secondPoint.pos,
                        halfThickFirst,
                        rf,
                        gf,
                        bf,
                        af,
                        isStart = true,
                        geometry = geometry,
                        toCamera = toCameraFirst,
                    )
                    drawSquareCap(
                        buffer,
                        matrix,
                        lastPoint.pos,
                        secondLastPoint.pos,
                        halfThickLast,
                        rl,
                        gl,
                        bl,
                        al,
                        isStart = false,
                        geometry = geometry,
                        toCamera = toCameraLast,
                    )
                }
                else -> {}
            }
        }

        val mesh = buffer.build()
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh)
        }
    }

    private fun extractColorComponents(color: Int): FloatArray =
        floatArrayOf(
            GfxRenderer.alpha(color),
            GfxRenderer.red(color),
            GfxRenderer.green(color),
            GfxRenderer.blue(color),
        )

    private fun drawSimplePolyline(
        points: List<PolylinePoint>,
        baseThickness: Float,
        endCaps: LineEndCap,
        closed: Boolean,
    ) {
        for (i in 0 until points.size - 1) {
            val curr = points[i]
            val next = points[i + 1]
            val thickness = baseThickness * (curr.thickness + next.thickness) / 2
            GfxLines.line(curr.position, next.position, curr.color, thickness)
        }
        if (closed && points.size >= 3) {
            val first = points.first()
            val last = points.last()
            val thickness = baseThickness * (first.thickness + last.thickness) / 2
            GfxLines.line(last.position, first.position, last.color, thickness)
        }
    }

    // ========================================================================
    // Perpendicular Calculation
    // ========================================================================

    /**
     * Calculate the perpendicular direction for a line segment based on geometry mode.
     */
    private fun calculatePerpendicular(
        tangent: Vec3,
        toCamera: Vec3,
        geometry: LineGeometry,
    ): Vec3 {
        val planeNormal = geometry.getPlaneNormal()

        return if (planeNormal != null) {
            // Flat mode: perpendicular is tangent × planeNormal
            val perp = tangent.cross(planeNormal)
            if (perp.lengthSqr() < 0.0001) {
                // Tangent is parallel to plane normal - use fallback
                when (geometry) {
                    LineGeometry.FLAT_XY -> Vec3(1.0, 0.0, 0.0)
                    LineGeometry.FLAT_XZ -> Vec3(1.0, 0.0, 0.0)
                    LineGeometry.FLAT_YZ -> Vec3(0.0, 1.0, 0.0)
                    LineGeometry.BILLBOARD -> Vec3(0.0, 1.0, 0.0)
                }
            } else {
                perp.normalize()
            }
        } else {
            // Billboard mode: perpendicular is tangent × toCamera
            val perp = tangent.cross(toCamera)
            if (perp.lengthSqr() < 0.0001) {
                // Tangent is parallel to camera direction - use fallback
                val altAxis = if (abs(tangent.y) < 0.9) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
                tangent.cross(altAxis).normalize()
            } else {
                perp.normalize()
            }
        }
    }

    // ========================================================================
    // Miter Calculation
    // ========================================================================

    /**
     * Compute the miter offset vector at a corner.
     *
     * The miter direction is the sum of the two perpendiculars (normalized),
     * and the length is adjusted so that the miter reaches the proper distance
     * from the line.
     */
    private fun computeMiterOffset(
        perpPrev: Vec3,
        perpNext: Vec3,
        halfThickness: Double,
        miterLimit: Double,
        joins: PolylineJoins,
    ): Vec3 {
        val dotVal = perpPrev.dot(perpNext)

        // Handle 180° turn (perpendiculars point opposite directions)
        if (dotVal < -0.99) {
            return perpNext.scale(halfThickness)
        }

        // Miter direction is sum of perpendiculars, normalized
        val miterDir = perpPrev.add(perpNext)
        if (miterDir.lengthSqr() < 0.0001) {
            return perpNext.scale(halfThickness)
        }
        val miterNormalized = miterDir.normalize()

        // Miter length: halfThickness / dot(miterDir, perpNext)
        // This ensures the miter extends far enough to meet the line edges
        val cosHalfAngle = miterNormalized.dot(perpNext)
        val miterLength =
            if (cosHalfAngle > 0.0001) {
                val unclamped = halfThickness / cosHalfAngle
                when (joins) {
                    PolylineJoins.BEVEL -> min(unclamped, halfThickness * 1.0)
                    PolylineJoins.MITER -> min(unclamped, halfThickness * miterLimit)
                    else -> unclamped
                }
            } else {
                halfThickness * miterLimit
            }

        return miterNormalized.scale(miterLength)
    }

    // ========================================================================
    // Join Drawing
    // ========================================================================

    private fun drawRoundJoin(
        buffer: BufferBuilder,
        matrix: PoseStack.Pose,
        prev: Vec3,
        curr: Vec3,
        next: Vec3,
        halfThickness: Double,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        geometry: LineGeometry,
        toCamera: Vec3,
    ) {
        val tangentIn = curr.subtract(prev).normalize()
        val tangentOut = next.subtract(curr).normalize()

        val perpIn = calculatePerpendicular(tangentIn, toCamera, geometry)
        val perpOut = calculatePerpendicular(tangentOut, toCamera, geometry)

        // Determine turn direction
        val planeNormal = geometry.getPlaneNormal() ?: toCamera
        val turnDir = tangentIn.cross(tangentOut).dot(planeNormal)

        // Skip nearly straight segments
        if (abs(turnDir) < 0.001) return

        // Round joins fill the wedge at the outer corner gap
        val flip = if (turnDir > 0) 1.0 else -1.0

        // Outer edge directions
        val startPerp = perpIn.scale(flip)
        val endPerp = perpOut.scale(flip)

        // Bisector direction pointing toward inner corner
        // The miter length is halfThickness / cos(angle/2) where angle is between perpendiculars
        val bisector = perpIn.add(perpOut)
        val bisectorLen = bisector.length()
        // cos(angle/2) = |bisector| / 2 (since each perp is unit length)
        // So miter length = halfThickness / (bisectorLen / 2) = 2 * halfThickness / bisectorLen
        val miterLength = if (bisectorLen > 0.001) 2.0 * halfThickness / bisectorLen else halfThickness
        val innerDir = bisector.normalize().scale(-flip)

        // Calculate angle between perpendiculars
        val dot = startPerp.dot(endPerp).coerceIn(-1.0, 1.0)
        val angle = kotlin.math.acos(dot)

        // Skip tiny angles
        if (angle < 0.01) return

        val segments = maxOf(3, (angle / (PI / ROUND_SEGMENTS)).toInt())

        // Build a 2D coordinate system for UV encoding
        val basisU = startPerp.normalize()
        val basisV = planeNormal.cross(basisU).normalize()

        // Expand geometry by aaPadding for smooth AA at edges
        val aaPadding = GfxRenderer.aaPadding
        val expandedThickness = halfThickness * aaPadding

        // v0 pushed toward inner corner using miter length
        val v0 = curr.add(innerDir.scale(miterLength))

        for (i in 0 until segments) {
            val t1 = i.toFloat() / segments
            val t2 = (i + 1).toFloat() / segments

            val p1 = slerpVec3(startPerp, endPerp, t1).normalize()
            val p2 = slerpVec3(startPerp, endPerp, t2).normalize()

            // World positions - use expanded thickness for geometry
            val v1 = curr.add(p1.scale(expandedThickness))
            val v2 = curr.add(p2.scale(expandedThickness))

            // UV coordinates: project onto 2D basis, scaled by aaPadding
            // Edge vertices at length = aaPadding, visible edge (SDF=1) is at length=1
            // Add 2.0 offset to UV.x to signal round geometry to the shader
            val uv1x = (p1.dot(basisU) * aaPadding).toFloat() + ROUND_GEOMETRY_UV_OFFSET
            val uv1y = (p1.dot(basisV) * aaPadding).toFloat()
            val uv2x = (p2.dot(basisU) * aaPadding).toFloat() + ROUND_GEOMETRY_UV_OFFSET
            val uv2y = (p2.dot(basisV) * aaPadding).toFloat()

            buffer
                .addVertex(matrix, v0.x.toFloat(), v0.y.toFloat(), v0.z.toFloat())
                .setUv(ROUND_GEOMETRY_UV_OFFSET, 0f) // Center vertex
                .setColor(r, g, b, a)
            buffer
                .addVertex(matrix, v1.x.toFloat(), v1.y.toFloat(), v1.z.toFloat())
                .setUv(uv1x, uv1y)
                .setColor(r, g, b, a)
            buffer
                .addVertex(matrix, v2.x.toFloat(), v2.y.toFloat(), v2.z.toFloat())
                .setUv(uv2x, uv2y)
                .setColor(r, g, b, a)
        }
    }

    private fun drawBevelJoin(
        buffer: BufferBuilder,
        matrix: PoseStack.Pose,
        prev: Vec3,
        curr: Vec3,
        next: Vec3,
        halfThickness: Double,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        geometry: LineGeometry,
        toCamera: Vec3,
    ) {
        val tangentIn = curr.subtract(prev).normalize()
        val tangentOut = next.subtract(curr).normalize()

        val perpIn = calculatePerpendicular(tangentIn, toCamera, geometry)
        val perpOut = calculatePerpendicular(tangentOut, toCamera, geometry)

        // Determine turn direction
        val planeNormal = geometry.getPlaneNormal() ?: toCamera
        val turnDir = tangentIn.cross(tangentOut).dot(planeNormal)

        // Skip nearly straight segments
        if (abs(turnDir) < 0.001) return

        // Bevel joins create a single triangle filling the outer corner gap
        val flip = if (turnDir > 0) 1.0 else -1.0

        // Bisector direction pointing toward inner corner
        val innerDir = perpIn.add(perpOut).normalize().scale(-flip)

        // Outer edge directions
        val p1 = perpIn.scale(flip).normalize()
        val p2 = perpOut.scale(flip).normalize()

        // v0 at inner corner
        val v0 = curr.add(innerDir.scale(halfThickness))

        // Expand geometry by aaPadding for smooth AA at outer edge
        val aaPadding = GfxRenderer.aaPadding

        // Expand v1 and v2 outward by aaPadding
        val v1Expanded = curr.add(p1.scale(halfThickness * aaPadding))
        val v2Expanded = curr.add(p2.scale(halfThickness * aaPadding))

        // UV encoding for bevel triangle:
        // - v0 (inner corner): UV.y = 0 (inside, far from outer edge)
        // - v1, v2 (outer edge): UV.y = aaPadding (at expanded outer edge, visible edge at y=1)
        // Use BEVEL_GEOMETRY_UV_OFFSET to signal bevel geometry type to shader
        buffer
            .addVertex(matrix, v0.x.toFloat(), v0.y.toFloat(), v0.z.toFloat())
            .setUv(BEVEL_GEOMETRY_UV_OFFSET, 0f) // Inner corner - y=0
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, v1Expanded.x.toFloat(), v1Expanded.y.toFloat(), v1Expanded.z.toFloat())
            .setUv(BEVEL_GEOMETRY_UV_OFFSET, aaPadding) // Outer edge - y=aaPadding
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, v2Expanded.x.toFloat(), v2Expanded.y.toFloat(), v2Expanded.z.toFloat())
            .setUv(BEVEL_GEOMETRY_UV_OFFSET, aaPadding) // Outer edge - y=aaPadding
            .setColor(r, g, b, a)
    }

    // ========================================================================
    // End Cap Drawing
    // ========================================================================

    private fun drawRoundCap(
        buffer: BufferBuilder,
        matrix: PoseStack.Pose,
        endpoint: Vec3,
        neighbor: Vec3,
        halfThickness: Double,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        isStart: Boolean,
        geometry: LineGeometry,
        toCamera: Vec3,
    ) {
        // For start cap: line goes endpoint -> neighbor, cap extends backward (away from neighbor)
        // For end cap: line goes neighbor -> endpoint, cap extends forward (away from neighbor)
        // In both cases, capDir should point AWAY from the neighbor (the interior of the line)
        val capDir = endpoint.subtract(neighbor).normalize()

        val perp = calculatePerpendicular(capDir, toCamera, geometry)

        // Draw semicircle fan from +perp, through capDir (tip), to -perp
        // Angle 0 = +perp direction, angle π/2 = capDir direction, angle π = -perp direction
        val segments = ROUND_SEGMENTS

        // Expand geometry by aaPadding for smooth AA at edges
        val aaPadding = GfxRenderer.aaPadding
        val expandedThickness = halfThickness * aaPadding

        for (i in 0 until segments) {
            val angle1 = (i.toFloat() / segments) * PI
            val angle2 = ((i + 1).toFloat() / segments) * PI

            // At angle=0: cos=1, sin=0 -> p = +perp (connects to line edge)
            // At angle=π/2: cos=0, sin=1 -> p = +capDir (tip of semicircle)
            // At angle=π: cos=-1, sin=0 -> p = -perp (connects to other line edge)
            // Use expandedThickness for geometry, but UV stays at unit circle (edge at length=1)
            val p1 = perp.scale(cos(angle1) * expandedThickness).add(capDir.scale(sin(angle1) * expandedThickness))
            val p2 = perp.scale(cos(angle2) * expandedThickness).add(capDir.scale(sin(angle2) * expandedThickness))

            val v0 = endpoint
            val v1 = endpoint.add(p1)
            val v2 = endpoint.add(p2)

            // UV encoding: unit circle coordinates scaled by aaPadding
            // Edge vertices at length = aaPadding, visible edge (SDF=1) is at length=1
            // Add 2.0 offset to UV.x to signal round geometry to the shader
            val uv1x = (cos(angle1) * aaPadding).toFloat()
            val uv1y = (sin(angle1) * aaPadding).toFloat()
            val uv2x = (cos(angle2) * aaPadding).toFloat()
            val uv2y = (sin(angle2) * aaPadding).toFloat()

            buffer
                .addVertex(matrix, v0.x.toFloat(), v0.y.toFloat(), v0.z.toFloat())
                .setUv(ROUND_GEOMETRY_UV_OFFSET, 0f) // Center vertex
                .setColor(r, g, b, a)
            buffer
                .addVertex(matrix, v1.x.toFloat(), v1.y.toFloat(), v1.z.toFloat())
                .setUv(uv1x + ROUND_GEOMETRY_UV_OFFSET, uv1y)
                .setColor(r, g, b, a)
            buffer
                .addVertex(matrix, v2.x.toFloat(), v2.y.toFloat(), v2.z.toFloat())
                .setUv(uv2x + ROUND_GEOMETRY_UV_OFFSET, uv2y)
                .setColor(r, g, b, a)
        }
    }

    private fun drawSquareCap(
        buffer: BufferBuilder,
        matrix: PoseStack.Pose,
        endpoint: Vec3,
        neighbor: Vec3,
        halfThickness: Double,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        isStart: Boolean,
        geometry: LineGeometry,
        toCamera: Vec3,
    ) {
        // Cap direction always points AWAY from the neighbor (the interior of the line)
        val capDirNorm = endpoint.subtract(neighbor).normalize()
        val capDir = capDirNorm.scale(halfThickness) // Extension direction

        val perp = calculatePerpendicular(capDirNorm, toCamera, geometry).scale(halfThickness)

        // Quad extending from endpoint
        val v0 = endpoint.subtract(perp)
        val v1 = endpoint.add(perp)
        val v2 = endpoint.add(perp).add(capDir)
        val v3 = endpoint.subtract(perp).add(capDir)

        // Two triangles
        buffer
            .addVertex(matrix, v0.x.toFloat(), v0.y.toFloat(), v0.z.toFloat())
            .setUv(0f, -1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, v1.x.toFloat(), v1.y.toFloat(), v1.z.toFloat())
            .setUv(0f, 1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, v2.x.toFloat(), v2.y.toFloat(), v2.z.toFloat())
            .setUv(1f, 1f)
            .setColor(r, g, b, a)

        buffer
            .addVertex(matrix, v0.x.toFloat(), v0.y.toFloat(), v0.z.toFloat())
            .setUv(0f, -1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, v2.x.toFloat(), v2.y.toFloat(), v2.z.toFloat())
            .setUv(1f, 1f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, v3.x.toFloat(), v3.y.toFloat(), v3.z.toFloat())
            .setUv(1f, -1f)
            .setColor(r, g, b, a)
    }

    // ========================================================================
    // Utility Functions
    // ========================================================================

    private fun slerpVec3(
        a: Vec3,
        b: Vec3,
        t: Float,
    ): Vec3 {
        val dot = a.dot(b).coerceIn(-1.0, 1.0)
        val theta = kotlin.math.acos(dot)

        if (theta < 0.001) {
            // Nearly parallel - use linear interpolation
            val oneMinusT = 1f - t
            return Vec3(
                a.x * oneMinusT + b.x * t,
                a.y * oneMinusT + b.y * t,
                a.z * oneMinusT + b.z * t,
            ).normalize()
        }

        val sinTheta = sin(theta)
        val wa = sin((1 - t) * theta) / sinTheta
        val wb = sin(t * theta) / sinTheta

        return Vec3(
            a.x * wa + b.x * wb,
            a.y * wa + b.y * wb,
            a.z * wa + b.z * wb,
        )
    }
}
