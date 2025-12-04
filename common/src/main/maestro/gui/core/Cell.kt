package maestro.gui.core

import maestro.gui.widget.GuiWidget

/**
 * Wraps a widget with layout metadata for positioning within a container.
 *
 * Provides fluent API for configuring:
 * - Padding (space around widget within cell)
 * - Alignment (position within cell bounds)
 * - Expansion (whether widget grows to fill available space)
 *
 * @param T Widget type
 * @property widget The wrapped widget
 */
class Cell<T : GuiWidget>(
    val widget: T,
) {
    /**
     * Cell bounds (set by container during layout).
     */
    var x: Int = 0
    var y: Int = 0
    var width: Int = 0
    var height: Int = 0

    /**
     * Horizontal alignment within cell.
     */
    var alignX: AlignmentX = AlignmentX.LEFT
        private set

    /**
     * Vertical alignment within cell.
     */
    var alignY: AlignmentY = AlignmentY.TOP
        private set

    /**
     * Padding around widget within cell.
     */
    var padding: Insets = Insets.ZERO
        private set

    /**
     * Whether widget expands horizontally to fill available space.
     */
    var expandX: Boolean = false
        private set

    /**
     * Whether widget expands vertically to fill available space.
     */
    var expandY: Boolean = false
        private set

    /**
     * Total width including padding (padding.left + widget.width + padding.right).
     *
     * Useful for container layout calculations.
     */
    val paddedWidth: Int
        get() = padding.left + widget.width + padding.right

    /**
     * Total height including padding (padding.top + widget.height + padding.bottom).
     *
     * Useful for container layout calculations.
     */
    val paddedHeight: Int
        get() = padding.top + widget.height + padding.bottom

    /**
     * Gets content area as Insets for use with Rect operations.
     */
    val contentInsets: Insets
        get() = padding

    /**
     * Gets content area as a Rect.
     *
     * Returns the inner rectangle after applying padding.
     */
    fun contentRect(): Rect =
        Rect(
            x + padding.left,
            y + padding.top,
            (width - padding.left - padding.right).coerceAtLeast(0),
            (height - padding.top - padding.bottom).coerceAtLeast(0),
        )

    fun left(): Cell<T> =
        apply {
            alignX = AlignmentX.LEFT
        }

    fun centerX() =
        apply {
            require(!expandX) { "Cannot center an expanded widget (expandX overrides alignment)" }
            alignX = AlignmentX.CENTER
        }

    fun right() =
        apply {
            require(!expandX) { "Cannot right-align an expanded widget (expandX overrides alignment)" }
            alignX = AlignmentX.RIGHT
        }

    fun top() =
        apply {
            alignY = AlignmentY.TOP
        }

    fun centerY() =
        apply {
            require(!expandY) { "Cannot center an expanded widget (expandY overrides alignment)" }
            alignY = AlignmentY.CENTER
        }

    fun bottom() =
        apply {
            require(!expandY) { "Cannot bottom-align an expanded widget (expandY overrides alignment)" }
            alignY = AlignmentY.BOTTOM
        }

    fun center() =
        apply {
            centerX()
            centerY()
        }

    /**
     * Sets uniform padding on all sides.
     */
    fun pad(all: Int) =
        apply {
            padding = Insets(all)
        }

    /**
     * Sets symmetric padding (vertical and horizontal).
     */
    fun pad(
        vertical: Int,
        horizontal: Int,
    ) = apply {
        padding = Insets(vertical, horizontal)
    }

    /**
     * Sets individual padding for each side.
     */
    fun pad(
        top: Int,
        right: Int,
        bottom: Int,
        left: Int,
    ) = apply {
        padding = Insets(top, right, bottom, left)
    }

    /**
     * Sets horizontal padding (left and right).
     */
    fun padHorizontal(amount: Int) =
        apply {
            padding = padding.copy(left = amount, right = amount)
        }

    /**
     * Sets vertical padding (top and bottom).
     */
    fun padVertical(amount: Int) =
        apply {
            padding = padding.copy(top = amount, bottom = amount)
        }

    /**
     * Sets top padding.
     */
    fun padTop(amount: Int) =
        apply {
            padding = padding.copy(top = amount)
        }

    /**
     * Sets right padding.
     */
    fun padRight(amount: Int) =
        apply {
            padding = padding.copy(right = amount)
        }

    /**
     * Sets bottom padding.
     */
    fun padBottom(amount: Int) =
        apply {
            padding = padding.copy(bottom = amount)
        }

    /**
     * Sets left padding.
     */
    fun padLeft(amount: Int) =
        apply {
            padding = padding.copy(left = amount)
        }

    /**
     * Widget expands horizontally to fill available space.
     */
    fun expandX() =
        apply {
            expandX = true
        }

    /**
     * Widget expands vertically to fill available space.
     */
    fun expandY() =
        apply {
            expandY = true
        }

    /**
     * Widget expands both horizontally and vertically.
     */
    fun expand() =
        apply {
            expandX()
            expandY()
        }

    /**
     * Positions widget within cell bounds.
     * Called by container during layout phase.
     */
    fun alignWidget() {
        val contentWidth = width - padding.left - padding.right
        val contentHeight = height - padding.top - padding.bottom

        // Calculate widget X position
        val widgetX =
            if (expandX) {
                x + padding.left
            } else {
                when (alignX) {
                    AlignmentX.LEFT -> x + padding.left
                    AlignmentX.CENTER -> x + padding.left + (contentWidth - widget.width) / 2
                    AlignmentX.RIGHT -> x + padding.left + (contentWidth - widget.width)
                }
            }

        // Calculate widget Y position
        val widgetY =
            if (expandY) {
                y + padding.top
            } else {
                when (alignY) {
                    AlignmentY.TOP -> y + padding.top
                    AlignmentY.CENTER -> y + padding.top + (contentHeight - widget.height) / 2
                    AlignmentY.BOTTOM -> y + padding.top + (contentHeight - widget.height)
                }
            }

        // Apply expansion to widget size if needed
        if (expandX) {
            widget.width = contentWidth
        }
        if (expandY) {
            widget.height = contentHeight
        }

        widget.setPosition(widgetX, widgetY)
    }
}
