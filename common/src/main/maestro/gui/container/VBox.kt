package maestro.gui.container

import maestro.gui.core.Container

/**
 * Vertical linear layout container.
 *
 * Stacks widgets vertically with configurable spacing.
 *
 * Layout algorithm:
 * - Width: Maximum widget width (plus padding)
 * - Height: Sum of widget heights + spacing (plus padding)
 * - Each cell spans full container width
 * - Widgets stack top-to-bottom with spacing between
 *
 * @property spacing Vertical spacing between widgets in pixels
 */
open class VBox(
    var spacing: Int = 5,
) : Container() {
    override fun onCalculateSize() {
        width = cells.maxOfOrNull { it.paddedWidth } ?: 0

        var totalHeight = 0
        for ((index, cell) in cells.withIndex()) {
            if (index > 0) totalHeight += spacing
            totalHeight += cell.paddedHeight
        }
        height = totalHeight
    }

    override fun onCalculateWidgetPositions() {
        var currentY = y

        for ((index, cell) in cells.withIndex()) {
            if (index > 0) currentY += spacing

            cell.x = x
            cell.y = currentY
            cell.width = width
            cell.height = cell.paddedHeight

            currentY += cell.height
        }
    }
}

/**
 * DSL builder for VBox.
 *
 * Example:
 * ```
 * vbox(spacing = 10) {
 *     add(LabelWidget("Title", 200)) { centerX() }
 *     add(ButtonWidget("Click", onClick, 150))
 * }
 * ```
 */
fun vbox(
    spacing: Int = 5,
    init: VBox.() -> Unit,
): VBox = VBox(spacing).apply(init)
