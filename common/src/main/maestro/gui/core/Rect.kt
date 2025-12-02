package maestro.gui.core

/**
 * Represents a rectangular region with position and size.
 *
 * Provides utilities for bounds checking, coordinate calculations, and inset/expand operations.
 *
 * Example:
 * ```
 * val widgetBounds = Rect(x, y, width, height)
 * if (widgetBounds.contains(mouseX, mouseY)) {
 *     // Mouse is over widget
 * }
 *
 * val contentArea = widgetBounds.inset(padding + border)
 * ```
 *
 * @property x Left edge x-coordinate
 * @property y Top edge y-coordinate
 * @property width Width in pixels
 * @property height Height in pixels
 */
data class Rect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    /** Right edge x-coordinate (x + width) */
    val right: Int
        get() = x + width

    /** Bottom edge y-coordinate (y + height) */
    val bottom: Int
        get() = y + height

    /** Center x-coordinate */
    val centerX: Int
        get() = x + width / 2

    /** Center y-coordinate */
    val centerY: Int
        get() = y + height / 2

    /**
     * Checks if a point is inside this rectangle.
     *
     * Uses half-open interval: [x, x+width) × [y, y+height)
     *
     * @param px Point x-coordinate
     * @param py Point y-coordinate
     * @return true if point is inside rectangle
     */
    fun contains(
        px: Int,
        py: Int,
    ): Boolean = px >= x && px < right && py >= y && py < bottom

    /**
     * Creates a new rectangle inset (shrunk) by the given insets.
     *
     * Example:
     * ```
     * val outer = Rect(0, 0, 100, 100)
     * val padding = Insets(all = 10)
     * val inner = outer.inset(padding)  // Rect(10, 10, 80, 80)
     * ```
     *
     * @param insets Insets to apply (positive values shrink the rectangle)
     * @return New rectangle shrunk by insets
     */
    fun inset(insets: Insets) =
        Rect(
            x + insets.left,
            y + insets.top,
            (width - insets.horizontal).coerceAtLeast(0),
            (height - insets.vertical).coerceAtLeast(0),
        )

    /**
     * Creates a new rectangle expanded (grown) by the given insets.
     *
     * Example:
     * ```
     * val inner = Rect(10, 10, 80, 80)
     * val padding = Insets(all = 10)
     * val outer = inner.expand(padding)  // Rect(0, 0, 100, 100)
     * ```
     *
     * @param insets Insets to apply (positive values grow the rectangle)
     * @return New rectangle expanded by insets
     */
    fun expand(insets: Insets) =
        Rect(
            x - insets.left,
            y - insets.top,
            width + insets.horizontal,
            height + insets.vertical,
        )
}
