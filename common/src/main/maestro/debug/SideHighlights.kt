package maestro.debug

import net.minecraft.core.Direction

/**
 * Specifies which sides of a block should be rendered with highlighting.
 * Uses a bitfield internally for efficient storage and checking.
 */
class SideHighlights private constructor(
    private val mask: Int,
) {
    companion object {
        private const val ALL_MASK = 0x3F // 6 bits for 6 directions (0b111111)

        // Cached instances for common patterns
        private val ALL_INSTANCE = SideHighlights(ALL_MASK)
        private val TOP_INSTANCE = SideHighlights(1 shl Direction.UP.ordinal)
        private val BOTTOM_INSTANCE = SideHighlights(1 shl Direction.DOWN.ordinal)
        private val HORIZONTAL_INSTANCE =
            SideHighlights(
                (1 shl Direction.NORTH.ordinal) or
                    (1 shl Direction.SOUTH.ordinal) or
                    (1 shl Direction.EAST.ordinal) or
                    (1 shl Direction.WEST.ordinal),
            )

        /**
         * Render all 6 faces of the block.
         */
        fun all(): SideHighlights = ALL_INSTANCE

        /**
         * Render only the top face (Direction.UP).
         */
        fun top(): SideHighlights = TOP_INSTANCE

        /**
         * Render only the bottom face (Direction.DOWN).
         */
        fun bottom(): SideHighlights = BOTTOM_INSTANCE

        /**
         * Render all horizontal faces (NORTH, SOUTH, EAST, WEST).
         */
        fun horizontal(): SideHighlights = HORIZONTAL_INSTANCE

        /**
         * Render specific faces.
         *
         * @param directions Vararg of directions to render
         * @return SideHighlights configured for the specified directions
         */
        fun of(vararg directions: Direction): SideHighlights {
            var mask = 0
            directions.forEach { mask = mask or (1 shl it.ordinal) }
            return SideHighlights(mask)
        }
    }

    /**
     * Check if a specific direction should be rendered.
     *
     * @param direction The direction to check
     * @return True if this direction should be rendered
     */
    fun shouldRender(direction: Direction): Boolean = (mask and (1 shl direction.ordinal)) != 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SideHighlights) return false
        return mask == other.mask
    }

    override fun hashCode(): Int = mask
}
