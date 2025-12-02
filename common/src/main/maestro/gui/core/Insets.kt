package maestro.gui.core

/**
 * Represents spacing on all four sides of a rectangle.
 *
 * Used for padding, borders, and margins in GUI layouts. Provides utilities for combining
 * insets and calculating content dimensions.
 *
 * Example:
 * ```
 * val padding = Insets(vertical = 8, horizontal = 12)
 * val border = Insets(all = 1)
 * val total = padding + border  // Insets(9, 13, 9, 13)
 *
 * val contentWidth = totalWidth - total.horizontal
 * ```
 *
 * @property top Space at the top edge in pixels
 * @property right Space at the right edge in pixels
 * @property bottom Space at the bottom edge in pixels
 * @property left Space at the left edge in pixels
 */
data class Insets(
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0,
) {
    /**
     * Creates insets with the same value on all sides.
     *
     * @param all Value for all four sides
     */
    constructor(all: Int) : this(all, all, all, all)

    /**
     * Creates insets with vertical and horizontal symmetry.
     *
     * @param vertical Value for top and bottom
     * @param horizontal Value for left and right
     */
    constructor(vertical: Int, horizontal: Int) : this(vertical, horizontal, vertical, horizontal)

    /** Total horizontal space (left + right) */
    val horizontal: Int
        get() = left + right

    /** Total vertical space (top + bottom) */
    val vertical: Int
        get() = top + bottom

    /**
     * Adds two insets together.
     *
     * Useful for combining padding and border:
     * ```
     * val total = padding + border
     * ```
     */
    operator fun plus(other: Insets) =
        Insets(
            top + other.top,
            right + other.right,
            bottom + other.bottom,
            left + other.left,
        )

    /**
     * Subtracts insets from another.
     *
     * All resulting values are clamped to non-negative.
     */
    operator fun minus(other: Insets) =
        Insets(
            (top - other.top).coerceAtLeast(0),
            (right - other.right).coerceAtLeast(0),
            (bottom - other.bottom).coerceAtLeast(0),
            (left - other.left).coerceAtLeast(0),
        )

    companion object {
        /** Zero insets (no spacing) */
        val ZERO = Insets(0)
    }
}
