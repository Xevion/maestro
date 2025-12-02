package maestro.gui.settings

import maestro.api.Setting
import maestro.gui.widget.CheckboxWidget
import maestro.gui.widget.GuiWidget
import maestro.gui.widget.SliderWidget

/**
 * Factory for creating GUI widgets from settings based on their types.
 *
 * Supports:
 * - Boolean → CheckboxWidget
 * - Integer → SliderWidget with inferred range
 * - Long → SliderWidget with inferred range
 * - Float → SliderWidget with inferred range and precision
 * - Double → SliderWidget with inferred range and precision
 *
 * Unsupported types (Color, List, Block, etc.) return null.
 */
object SettingWidgetFactory {
    /**
     * Creates a widget for a setting, or null if the type is not supported.
     *
     * @param setting The setting to create a widget for
     * @param width Widget width in pixels
     * @param onChange Callback when the value changes
     * @return Widget instance or null if type unsupported
     */
    fun createWidget(
        setting: Setting<*>,
        width: Int,
        onChange: (Any) -> Unit,
    ): GuiWidget? {
        val valueClass = setting.getValueClass()
        val name = setting.getName()

        return when {
            valueClass == java.lang.Boolean::class.java -> {
                @Suppress("UNCHECKED_CAST")
                val boolSetting = setting as Setting<Boolean>
                CheckboxWidget(
                    label = formatSettingName(name),
                    initialChecked = boolSetting.value,
                    defaultValue = boolSetting.defaultValue,
                    description = setting.description,
                    onChange = { newValue -> onChange(newValue) },
                    width = width,
                )
            }

            valueClass == java.lang.Integer::class.java -> {
                @Suppress("UNCHECKED_CAST")
                val intSetting = setting as Setting<Int>

                // Use explicit constraints if provided, otherwise infer from name
                val constraints = intSetting.constraints
                val (min, max, unit) =
                    if (constraints != null) {
                        Triple(constraints.min, constraints.max, constraints.unit)
                    } else {
                        val range = inferIntRange(name, intSetting.defaultValue)
                        Triple(range.first.toDouble(), range.second.toDouble(), null)
                    }

                SliderWidget(
                    label = formatSettingName(name),
                    min = min,
                    max = max,
                    initialValue = intSetting.value.toDouble(),
                    defaultValue = intSetting.defaultValue.toDouble(),
                    precision = 0,
                    unit = unit,
                    description = setting.description,
                    onChange = { newValue -> onChange(newValue.toInt()) },
                    width = width,
                )
            }

            valueClass == java.lang.Long::class.java -> {
                @Suppress("UNCHECKED_CAST")
                val longSetting = setting as Setting<Long>

                // Use explicit constraints if provided, otherwise infer from name
                val constraints = longSetting.constraints
                val (min, max, unit) =
                    if (constraints != null) {
                        Triple(constraints.min, constraints.max, constraints.unit)
                    } else {
                        val range = inferLongRange(name, longSetting.defaultValue)
                        Triple(range.first.toDouble(), range.second.toDouble(), null)
                    }

                SliderWidget(
                    label = formatSettingName(name),
                    min = min,
                    max = max,
                    initialValue = longSetting.value.toDouble(),
                    defaultValue = longSetting.defaultValue.toDouble(),
                    precision = 0,
                    unit = unit,
                    description = setting.description,
                    onChange = { newValue -> onChange(newValue.toLong()) },
                    width = width,
                )
            }

            valueClass == java.lang.Float::class.java -> {
                @Suppress("UNCHECKED_CAST")
                val floatSetting = setting as Setting<Float>

                // Use explicit constraints if provided, otherwise infer from name
                val constraints = floatSetting.constraints
                val (min, max, unit) =
                    if (constraints != null) {
                        Triple(constraints.min, constraints.max, constraints.unit)
                    } else {
                        val range = inferFloatRange(name, floatSetting.defaultValue)
                        Triple(range.first.toDouble(), range.second.toDouble(), null)
                    }

                // Determine precision from unit or default to 2
                val precision = unit?.defaultPrecision() ?: 2

                SliderWidget(
                    label = formatSettingName(name),
                    min = min,
                    max = max,
                    initialValue = floatSetting.value.toDouble(),
                    defaultValue = floatSetting.defaultValue.toDouble(),
                    precision = precision,
                    unit = unit,
                    description = setting.description,
                    onChange = { newValue -> onChange(newValue.toFloat()) },
                    width = width,
                )
            }

            valueClass == java.lang.Double::class.java -> {
                @Suppress("UNCHECKED_CAST")
                val doubleSetting = setting as Setting<Double>

                // Use explicit constraints if provided, otherwise infer from name
                val constraints = doubleSetting.constraints
                val (min, max, unit) =
                    if (constraints != null) {
                        Triple(constraints.min, constraints.max, constraints.unit)
                    } else {
                        val range = inferDoubleRange(name, doubleSetting.defaultValue)
                        Triple(range.first, range.second, null)
                    }

                // Determine precision from unit or default to 2
                val precision = unit?.defaultPrecision() ?: 2

                SliderWidget(
                    label = formatSettingName(name),
                    min = min,
                    max = max,
                    initialValue = doubleSetting.value,
                    defaultValue = doubleSetting.defaultValue,
                    precision = precision,
                    unit = unit,
                    description = setting.description,
                    onChange = { newValue -> onChange(newValue) },
                    width = width,
                )
            }

            else -> null // Unsupported type (Color, List, Block, etc.)
        }
    }

