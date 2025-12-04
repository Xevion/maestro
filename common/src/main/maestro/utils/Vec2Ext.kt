@file:Suppress("unused")

package maestro.utils

import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Adds two Vec2 vectors component-wise.
 */
operator fun Vec2.plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

/**
 * Subtracts two Vec2 vectors component-wise.
 */
operator fun Vec2.minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

/**
 * Multiplies a Vec2 by a scalar.
 */
operator fun Vec2.times(scalar: Float): Vec2 = Vec2(x * scalar, y * scalar)

/**
 * Divides a Vec2 by a scalar.
 */
operator fun Vec2.div(scalar: Float): Vec2 = Vec2(x / scalar, y / scalar)

/**
 * Negates a Vec2 vector.
 */
operator fun Vec2.unaryMinus(): Vec2 = Vec2(-x, -y)

/**
 * Adds a scalar value to both components of this vector.
 * Example: Vec2(1, 2) + 0.5f = Vec2(1.5, 2.5)
 */
operator fun Vec2.plus(scalar: Float): Vec2 = Vec2(x + scalar, y + scalar)

/**
 * Calculates the squared length of this vector.
 * Useful for distance comparisons without expensive sqrt().
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Vec2.lengthSquared(): Float = x * x + y * y

/**
 * Calculates the length (magnitude) of this vector.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Vec2.length(): Double = sqrt(lengthSquared().toDouble())

/**
 * Calculates the squared distance to another Vec2.
 */
fun Vec2.distanceSquaredTo(other: Vec2): Float {
    val dx = x - other.x
    val dy = y - other.y
    return dx * dx + dy * dy
}

/**
 * Calculates the distance to another Vec2.
 */
fun Vec2.distanceTo(other: Vec2): Double = sqrt(distanceSquaredTo(other).toDouble())

/**
 * Returns a normalized (unit length) version of this vector.
 * If the vector has near-zero length, returns the original vector.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Vec2.normalized(): Vec2 {
    val len = length()
    return if (len > 0.0001) this / len else this
}

/**
 * Returns a normalized vector or zero vector if length is near-zero.
 */
fun Vec2.normalizedOrZero(): Vec2 {
    val len = length()
    return if (len > 0.0001) this / len else Vec2(0f, 0f)
}

/**
 * Calculates the dot product of two Vec2 vectors.
 * Useful for determining if vectors point in similar directions.
 */
infix fun Vec2.dot(other: Vec2): Float = x * other.x + y * other.y

/**
 * Projects this vector onto the line segment from [lineStart] to [lineEnd].
 * The result is clamped to the line segment (t in [0, 1]).
 *
 * Returns the closest point on the line segment to this vector.
 */
fun Vec2.projectOnto(
    lineStart: Vec2,
    lineEnd: Vec2,
): Vec2 {
    val dir = lineEnd - lineStart
    val lenSq = dir.lengthSquared()

    // Line segment is essentially a point
    if (lenSq < 0.0001f) return lineStart

    // Calculate parameter t (0 = lineStart, 1 = lineEnd)
    val t = ((this - lineStart) dot dir) / lenSq
    val clampedT = t.coerceIn(0f, 1f)

    return lineStart + dir * clampedT
}

/**
 * Calculates the perpendicular distance from this point to the line segment
 * from [lineStart] to [lineEnd].
 *
 * Uses the cross product formula: |cross| / |line|
 */
fun Vec2.perpendicularDistanceTo(
    lineStart: Vec2,
    lineEnd: Vec2,
): Double {
    val dir = lineEnd - lineStart
    val lenSq = dir.lengthSquared()

    // Line segment is essentially a point - return direct distance
    if (lenSq < 0.0001f) return distanceTo(lineStart)

    // Calculate cross product magnitude: |a × b| = |a.x * b.y - a.y * b.x|
    val cross = abs(dir.x * (lineStart.y - y) - (lineStart.x - x) * dir.y)
    return cross / sqrt(lenSq.toDouble())
}

/**
 * Linearly interpolates between this vector and [other] by parameter [t].
 * - t = 0.0 returns this vector
 * - t = 1.0 returns [other]
 * - t = 0.5 returns midpoint
 */
fun Vec2.lerp(
    other: Vec2,
    t: Float,
): Vec2 = Vec2(x + (other.x - x) * t, y + (other.y - y) * t)

/**
 * Converts this Vec2 to a Vec3 by placing it in the XZ plane with the given Y coordinate.
 * Common for horizontal movement (x, z) with a specific height.
 */
fun Vec2.toVec3XZ(y: Double): Vec3 = Vec3(x.toDouble(), y, this.y.toDouble())

/**
 * Converts this Vec2 to a Vec3 by placing it in the XY plane with the given Z coordinate.
 */
fun Vec2.toVec3XY(z: Double): Vec3 = Vec3(x.toDouble(), this.y.toDouble(), z)

/**
 * Converts this Vec2 to a Vec3 by placing it in the YZ plane with the given X coordinate.
 */
fun Vec2.toVec3YZ(x: Double): Vec3 = Vec3(x, this.x.toDouble(), this.y.toDouble())

/**
 * Projects this Vec3 onto the XZ plane (horizontal plane).
 * Returns Vec2(x, z) - most common for movement calculations.
 */
val Vec3.xz: Vec2 get() = Vec2(x.toFloat(), z.toFloat())

/**
 * Projects this Vec3 onto the XY plane.
 * Returns Vec2(x, y).
 */
val Vec3.xy: Vec2 get() = Vec2(x.toFloat(), y.toFloat())

/**
 * Projects this Vec3 onto the YZ plane.
 * Returns Vec2(y, z).
 */
val Vec3.yz: Vec2 get() = Vec2(y.toFloat(), z.toFloat())

/**
 * Rotates this vector 90 degrees counter-clockwise.
 * Example: Vec2(1, 0).rotated90() = Vec2(0, 1)
 */
fun Vec2.rotated90(): Vec2 = Vec2(-y, x)

/**
 * Clamps the length of this vector to the specified maximum.
 * If the vector is shorter than maxLength, returns unchanged.
 */
fun Vec2.clampLength(maxLength: Float): Vec2 {
    val currentLength = length()
    return if (currentLength > maxLength) {
        this * (maxLength / currentLength.toFloat())
    } else {
        this
    }
}

/**
 * Calculates the direction vector from this position to another.
 * Result is not normalized. For normalized direction, use normalizedDirectionTo().
 */
fun Vec2.directionTo(other: Vec2): Vec2 = other - this

/**
 * Calculates the normalized direction vector from this position to another.
 * Returns zero vector if positions are too close (distance < 0.0001).
 */
fun Vec2.normalizedDirectionTo(other: Vec2): Vec2 {
    val dir = directionTo(other)
    return dir.normalizedOrZero()
}
