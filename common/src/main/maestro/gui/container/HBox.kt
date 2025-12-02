package maestro.gui.container

import maestro.gui.core.Container

/**
 * Horizontal linear layout container.
 *
 * Arranges widgets horizontally with configurable spacing and expansion support.
 *
 * Layout algorithm:
 * - Width: Sum of widget widths + spacing (plus padding)
 * - Height: Maximum widget height (plus padding)
 * - Cells with expandX = true share extra width evenly
 * - Widgets arrange left-to-right with spacing between
 *
 * @property spacing Horizontal spacing between widgets in pixels
 */
open class HBox(
    var spacing: Int = 5,
) : Container() {
    private var calculatedWidth: Int = 0
    private var expandXCount: Int = 0

    override fun onCalculateSize() {
        width = rowWidth(cells, spacing)
        height = rowHeight(cells)
        expandXCount = cells.count { it.expandX }
        calculatedWidth = width
    }

    override fun onCalculateWidgetPositions() {
        var currentX = x
        val extraWidth =
            if (expandXCount > 0) {
                (width - calculatedWidth) / expandXCount
            } else {
                0
            }

        for ((index, cell) in cells.withIndex()) {
            if (index > 0) currentX += spacing

            cell.x = currentX
            cell.y = y
            cell.width = cell.paddedWidth + if (cell.expandX) extraWidth else 0
            cell.height = height

            currentX += cell.width
        }
    }
}

/**
 * DSL builder for HBox.
 *
 * Example:
 * ```
 * hbox(spacing = 5) {
 *     add(ButtonWidget("Left", {}, 100)) { expandX() }
 *     add(ButtonWidget("Right", {}, 100))
 * }
 * ```
 */
fun hbox(
    spacing: Int = 5,
    init: HBox.() -> Unit,
): HBox = HBox(spacing).apply(init)