    /**
     * Formats a setting name for display (camelCase → Title Case).
     *
     * Examples:
     * - "allowSprint" → "Allow Sprint"
     * - "rangedCombatMinRange" → "Ranged Combat Min Range"
     */
    private fun formatSettingName(name: String): String {
        return name
            .replace(Regex("([a-z])([A-Z])"), "$1 $2") // Insert space before capitals
            .replaceFirstChar { it.uppercase() } // Capitalize first letter
    }

    /**
     * Infers a reasonable range for an integer setting based on its name and default value.
     */
    private fun inferIntRange(
        name: String,
        defaultValue: Int,
    ): Pair<Int, Int> {
        val nameLower = name.lowercase()

        return when {
            // Percentages (0-1000%)
            "percent" in nameLower -> 0 to 1000

            // Tick-based timings
            "tick" in nameLower ->
                when {
                    defaultValue < 5 -> 0 to 20
                    defaultValue < 100 -> 0 to 200
                    else -> 0 to (defaultValue * 4).coerceAtLeast(100)
                }

            // Distance-based settings
            "distance" in nameLower || "radius" in nameLower || "range" in nameLower -> 0 to 200

            // Height/Y-level settings
            "height" in nameLower || "level" in nameLower -> -64 to 320

            // Depth settings
            "depth" in nameLower -> 1 to 20

            // Iteration counts
            "iteration" in nameLower || "count" in nameLower -> 1 to 20

            // Default: reasonable range based on default value
            else ->
                when {
                    defaultValue < 10 -> 0 to 100
                    defaultValue < 100 -> 0 to (defaultValue * 5).coerceAtLeast(100)
                    else -> 0 to (defaultValue * 3).coerceAtLeast(1000)
                }
        }
    }

    /**
     * Infers a reasonable range for a long setting based on its name and default value.
     */
    private fun inferLongRange(
        name: String,
        defaultValue: Long,
    ): Pair<Long, Long> {
        val nameLower = name.lowercase()

        return when {
            // Millisecond timeouts
            "ms" in nameLower || "millis" in nameLower -> 0L to 10000L

            // Second-based durations
            "timeout" in nameLower || "duration" in nameLower -> 0L to 60L

            // Default: based on magnitude
            else -> 0L to (defaultValue * 3).coerceAtLeast(1000L)
        }
    }

    /**
     * Infers a reasonable range for a float setting based on its name and default value.
     */
    private fun inferFloatRange(
        name: String,
        defaultValue: Float,
    ): Pair<Float, Float> {
        val nameLower = name.lowercase()

        return when {
            // Normalized values (0.0 to 1.0)
            "charge" in nameLower || "ratio" in nameLower -> 0.0f to 1.0f

            // Multipliers
            "multiplier" in nameLower || "factor" in nameLower -> 0.0f to 10.0f

            // Angles/directions (degrees)
            "direction" in nameLower || "angle" in nameLower -> 0.0f to 360.0f

            // Default
            else -> 0.0f to (defaultValue * 3).coerceAtLeast(10.0f)
        }
    }

    /**
     * Infers a reasonable range for a double setting based on its name and default value.
     */
    private fun inferDoubleRange(
        name: String,
        defaultValue: Double,
    ): Pair<Double, Double> {
        val nameLower = name.lowercase()

        return when {
            // Percentages
            "percent" in nameLower -> 0.0 to 1000.0

            // Penalties/costs
            "penalty" in nameLower || "cost" in nameLower -> 0.0 to 100.0

            // Multipliers
            "multiplier" in nameLower || "factor" in nameLower -> 0.0 to 10.0

            // Distance/range settings
            "distance" in nameLower || "radius" in nameLower || "range" in nameLower -> 0.0 to 200.0

            // Precision/tolerance settings
            "precision" in nameLower || "tolerance" in nameLower || "threshold" in nameLower -> 0.0 to 1.0

            // Default
            else -> 0.0 to (defaultValue * 3).coerceAtLeast(10.0)
        }
    }
}
