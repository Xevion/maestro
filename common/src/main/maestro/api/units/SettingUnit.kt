package maestro.api.units

/**
 * Represents a unit of measurement for setting values.
 *
 * Used for formatting setting values in the GUI with appropriate units
 * (e.g., "4.5 blocks", "250ms", "125%").
 *
 * Units are display-only and not persisted in settings.txt.
 */
sealed class SettingUnit {
    /**
     * Formats a value with the full unit suffix.
     *
     * Examples: "4.5 blocks", "250ms", "125%"
     */
    abstract fun format(value: Double): String

    /**
     * Formats a value in compact form (value only, no unit).
     *
     * Examples: "4.5", "250", "125"
     */
    abstract fun formatCompact(value: Double): String

    /**
     * Returns the display name of this unit.
     *
     * Examples: "blocks", "ms", "%"
     */
    abstract fun displayName(): String

    /**
     * Returns the default precision (decimal places) for this unit.
     */
    abstract fun defaultPrecision(): Int
}

/**
 * Unit for distance-based settings (blocks, chunks).
 *
 * @param type The distance type (BLOCKS or CHUNKS)
 * @param precision Number of decimal places (defaults to type's default precision)
 */
data class DistanceUnit(
    val type: DistanceType = DistanceType.BLOCKS,
    val precision: Int = type.defaultPrecision,
) : SettingUnit() {
    enum class DistanceType(
        val suffix: String,
        val defaultPrecision: Int,
    ) {
        BLOCKS("blocks", 1),
        CHUNKS("chunks", 2),
    }

    override fun format(value: Double): String = String.format("%.${precision}f ${type.suffix}", value)

    override fun formatCompact(value: Double): String = String.format("%.${precision}f", value)

    override fun displayName(): String = type.suffix

    override fun defaultPrecision(): Int = type.defaultPrecision
}

/**
 * Unit for duration-based settings (milliseconds, seconds, ticks).
 *
 * Smart formatting:
 * - MILLISECONDS: "5ms" if <1000, "2.5s" if >=1000
 * - SECONDS: "2.5s"
 * - TICKS: "20 ticks"
 *
 * @param type The duration type
 * @param precision Number of decimal places (defaults to type's default precision)
 */
data class DurationUnit(
    val type: DurationType = DurationType.MILLISECONDS,
    val precision: Int = type.defaultPrecision,
) : SettingUnit() {
    enum class DurationType(
        val defaultPrecision: Int,
    ) {
        MILLISECONDS(0),
        SECONDS(1),
        TICKS(0),
    }

    override fun format(value: Double): String =
        when (type) {
            DurationType.MILLISECONDS ->
                if (value < 1000) {
                    "${value.toInt()}ms"
                } else {
                    String.format("%.1fs", value / 1000.0)
                }
            DurationType.SECONDS -> String.format("%.${precision}fs", value)
            DurationType.TICKS -> "${value.toInt()} ticks"
        }

    override fun formatCompact(value: Double): String =
        when (type) {
            DurationType.MILLISECONDS ->
                if (value < 1000) {
                    value.toInt().toString()
                } else {
                    String.format("%.1f", value / 1000.0)
                }
            DurationType.SECONDS -> String.format("%.${precision}f", value)
            DurationType.TICKS -> value.toInt().toString()
        }

    override fun displayName(): String =
        when (type) {
            DurationType.MILLISECONDS -> "ms"
            DurationType.SECONDS -> "s"
            DurationType.TICKS -> "ticks"
        }

    override fun defaultPrecision(): Int = type.defaultPrecision
}

/**
 * Unit for percentage-based settings.
 *
 * Converts internal 0-1000 values to 0-100% display.
 * Example: 125 (internal) → "12.5%" (display)
 *
 * @param precision Number of decimal places (default 0)
 */
data class PercentageUnit(
    val precision: Int = 0,
) : SettingUnit() {
    override fun format(value: Double): String = String.format("%.${precision}f%%", value / 10.0)

    override fun formatCompact(value: Double): String = String.format("%.${precision}f", value / 10.0)

    override fun displayName(): String = "%"

    override fun defaultPrecision(): Int = precision
}

/**
 * Unit for arbitrary numeric values with optional suffix.
 *
 * Used for dimensionless values or custom units.
 * Example: multiplier with suffix "x" → "2.5x"
 *
 * @param precision Number of decimal places (default 2)
 * @param suffix Optional suffix to append (e.g., "x" for multipliers)
 */
data class ArbitraryUnit(
    val precision: Int = 2,
    val suffix: String? = null,
) : SettingUnit() {
    override fun format(value: Double): String {
        val formatted = String.format("%.${precision}f", value)
        return if (suffix != null) "$formatted$suffix" else formatted
    }

    override fun formatCompact(value: Double): String = String.format("%.${precision}f", value)

    override fun displayName(): String = suffix ?: ""

    override fun defaultPrecision(): Int = precision
}
