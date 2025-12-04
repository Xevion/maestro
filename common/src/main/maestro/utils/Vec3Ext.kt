@file:Suppress("unused")

package maestro.utils

import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val DEG_TO_RAD = Math.PI / 180.0

/**
 * Adds two Vec3 vectors component-wise.
 */
operator fun Vec3.plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)

/**
 * Subtracts two Vec3 vectors component-wise.
 */
operator fun Vec3.minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)

/**
 * Multiplies a Vec3 by a scalar.
 */
operator fun Vec3.times(scalar: Double): Vec3 = Vec3(x * scalar, y * scalar, z * scalar)

/**
 * Divides a Vec3 by a scalar.
 */
operator fun Vec3.div(scalar: Double): Vec3 = Vec3(x / scalar, y / scalar, z / scalar)

/**
 * Negates a Vec3 vector.
 */
operator fun Vec3.unaryMinus(): Vec3 = Vec3(-x, -y, -z)

/**
 * Creates a scaled copy of this vector.
 */
fun Vec3.scale(factor: Double): Vec3 = this * factor

/**
 * Adds a scalar value to all components of this vector.
 * Example: Vec3(1, 2, 3) + 0.5 = Vec3(1.5, 2.5, 3.5)
 */
operator fun Vec3.plus(scalar: Double): Vec3 = Vec3(x + scalar, y + scalar, z + scalar)

/**
 * Calculates the squared length of this vector.
 * Useful for distance comparisons without expensive sqrt().
 */
fun Vec3.lengthSquared(): Double = x * x + y * y + z * z

/**
 * Calculates the length (magnitude) of this vector.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Vec3.length(): Double = sqrt(lengthSquared())

/**
 * Calculates the squared distance to another Vec3.
 */
fun Vec3.distanceSquaredTo(other: Vec3): Double {
    val dx = x - other.x
    val dy = y - other.y
    val dz = z - other.z
    return dx * dx + dy * dy + dz * dz
}

/**
 * Calculates the distance to another Vec3.
 */
fun Vec3.distanceTo(other: Vec3): Double = sqrt(distanceSquaredTo(other))

/**
 * Calculates the horizontal (XZ plane) distance to a Vec2 target.
 * Ignores Y coordinate.
 */
fun Vec3.horizontalDistanceTo(target: Vec2): Double = xz.distanceTo(target)

/**
 * Calculates the squared horizontal distance to a Vec2 target.
 * Ignores Y coordinate.
 */
fun Vec3.horizontalDistanceSquaredTo(target: Vec2): Double = xz.distanceSquaredTo(target).toDouble()

/**
 * Calculates the horizontal length (XZ plane magnitude).
 * Ignores Y coordinate.
 */
val Vec3.horizontalLength: Double get() = sqrt(xz.lengthSquared().toDouble())

/**
 * Calculates the squared horizontal length.
 * Ignores Y coordinate.
 */
val Vec3.horizontalLengthSquared: Float get() = xz.lengthSquared()

/**
 * Returns a normalized (unit length) version of this vector.
 * If the vector has near-zero length, returns the original vector.
 */
fun Vec3.normalized(): Vec3 {
    val len = length()
    return if (len > 0.0001) this / len else this
}

/**
 * Normalizes the vector to the specified maximum length.
 * If the vector is shorter than maxLength, returns unchanged.
 */
fun Vec3.clampLength(maxLength: Double): Vec3 {
    val currentLength = length()
    return if (currentLength > maxLength) {
        this * (maxLength / currentLength)
    } else {
        this
    }
}

/**
 * Linearly interpolates between this vector and [other] by parameter [t].
 * - t = 0.0 returns this vector
 * - t = 1.0 returns [other]
 * - t = 0.5 returns midpoint
 */
fun Vec3.lerp(
    other: Vec3,
    t: Double,
): Vec3 = Vec3(x + (other.x - x) * t, y + (other.y - y) * t, z + (other.z - z) * t)

/**
 * Calculates forward/backward movement vector based on yaw and pitch.
 * Positive factor moves forward, negative moves backward.
 */
