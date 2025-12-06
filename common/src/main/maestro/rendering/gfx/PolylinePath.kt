package maestro.rendering.gfx

import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A builder for constructing polyline paths with support for:
 * - Manual point addition
 * - Bezier curves (cubic)
 * - Arc segments
 *
 * @param defaultPointsPerTurn Default point density for curves, in points per full 360° turn
 */
class PolylinePath(
    private val defaultPointsPerTurn: Float = 32f,
) {
    private val _points = mutableListOf<PolylinePoint>()

    /** Read-only access to the points in this path */
    val points: List<PolylinePoint> get() = _points

    /** Number of points in the path */
    val count: Int get() = _points.size

    /** Whether the path has any points */
    val isEmpty: Boolean get() = _points.isEmpty()

    /** Whether the path has enough points to draw (at least 2) */
    val isDrawable: Boolean get() = _points.size >= 2

    /** The last point in the path, or null if empty */
    val lastPoint: PolylinePoint? get() = _points.lastOrNull()

    /** The first point in the path, or null if empty */
    val firstPoint: PolylinePoint? get() = _points.firstOrNull()

    /** Clear all points from the path */
    fun clear() {
        _points.clear()
    }

    // region Point Adding

    /** Add a point at the given position */
    fun addPoint(position: Vec3): PolylinePath {
        _points.add(PolylinePoint(position))
        return this
    }

    /** Add a point at the given coordinates */
    fun addPoint(
        x: Double,
        y: Double,
        z: Double,
    ): PolylinePath {
        _points.add(PolylinePoint(Vec3(x, y, z)))
        return this
    }

    /** Add a point with position and color */
    fun addPoint(
        position: Vec3,
        color: Int,
    ): PolylinePath {
        _points.add(PolylinePoint(position, color))
        return this
    }

    /** Add a point with position, color, and thickness */
    fun addPoint(
        position: Vec3,
        color: Int,
        thickness: Float,
    ): PolylinePath {
        _points.add(PolylinePoint(position, color, thickness))
        return this
    }

    /** Add a pre-constructed polyline point */
    fun addPoint(point: PolylinePoint): PolylinePath {
        _points.add(point)
        return this
    }

    /** Add multiple points */
    fun addPoints(vararg positions: Vec3): PolylinePath {
        positions.forEach { _points.add(PolylinePoint(it)) }
        return this
    }

    /** Add multiple points with the same color */
    fun addPoints(
        positions: List<Vec3>,
        color: Int = 0xFFFFFFFF.toInt(),
    ): PolylinePath {
        positions.forEach { _points.add(PolylinePoint(it, color)) }
        return this
    }

    /** Add multiple pre-constructed points */
    fun addPoints(points: List<PolylinePoint>): PolylinePath {
        _points.addAll(points)
        return this
    }

    /** Set a point at the given index */
    operator fun set(
        index: Int,
        point: PolylinePoint,
    ) {
        _points[index] = point
    }

    /** Get a point at the given index */
    operator fun get(index: Int): PolylinePoint = _points[index]

    /** Set the position of a point at the given index */
    fun setPointPosition(
        index: Int,
        position: Vec3,
    ) {
        _points[index] = _points[index].withPosition(position)
    }

    /** Set the color of a point at the given index */
    fun setPointColor(
        index: Int,
        color: Int,
    ) {
        _points[index] = _points[index].withColor(color)
    }

    /** Set the thickness of a point at the given index */
    fun setPointThickness(
        index: Int,
        thickness: Float,
    ) {
        _points[index] = _points[index].withThickness(thickness)
    }

    // endregion

    // region Bezier Curves

    /**
     * Add a cubic Bezier curve from the current last point.
     *
     * Uses the previous point as the starting point. The curve passes through
     * the start tangent direction, curves toward the end tangent, and arrives at the end point.
     *
     * @param startTangent First control point (influences curve leaving the start)
     * @param endTangent Second control point (influences curve arriving at end)
     * @param end End position of the curve
     * @param pointsPerTurn Point density (points per full 360° turn)
     */
    fun bezierTo(
        startTangent: Vec3,
        endTangent: Vec3,
        end: Vec3,
        pointsPerTurn: Float = defaultPointsPerTurn,
    ): PolylinePath {
        val last = lastPoint ?: return this
        val pointCount = calculateBezierPointCount(last.position, startTangent, endTangent, end, pointsPerTurn)
        return bezierTo(startTangent, endTangent, end, pointCount)
    }

    /**
     * Add a cubic Bezier curve with explicit point count.
     */
    fun bezierTo(
        startTangent: Vec3,
        endTangent: Vec3,
        end: Vec3,
        pointCount: Int,
    ): PolylinePath {
        val last = lastPoint ?: return this
        val endPoint = last.withPosition(end)

        // Create control points by lerping between start and end for color/thickness
        val ppB = PolylinePoint.lerp(last, endPoint, 1f / 3f).withPosition(startTangent)
        val ppC = PolylinePoint.lerp(last, endPoint, 2f / 3f).withPosition(endTangent)

        return bezierTo(ppB, ppC, endPoint, pointCount)
    }

    /**
     * Add a cubic Bezier curve with full PolylinePoint control points.
     * Color and thickness blend across the curve according to control point values.
     */
    fun bezierTo(
        startTangent: PolylinePoint,
        endTangent: PolylinePoint,
        end: PolylinePoint,
        pointsPerTurn: Float = defaultPointsPerTurn,
    ): PolylinePath {
        val last = lastPoint ?: return this
        val pointCount =
            calculateBezierPointCount(
                last.position,
                startTangent.position,
                endTangent.position,
                end.position,
                pointsPerTurn,
            )
        return bezierTo(startTangent, endTangent, end, pointCount)
    }

    /**
     * Add a cubic Bezier curve with full PolylinePoint control points and explicit point count.
     */
    fun bezierTo(
        startTangent: PolylinePoint,
        endTangent: PolylinePoint,
        end: PolylinePoint,
        pointCount: Int,
    ): PolylinePath {
        val last = lastPoint ?: return this

        // Generate points along the curve (skip first since it's the last point)
        val count = max(2, pointCount)
        for (i in 1 until count) {
            val t = i.toFloat() / (count - 1f)
            _points.add(PolylinePoint.cubicBezier(last, startTangent, endTangent, end, t))
        }

        return this
    }

    private fun calculateBezierPointCount(
        a: Vec3,
        b: Vec3,
        c: Vec3,
        d: Vec3,
        pointsPerTurn: Float,
    ): Int {
        // Estimate curve length via angular sum
        val sampleCount = 11 // 5 * 2 + 1
        val curveSumDeg = getApproximateAngularCurveSumDegrees(a, b, c, d, sampleCount)
        val angSpanTurns = curveSumDeg / 360f
        return max(2, (angSpanTurns * pointsPerTurn).roundToInt())
    }

    private fun getApproximateAngularCurveSumDegrees(
        a: Vec3,
        b: Vec3,
        c: Vec3,
        d: Vec3,
        vertCount: Int,
    ): Float {
        var angSum = 0f

        // t = 0: tangent is direction from a to b (derivative / 3)
        var tangentPrev = b.subtract(a)

        // Intermediate tangents
        for (i in 1 until vertCount - 1) {
            val t = i.toFloat() / (vertCount - 1f)
            val tangent = cubicBezierDerivativeIsh(a, b, c, d, t)
            angSum += angleBetween(tangentPrev, tangent)
            tangentPrev = tangent
        }

        // t = 1: tangent is direction from c to d
        val finalTangent = d.subtract(c)
        angSum += angleBetween(tangentPrev, finalTangent)

        return angSum
    }

    private fun cubicBezierDerivativeIsh(
        a: Vec3,
        b: Vec3,
        c: Vec3,
        d: Vec3,
        t: Float,
    ): Vec3 {
        val omt = 1f - t
        val t2 = t * t
        val threeTSquared = 3 * t2
        val sa = -omt * omt
        val sb = threeTSquared - 4 * t + 1
        val sc = 2 * t - threeTSquared
        val sd = t2

        return Vec3(
            a.x * sa + b.x * sb + c.x * sc + d.x * sd,
            a.y * sa + b.y * sb + c.y * sc + d.y * sd,
            a.z * sa + b.z * sb + c.z * sc + d.z * sd,
        )
    }

    private fun angleBetween(
        a: Vec3,
        b: Vec3,
    ): Float {
        val lenA = a.length()
        val lenB = b.length()
        if (lenA < 0.0001 || lenB < 0.0001) return 0f

        val dot = a.dot(b) / (lenA * lenB)
        val clamped = dot.coerceIn(-1.0, 1.0)
        return Math.toDegrees(kotlin.math.acos(clamped)).toFloat()
    }

    // endregion

    // region Arc Curves

    /**
     * Add an arc wedged into the corner defined by the previous point, corner, and next point.
     *
     * @param corner The corner position (where the arc is centered)
     * @param next The next position after the arc
     * @param radius The radius of the arc
     * @param pointsPerTurn Point density
     */
    fun arcTo(
        corner: Vec3,
        next: Vec3,
        radius: Float,
        pointsPerTurn: Float = defaultPointsPerTurn,
    ): PolylinePath {
        val last = lastPoint ?: return this
        val nextPoint = last.withPosition(next)
        return arcToInternal(corner, nextPoint, radius, true, 0, pointsPerTurn)
    }

    /**
     * Add an arc with explicit point count.
     */
    fun arcTo(
        corner: Vec3,
        next: Vec3,
        radius: Float,
        pointCount: Int,
    ): PolylinePath {
        val last = lastPoint ?: return this
        val nextPoint = last.withPosition(next)
        return arcToInternal(corner, nextPoint, radius, false, pointCount, 0f)
    }

    /**
     * Add an arc with color/thickness blending to the endpoint.
     */
    fun arcTo(
        corner: Vec3,
        next: PolylinePoint,
        radius: Float,
        pointsPerTurn: Float = defaultPointsPerTurn,
    ): PolylinePath = arcToInternal(corner, next, radius, true, 0, pointsPerTurn)

    private fun arcToInternal(
        corner: Vec3,
        next: PolylinePoint,
        radius: Float,
        useDensity: Boolean,
        targetPointCount: Int,
        pointsPerTurn: Float,
    ): PolylinePath {
        val prev = lastPoint ?: return this

        val tangentA = prev.position.subtract(corner).normalize()
        val tangentB = next.position.subtract(corner).normalize()
        val cross = tangentA.cross(tangentB)

        // Check if it's basically a straight line
        if (abs(cross.x) + abs(cross.y) + abs(cross.z) <= 0.001) {
            // Straight line case - add two points for sharp color discontinuity
            val tCenter = getLineSegmentProjectionT(prev.position, next.position, corner)
            val tA = (tCenter - 0.0001f).coerceIn(0f, 1f)
            val tB = (tCenter + 0.0001f).coerceIn(0f, 1f)

            val ppA = prev.withPosition(lerpVec3(prev.position, next.position, tA))
            val ppB = next.withPosition(lerpVec3(prev.position, next.position, tB))

            _points.add(ppA)
            _points.add(ppB)
            return this
        }

        val axis = cross.normalize()
        val normalPrev = axis.cross(tangentA).normalize()
        val normalNext = axis.cross(tangentB).normalize()
        val cornerDir = normalPrev.add(normalNext).normalize()
        val cornerBDot = cornerDir.dot(normalNext)
        val safeRadius = max(radius, 0.0001f)
        val center = corner.add(cornerDir.scale(safeRadius / cornerBDot))

        // Calculate point count
        val count =
            if (useDensity) {
                val angTurn = angleBetween(normalPrev, normalNext) / 360f
                max(2, (angTurn * pointsPerTurn).roundToInt())
            } else {
                max(2, targetPointCount)
            }

        // Generate arc points
        val negNormPrev = normalPrev.scale(-1.0)
        val negNormNext = normalNext.scale(-1.0)

        for (i in 0 until count) {
            val t = i.toFloat() / (count - 1f)
            val dir = slerpVec3(negNormPrev, negNormNext, t)
            val position = center.add(dir.scale(safeRadius.toDouble()))
            val point = PolylinePoint.lerp(prev, next, t).withPosition(position)
            _points.add(point)
        }

        return this
    }

    private fun getLineSegmentProjectionT(
        a: Vec3,
        b: Vec3,
        p: Vec3,
    ): Float {
        val disp = b.subtract(a)
        val dot1 = p.subtract(a).dot(disp)
        val dot2 = disp.dot(disp)
        return if (dot2 > 0.0001) (dot1 / dot2).toFloat() else 0f
    }

    private fun lerpVec3(
        a: Vec3,
        b: Vec3,
        t: Float,
    ): Vec3 {
        val oneMinusT = 1f - t
        return Vec3(
            a.x * oneMinusT + b.x * t,
            a.y * oneMinusT + b.y * t,
            a.z * oneMinusT + b.z * t,
        )
    }

    private fun slerpVec3(
        a: Vec3,
        b: Vec3,
        t: Float,
    ): Vec3 {
        // Spherical linear interpolation for directions
        val dot = a.dot(b).coerceIn(-1.0, 1.0)
        val theta = kotlin.math.acos(dot)

        if (theta < 0.001) {
            return lerpVec3(a, b, t).normalize()
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

    // endregion

    // region Circle & Regular Polygon Helpers

    /**
     * Add points forming a circle.
     *
     * @param center Center of the circle
     * @param radius Radius of the circle
     * @param normal Normal vector of the circle plane (default: Y-up)
     * @param segments Number of segments
     * @param color Color for all points
     */
    fun addCircle(
        center: Vec3,
        radius: Double,
        normal: Vec3 = Vec3(0.0, 1.0, 0.0),
        segments: Int = 32,
        color: Int = 0xFFFFFFFF.toInt(),
    ): PolylinePath {
        // Calculate basis vectors for the circle plane
        val up = if (abs(normal.y) > 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
        val right = normal.cross(up).normalize()
        val forward = right.cross(normal).normalize()

        for (i in 0 until segments) {
            val angle = (i.toDouble() / segments) * 2.0 * PI
            val x = cos(angle) * radius
            val z = sin(angle) * radius
            val pos = center.add(right.scale(x)).add(forward.scale(z))
            _points.add(PolylinePoint(pos, color))
        }

        return this
    }

    /**
     * Add points forming a regular polygon.
     *
     * @param center Center of the polygon
     * @param radius Distance from center to vertices
     * @param sides Number of sides
     * @param normal Normal vector of the polygon plane
     * @param startAngle Starting angle in radians (default: 0)
     * @param color Color for all points
     */
    fun addRegularPolygon(
        center: Vec3,
        radius: Double,
        sides: Int,
        normal: Vec3 = Vec3(0.0, 1.0, 0.0),
        startAngle: Double = 0.0,
        color: Int = 0xFFFFFFFF.toInt(),
    ): PolylinePath {
        val up = if (abs(normal.y) > 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
        val right = normal.cross(up).normalize()
        val forward = right.cross(normal).normalize()

        for (i in 0 until sides) {
            val angle = startAngle + (i.toDouble() / sides) * 2.0 * PI
            val x = cos(angle) * radius
            val z = sin(angle) * radius
            val pos = center.add(right.scale(x)).add(forward.scale(z))
            _points.add(PolylinePoint(pos, color))
        }

        return this
    }

    // endregion

    companion object {
        /**
         * Create a path from a list of positions.
         */
        fun fromPositions(
            positions: List<Vec3>,
            color: Int = 0xFFFFFFFF.toInt(),
        ): PolylinePath = PolylinePath().addPoints(positions, color)

        /**
         * Create a path from a list of polyline points.
         */
        fun fromPoints(points: List<PolylinePoint>): PolylinePath = PolylinePath().addPoints(points)

        /**
         * Create a circular path.
         */
        fun circle(
            center: Vec3,
            radius: Double,
            normal: Vec3 = Vec3(0.0, 1.0, 0.0),
            segments: Int = 32,
            color: Int = 0xFFFFFFFF.toInt(),
        ): PolylinePath = PolylinePath().addCircle(center, radius, normal, segments, color)

        /**
         * Create a regular polygon path.
         */
        fun regularPolygon(
            center: Vec3,
            radius: Double,
            sides: Int,
            normal: Vec3 = Vec3(0.0, 1.0, 0.0),
            startAngle: Double = 0.0,
            color: Int = 0xFFFFFFFF.toInt(),
        ): PolylinePath = PolylinePath().addRegularPolygon(center, radius, sides, normal, startAngle, color)

        /**
         * Create a rectangle path.
         */
        fun rectangle(
            center: Vec3,
            width: Double,
            height: Double,
            normal: Vec3 = Vec3(0.0, 1.0, 0.0),
            color: Int = 0xFFFFFFFF.toInt(),
        ): PolylinePath {
            val up = if (abs(normal.y) > 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
            val right = normal.cross(up).normalize()
            val forward = right.cross(normal).normalize()

            val hw = width / 2.0
            val hh = height / 2.0

            return PolylinePath()
                .addPoint(center.add(right.scale(-hw)).add(forward.scale(-hh)), color)
                .addPoint(center.add(right.scale(hw)).add(forward.scale(-hh)), color)
                .addPoint(center.add(right.scale(hw)).add(forward.scale(hh)), color)
                .addPoint(center.add(right.scale(-hw)).add(forward.scale(hh)), color)
        }
    }
}
