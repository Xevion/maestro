package maestro.api.utils

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a rotation with yaw and pitch angles.
 *
 * This is an immutable value class that validates angles on construction and provides
 * utilities for rotation manipulation including normalization, clamping, and arithmetic operations.
 *
 * Kotlin code can use operators: `rotation1 + rotation2` and `rotation1 - rotation2`.
 * Java code uses method names: `rotation1.add(rotation2)` and `rotation1.subtract(rotation2)`.
 *
 * @property yaw The yaw angle (horizontal rotation)
 * @property pitch The pitch angle (vertical rotation)
 * @throws IllegalStateException if yaw or pitch are infinite or NaN
 */
data class Rotation(
    @get:JvmName("getYaw") val yaw: Float,
    @get:JvmName("getPitch") val pitch: Float,
) {
    init {
        require(!(yaw.isInfinite() || yaw.isNaN() || pitch.isInfinite() || pitch.isNaN())) {
            "$yaw $pitch"
        }
    }

    /**
     * Adds the yaw/pitch of the specified rotations to this rotation's yaw/pitch, and returns the
     * result.
     *
     * @param other Another rotation
     * @return The result from adding the other rotation to this rotation
     */
    @JvmName("add")
    operator fun plus(other: Rotation): Rotation = Rotation(yaw + other.yaw, pitch + other.pitch)

    /**
     * Subtracts the yaw/pitch of the specified rotations from this rotation's yaw/pitch, and
     * returns the result.
     *
     * @param other Another rotation
     * @return The result from subtracting the other rotation from this rotation
     */
    @JvmName("subtract")
    operator fun minus(other: Rotation): Rotation = Rotation(yaw - other.yaw, pitch - other.pitch)

    /**
     * @return A copy of this rotation with the pitch clamped
     */
    fun clamp(): Rotation = Rotation(yaw, clampPitch(pitch))

    /**
     * @return A copy of this rotation with the yaw normalized
     */
    fun normalize(): Rotation = Rotation(normalizeYaw(yaw), pitch)

    /**
     * @return A copy of this rotation with the pitch clamped and the yaw normalized
     */
    fun normalizeAndClamp(): Rotation = Rotation(normalizeYaw(yaw), clampPitch(pitch))

    /**
     * Creates a copy of this rotation with a different pitch value.
     *
     * @param pitch The new pitch value
     * @return A new rotation with the same yaw but different pitch
     */
    fun withPitch(pitch: Float): Rotation = Rotation(yaw, pitch)

    /**
     * Checks if this rotation is really close to another rotation.
     *
     * @param other Another rotation
     * @return true if both yaw and pitch are within tolerance (< 0.01)
     */
    fun isReallyCloseTo(other: Rotation): Boolean = yawIsReallyClose(other) && abs(pitch - other.pitch) < 0.01

    /**
     * Checks if the yaw of this rotation is really close to another rotation's yaw.
     *
     * @param other Another rotation
     * @return true if normalized yaw values are within tolerance (< 0.01 or > 359.99)
     */
    fun yawIsReallyClose(other: Rotation): Boolean {
        val yawDiff = abs(normalizeYaw(yaw) - normalizeYaw(other.yaw))
        return yawDiff !in 0.01..359.99
    }

    override fun toString(): String = "Yaw: $yaw, Pitch: $pitch"

    companion object {
        /**
         * Clamps the specified pitch value between -90 and 90.
         *
         * @param pitch The input pitch
         * @return The clamped pitch
         */
        fun clampPitch(pitch: Float): Float = max(-90f, min(90f, pitch))

        /**
         * Normalizes the specified yaw value between -180 and 180.
         *
         * @param yaw The input yaw
         * @return The normalized yaw
         */
        fun normalizeYaw(yaw: Float): Float {
            var newYaw = yaw % 360f
            if (newYaw < -180f) {
                newYaw += 360f
            }
            if (newYaw > 180f) {
                newYaw -= 360f
            }
            return newYaw
        }
    }
}
