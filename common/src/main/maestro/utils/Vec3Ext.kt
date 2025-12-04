package maestro.utils

import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val DEG_TO_RAD = Math.PI / 180.0

/**
 * Creates a scaled copy of this vector.
 */
fun Vec3.scale(factor: Double): Vec3 = Vec3(x * factor, y * factor, z * factor)

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
 * Normalizes the vector to the specified maximum length.
 * If the vector is shorter than maxLength, returns unchanged.
 */
fun Vec3.clampLength(maxLength: Double): Vec3 {
    val currentLength = sqrt(x * x + y * y + z * z)
    return if (currentLength > maxLength) {
        scale(maxLength / currentLength)
    } else {
        this
    }
}
