package maestro.rendering.gfx

import net.minecraft.world.phys.Vec3

/**
 * A point in a polyline with position, color, and thickness information.
 *
 * Each point in a polyline can have:
 * - Its own position
 * - Its own color (for per-vertex coloring)
 * - Its own thickness multiplier (for varying line width)
 *
 * @property position World-space position of the point
 * @property color ARGB color at this point (default: white with full opacity)
 * @property thickness Thickness multiplier for this point (default: 1.0, multiplied with global thickness)
 */
data class PolylinePoint(
    val position: Vec3,
    val color: Int = 0xFFFFFFFF.toInt(),
    val thickness: Float = 1f,
) {
    constructor(x: Double, y: Double, z: Double) : this(Vec3(x, y, z))
    constructor(x: Double, y: Double, z: Double, color: Int) : this(Vec3(x, y, z), color)
    constructor(x: Double, y: Double, z: Double, color: Int, thickness: Float) : this(Vec3(x, y, z), color, thickness)

    companion object {
        /**
         * Linearly interpolate between two polyline points.
         *
         * @param a Start point
         * @param b End point
         * @param t Interpolation factor (0 = a, 1 = b)
         */
        fun lerp(
            a: PolylinePoint,
            b: PolylinePoint,
            t: Float,
        ): PolylinePoint {
            val clampedT = t.coerceIn(0f, 1f)
            val oneMinusT = 1f - clampedT

            // Interpolate position
            val pos =
                Vec3(
                    a.position.x * oneMinusT + b.position.x * clampedT,
                    a.position.y * oneMinusT + b.position.y * clampedT,
                    a.position.z * oneMinusT + b.position.z * clampedT,
                )

            // Interpolate color (ARGB)
            val aA = (a.color shr 24) and 0xFF
            val aR = (a.color shr 16) and 0xFF
            val aG = (a.color shr 8) and 0xFF
            val aB = a.color and 0xFF

            val bA = (b.color shr 24) and 0xFF
            val bR = (b.color shr 16) and 0xFF
            val bG = (b.color shr 8) and 0xFF
            val bB = b.color and 0xFF

            val newA = (aA * oneMinusT + bA * clampedT).toInt()
            val newR = (aR * oneMinusT + bR * clampedT).toInt()
            val newG = (aG * oneMinusT + bG * clampedT).toInt()
            val newB = (aB * oneMinusT + bB * clampedT).toInt()

            val color = (newA shl 24) or (newR shl 16) or (newG shl 8) or newB

            // Interpolate thickness
            val thickness = a.thickness * oneMinusT + b.thickness * clampedT

            return PolylinePoint(pos, color, thickness)
        }

        /**
         * Calculate a point along a cubic Bezier curve.
         *
         * @param p0 Start point
         * @param p1 First control point
         * @param p2 Second control point
         * @param p3 End point
         * @param t Parameter (0-1)
         */
        fun cubicBezier(
            p0: PolylinePoint,
            p1: PolylinePoint,
            p2: PolylinePoint,
            p3: PolylinePoint,
            t: Float,
        ): PolylinePoint {
            if (t <= 0f) return p0
            if (t >= 1f) return p3

            val omt = 1f - t
            val omt2 = omt * omt
            val t2 = t * t

            // Cubic Bezier weights: (1-t)³, 3(1-t)²t, 3(1-t)t², t³
            val w0 = omt2 * omt
            val w1 = 3f * omt2 * t
            val w2 = 3f * omt * t2
            val w3 = t2 * t

            // Weighted position
            val pos =
                Vec3(
                    p0.position.x * w0 + p1.position.x * w1 + p2.position.x * w2 + p3.position.x * w3,
                    p0.position.y * w0 + p1.position.y * w1 + p2.position.y * w2 + p3.position.y * w3,
                    p0.position.z * w0 + p1.position.z * w1 + p2.position.z * w2 + p3.position.z * w3,
                )

            // Weighted color
            val a0 = ((p0.color shr 24) and 0xFF).toFloat()
            val r0 = ((p0.color shr 16) and 0xFF).toFloat()
            val g0 = ((p0.color shr 8) and 0xFF).toFloat()
            val b0 = (p0.color and 0xFF).toFloat()

            val a1 = ((p1.color shr 24) and 0xFF).toFloat()
            val r1 = ((p1.color shr 16) and 0xFF).toFloat()
            val g1 = ((p1.color shr 8) and 0xFF).toFloat()
            val b1 = (p1.color and 0xFF).toFloat()

            val a2 = ((p2.color shr 24) and 0xFF).toFloat()
            val r2 = ((p2.color shr 16) and 0xFF).toFloat()
            val g2 = ((p2.color shr 8) and 0xFF).toFloat()
            val b2 = (p2.color and 0xFF).toFloat()

            val a3 = ((p3.color shr 24) and 0xFF).toFloat()
            val r3 = ((p3.color shr 16) and 0xFF).toFloat()
            val g3 = ((p3.color shr 8) and 0xFF).toFloat()
            val b3 = (p3.color and 0xFF).toFloat()

            val newA = (a0 * w0 + a1 * w1 + a2 * w2 + a3 * w3).toInt().coerceIn(0, 255)
            val newR = (r0 * w0 + r1 * w1 + r2 * w2 + r3 * w3).toInt().coerceIn(0, 255)
            val newG = (g0 * w0 + g1 * w1 + g2 * w2 + g3 * w3).toInt().coerceIn(0, 255)
            val newB = (b0 * w0 + b1 * w1 + b2 * w2 + b3 * w3).toInt().coerceIn(0, 255)

            val color = (newA shl 24) or (newR shl 16) or (newG shl 8) or newB

            // Weighted thickness
            val thickness = p0.thickness * w0 + p1.thickness * w1 + p2.thickness * w2 + p3.thickness * w3

            return PolylinePoint(pos, color, thickness)
        }
    }

    /**
     * Create a copy with a different position.
     */
    fun withPosition(newPosition: Vec3) = copy(position = newPosition)

    /**
     * Create a copy with a different color.
     */
    fun withColor(newColor: Int) = copy(color = newColor)

    /**
     * Create a copy with a different thickness.
     */
    fun withThickness(newThickness: Float) = copy(thickness = newThickness)
}