fun Vec3.forward(
    yawDegrees: Float,
    pitchDegrees: Float,
    factor: Double,
): Vec3 {
    val yawRad = yawDegrees.toDouble() * DEG_TO_RAD
    val pitchRad = pitchDegrees.toDouble() * DEG_TO_RAD
    val sinYaw = sin(yawRad)
    val cosYaw = cos(yawRad)
    val sinPitch = sin(pitchRad)
    val cosPitch = cos(pitchRad)

    return Vec3(
        x - sinYaw * cosPitch * factor,
        y - sinPitch * factor,
        z + cosYaw * cosPitch * factor,
    )
}

/**
 * Calculates strafe (left/right) movement vector based on yaw.
 * Positive factor moves right, negative moves left.
 */
fun Vec3.strafe(
    yawDegrees: Float,
    factor: Double,
): Vec3 {
    val yawRad = yawDegrees.toDouble() * DEG_TO_RAD
    val sinYaw = sin(yawRad)
    val cosYaw = cos(yawRad)

    return Vec3(
        x - cosYaw * factor,
        y,
        z - sinYaw * factor,
    )
}

/**
 * Adds vertical movement (world-space Y axis).
 */
fun Vec3.vertical(factor: Double): Vec3 = Vec3(x, y + factor, z)

/**
 * Returns a copy of this vector with the X component replaced.
 */
fun Vec3.withX(x: Double): Vec3 = Vec3(x, this.y, this.z)

/**
 * Returns a copy of this vector with the Y component replaced.
 */
fun Vec3.withY(y: Double): Vec3 = Vec3(this.x, y, this.z)

/**
 * Returns a copy of this vector with the Z component replaced.
 */
fun Vec3.withZ(z: Double): Vec3 = Vec3(this.x, this.y, z)

/**
 * Returns a vector perpendicular to this one in the XZ plane.
 * Rotates 90 degrees counter-clockwise around the Y axis.
 * Useful for calculating perpendicular directions for pathfinding or movement.
 *
 * Example: Vec3(1, 0, 0).perpXZ() = Vec3(0, 0, 1)
 */
fun Vec3.perpXZ(): Vec3 = Vec3(-z, 0.0, x)

/**
 * Returns the midpoint between this vector and another.
 * Equivalent to lerp(other, 0.5).
 */
fun Vec3.midpoint(other: Vec3): Vec3 = lerp(other, 0.5)

/**
 * Calculates the squared horizontal distance (XZ plane).
 * Ignores Y coordinate. Useful for fast distance comparisons.
 */
val Vec3.horizontalDistanceSqr: Double
    get() {
        val len = xz.lengthSquared()
        return len.toDouble()
    }

/**
 * Calculates the squared horizontal distance to another Vec3.
 * Ignores Y coordinates. More efficient than horizontalDistanceTo when comparing distances.
 */
fun Vec3.horizontalDistanceSqrTo(other: Vec3): Double {
    val dx = x - other.x
    val dz = z - other.z
    return dx * dx + dz * dz
}

/**
 * Calculates the direction vector from this position to another.
 * Result is not normalized. For normalized direction, use normalizedDirectionTo().
 */
fun Vec3.directionTo(other: Vec3): Vec3 = other - this

/**
 * Calculates the normalized direction vector from this position to another.
 * Returns zero vector if positions are too close (distance < 0.0001).
 */
fun Vec3.normalizedDirectionTo(other: Vec3): Vec3 {
    val dir = directionTo(other)
    return dir.normalized()
}

/**
 * Checks if all components are valid (not NaN or Infinite).
 * Useful for validating collision shapes and calculated positions.
 */
fun Vec3.isValidFinite(): Boolean =
    !x.isNaN() &&
        !y.isNaN() &&
        !z.isNaN() &&
        !x.isInfinite() &&
        !y.isInfinite() &&
        !z.isInfinite()

/**
 * Calculates the angle in radians between this vector and another.
 * Returns 0.0 if either vector has near-zero length.
 */
fun Vec3.angleTo(other: Vec3): Double {
    val len1 = length()
    val len2 = other.length()
    if (len1 < 0.0001 || len2 < 0.0001) return 0.0

    val dotProduct = (x * other.x + y * other.y + z * other.z)
    val cosAngle = dotProduct / (len1 * len2)
    return kotlin.math.acos(cosAngle.coerceIn(-1.0, 1.0))
}
