package maestro.gui.container

import maestro.gui.core.Cell
import maestro.gui.core.Container

/**
 * Table layout container with rows and columns.
 *
 * Provides grid-based layout with:
 * - Row/column structure
 * - Per-cell alignment and padding
 * - Per-row expansion (cells with expandX share extra width)
 * - Column width synchronization (columns sized to widest cell)
 *
 * **IMPORTANT: expandX behavior:**
 * - `expandX` is **row-scoped**, not column-synchronized
 * - Extra width is distributed among `expandX` cells **within each row only**
 * - If row 1 has expandX in column 2, and row 2 doesn't, column widths may differ per row
 * - For column-synchronized expansion, use VBox of HBoxes instead
 *
 * Layout algorithm:
 * - Calculate max width per column across all rows (non-expanded sizes)
 * - Calculate max height per row
 * - Distribute extra width per row among expandX cells in that row
 *
 * Usage:
 * ```
 * grid(horizontalSpacing = 5, verticalSpacing = 5) {
 *     add(label1); add(input1)
 *     row()
 *     add(label2); add(input2)
 * }
 * ```
 *
 * @property horizontalSpacing Horizontal spacing between columns in pixels
 * @property verticalSpacing Vertical spacing between rows in pixels
 */
open class Grid(
    var horizontalSpacing: Int = 3,
    var verticalSpacing: Int = 3,
) : Container() {
    private val rows = mutableListOf<MutableList<Cell<*>>>()
    private var currentRowIndex = 0

    private val rowHeights = mutableListOf<Int>()
    private val columnWidths = mutableListOf<Int>()
    private val rowWidths = mutableListOf<Int>()
    private val rowExpandXCounts = mutableListOf<Int>()

    override fun <T : maestro.gui.widget.GuiWidget> add(
        widget: T,
        configure: maestro.gui.core.Cell<T>.() -> Unit,
    ): maestro.gui.core.Cell<T> {
        val cell =
            maestro.gui.core
                .Cell(widget)
                .apply(configure)
        cells.add(cell)

        if (rows.size <= currentRowIndex) {
            rows.add(mutableListOf())
        }
        rows[currentRowIndex].add(cell)

        return cell
    }

    /**
     * Advances to next row.
     * Subsequent add() calls will add widgets to the new row.
     */
    fun row() {
        currentRowIndex++
    }

    /**
     * Gets current row index (0-based).
     */
    fun rowIndex(): Int = currentRowIndex

    /**
     * Removes row at given index.
     * Decrements currentRowIndex if needed.
     */
    fun removeRow(index: Int) {
        if (index < 0 || index >= rows.size) return

        for (cell in rows.removeAt(index)) {
            cells.remove(cell)
        }

        if (currentRowIndex >= index) {
            currentRowIndex--
        }
    }

    override fun clear() {
        super.clear()
        rows.clear()
        currentRowIndex = 0
    }

    override fun onCalculateSize() {
        calculateRowColumnInfo()

        rowWidths.clear()

        var maxWidth = 0
        var totalHeight = 0

        // Calculate width (max row width)
        for ((rowIndex, row) in rows.withIndex()) {
            var rowWidth = 0
            for ((cellIndex, _) in row.withIndex()) {
                if (cellIndex > 0) rowWidth += horizontalSpacing
                rowWidth += columnWidths[cellIndex]
            }
            rowWidths.add(rowWidth)
            maxWidth = maxOf(maxWidth, rowWidth)
        }

        // Calculate height (sum of row heights)
        for ((rowIndex, _) in rows.withIndex()) {
            if (rowIndex > 0) totalHeight += verticalSpacing
            totalHeight += rowHeights[rowIndex]
        }

        width = maxWidth
        height = totalHeight
    }

    override fun onCalculateWidgetPositions() {
        var currentY = y

        for ((rowIndex, row) in rows.withIndex()) {
            if (rowIndex > 0) currentY += verticalSpacing

            var currentX = x
            val rowHeight = rowHeights[rowIndex]
            val extraWidth =
                if (rowExpandXCounts[rowIndex] > 0) {
                    (width - rowWidths[rowIndex]) / rowExpandXCounts[rowIndex]
                } else {
                    0
                }

            for ((cellIndex, cell) in row.withIndex()) {
                if (cellIndex > 0) currentX += horizontalSpacing

                val columnWidth = columnWidths[cellIndex]

                cell.x = currentX
                cell.y = currentY
                cell.width = columnWidth + if (cell.expandX) extraWidth else 0
                cell.height = rowHeight

                currentX += columnWidth + if (cell.expandX) extraWidth else 0
            }

            currentY += rowHeight
        }
    }

    private fun calculateRowColumnInfo() {
        rowHeights.clear()
        columnWidths.clear()
        rowExpandXCounts.clear()

        for (row in rows) {
            var maxRowHeight = 0
            var expandXCount = 0

            for ((cellIndex, cell) in row.withIndex()) {
                maxRowHeight = maxOf(maxRowHeight, cell.paddedHeight)

                if (columnWidths.size <= cellIndex) {
                    columnWidths.add(cell.paddedWidth)
                } else {
                    columnWidths[cellIndex] = maxOf(columnWidths[cellIndex], cell.paddedWidth)
                }

                if (cell.expandX) expandXCount++
            }

            rowHeights.add(maxRowHeight)
            rowExpandXCounts.add(expandXCount)
        }
    }
}

/**
 * DSL builder for Grid.
 *
 * Example:
 * ```
 * grid(horizontalSpacing = 5, verticalSpacing = 5) {
 *     add(LabelWidget("Name:", 80)) { right() }
 *     add(TextInputWidget("", {}, 200)) { expandX() }
 *     row()
 *     add(LabelWidget("Email:", 80)) { right() }
 *     add(TextInputWidget("", {}, 200)) { expandX() }
 * }
 * ```
 */
fun grid(
    horizontalSpacing: Int = 3,
    verticalSpacing: Int = 3,
    init: Grid.() -> Unit,
): Grid = Grid(horizontalSpacing, verticalSpacing).apply(init)
