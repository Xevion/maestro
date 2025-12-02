package maestro.api.constraints

import maestro.api.units.SettingUnit
import kotlin.math.roundToInt

/**
 * Constraints for numeric setting values.
 *
 * Defines min/max bounds, optional step size for discrete values, and optional unit for display.
 *
 * @param min Minimum allowed value
 * @param max Maximum allowed value
 * @param unit Optional unit for formatting display
 * @param step Optional step size for discrete values (e.g., 1.0 for integers, 0.5 for halves)
 */
data class NumericConstraints(
    val min: Double,
    val max: Double,
    val unit: SettingUnit? = null,
    val step: Double? = null,
) {
    init {
        require(min <= max) { "min ($min) must be <= max ($max)" }
        require(step == null || step > 0) { "step must be positive if provided" }
    }

    /**
     * Clamps a value to the [min, max] range.
     */
    fun clamp(value: Double): Double = value.coerceIn(min, max)

    /**
     * Applies step snapping if a step size is defined.
     *
     * Rounds the value to the nearest multiple of [step] within the [min, max] range.
     */
    fun applyStep(value: Double): Double {
        if (step == null) return value

        val steps = ((value - min) / step).roundToInt()
        return (min + (steps * step)).coerceIn(min, max)
    }

    /**
     * Returns the number of discrete steps, or null if continuous.
     *
     * Used to determine widget type (dropdown for 2-5 steps, slider otherwise).
     */
    fun stepCount(): Int? = step?.let { ((max - min) / it).roundToInt() + 1 }

    /**
     * Validates and normalizes a value by clamping and applying step.
     */
    fun normalize(value: Double): Double = applyStep(clamp(value))
}
