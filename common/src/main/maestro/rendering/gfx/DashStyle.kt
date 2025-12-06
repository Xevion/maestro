package maestro.rendering.gfx

/**
 * Types of dashes for lines and polylines.
 */
enum class DashType {
    /** Standard rectangular dashes */
    BASIC,

    /** Rounded dashes (semicircle caps on each dash) */
    ROUNDED,

    /** Angled/skewed dashes (like hazard stripes) */
    ANGLED,

    /** Chevron/arrow-shaped dashes */
    CHEVRON,
}

/**
 * Space in which dash sizes are defined.
 */
enum class DashSpace {
    /**
     * Dash sizes are relative to line thickness.
     * A size of 1.0 means the dash is as long as the line is thick.
     */
    RELATIVE,

    /**
     * Dash sizes are in world units (blocks).
     */
    WORLD_UNITS,

    /**
     * Sets the total number of dashes across the line.
     * Size = dash count, spacing = dash:space ratio (0-1).
     */
    FIXED_COUNT,
}

/**
 * Snapping modes for dashed lines.
 */
enum class DashSnapping {
    /** No snapping - dashes may be cut off at line ends */
    OFF,

    /** Snap so the dash pattern tiles seamlessly along the line */
    TILING,

    /** Snap so there's a solid dash at each end of the line */
    END_TO_END,
}

/**
 * Complete dash style configuration for lines and polylines.
 *
 * @property type The visual style of the dashes
 * @property space How dash sizes are interpreted
 * @property snap How dashes are snapped/aligned
 * @property size Dash size (interpretation depends on space)
 * @property spacing Gap size between dashes (interpretation depends on space)
 * @property offset Dash offset as fraction of period (0-1 repeats)
 * @property shapeModifier Style modifier for angled/chevron dashes (-1 to 1)
 */
data class DashStyle(
    val type: DashType = DashType.BASIC,
    val space: DashSpace = DashSpace.RELATIVE,
    val snap: DashSnapping = DashSnapping.OFF,
    val size: Float = 4f,
    val spacing: Float = 4f,
    val offset: Float = 0f,
    val shapeModifier: Float = 1f,
) {
    /**
     * Calculate the absolute dash size for rendering.
     *
     * @param thickness The line thickness
     * @param totalLength The total length of the line (for FIXED_COUNT)
     */
    fun getAbsoluteSize(
        thickness: Float,
        totalLength: Float = 0f,
    ): Float =
        when (space) {
            DashSpace.RELATIVE -> thickness * size
            DashSpace.WORLD_UNITS -> size
            DashSpace.FIXED_COUNT -> if (size > 0) totalLength / size else 0f
        }

    /**
     * Calculate the absolute spacing size for rendering.
     *
     * @param thickness The line thickness
     * @param dashSize The calculated absolute dash size (for FIXED_COUNT)
     */
    fun getAbsoluteSpacing(
        thickness: Float,
        dashSize: Float = 0f,
    ): Float =
        when (space) {
            DashSpace.RELATIVE -> thickness * spacing
            DashSpace.WORLD_UNITS -> spacing
            DashSpace.FIXED_COUNT -> dashSize * spacing // spacing is ratio in FIXED_COUNT mode
        }

    /**
     * Calculate the period (dash + space) length.
     */
    fun getPeriod(
        thickness: Float,
        totalLength: Float = 0f,
    ): Float {
        val dashSize = getAbsoluteSize(thickness, totalLength)
        val spacingSize = getAbsoluteSpacing(thickness, dashSize)
        return dashSize + spacingSize
    }

    companion object {
        /** Default dash style with balanced proportions */
        val DEFAULT =
            DashStyle(
                type = DashType.BASIC,
                space = DashSpace.RELATIVE,
                snap = DashSnapping.OFF,
                size = 4f,
                spacing = 4f,
            )

        /** Default dash style for lines with end-to-end snapping */
        val DEFAULT_LINE =
            DashStyle(
                type = DashType.BASIC,
                space = DashSpace.RELATIVE,
                snap = DashSnapping.END_TO_END,
                size = 4f,
                spacing = 4f,
            )

        /** Default dash style for rings/circles with tiling */
        val DEFAULT_RING =
            DashStyle(
                type = DashType.BASIC,
                space = DashSpace.FIXED_COUNT,
                snap = DashSnapping.TILING,
                size = 16f,
                spacing = 0.5f,
            )

        /**
         * Create a dash style with sizes relative to line thickness.
         *
         * @param type Dash type
         * @param size Dash size as multiple of thickness
         * @param spacing Gap size as multiple of thickness
         * @param snap Snapping mode
         * @param offset Offset as fraction of period
         * @param shapeModifier Style modifier (-1 to 1)
         */
        fun relative(
            type: DashType = DashType.BASIC,
            size: Float = 4f,
            spacing: Float = 4f,
            snap: DashSnapping = DashSnapping.OFF,
            offset: Float = 0f,
            shapeModifier: Float = 1f,
        ) = DashStyle(type, DashSpace.RELATIVE, snap, size, spacing, offset, shapeModifier)

        /**
         * Create a dash style with sizes in world units.
         *
         * @param type Dash type
         * @param size Dash size in world units (blocks)
         * @param spacing Gap size in world units (blocks)
         * @param snap Snapping mode
         * @param offset Offset as fraction of period
         * @param shapeModifier Style modifier (-1 to 1)
         */
        fun worldUnits(
            type: DashType = DashType.BASIC,
            size: Float = 0.5f,
            spacing: Float = 0.5f,
            snap: DashSnapping = DashSnapping.OFF,
            offset: Float = 0f,
            shapeModifier: Float = 1f,
        ) = DashStyle(type, DashSpace.WORLD_UNITS, snap, size, spacing, offset, shapeModifier)

        /**
         * Create a dash style with a fixed number of dashes.
         *
         * @param type Dash type
         * @param count Number of dashes
         * @param spacingRatio Ratio of space to period (0-1, where 0.5 = equal dash and space)
         * @param snap Snapping mode
         * @param offset Offset as fraction of period
         * @param shapeModifier Style modifier (-1 to 1)
         */
        fun fixedCount(
            type: DashType = DashType.BASIC,
            count: Float = 8f,
            spacingRatio: Float = 0.5f,
            snap: DashSnapping = DashSnapping.TILING,
            offset: Float = 0f,
            shapeModifier: Float = 1f,
        ) = DashStyle(type, DashSpace.FIXED_COUNT, snap, count, spacingRatio.coerceIn(0f, 0.99f), offset, shapeModifier)
    }
}
