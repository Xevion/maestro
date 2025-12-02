package maestro.gui.core

import maestro.gui.widget.GuiWidget
import net.minecraft.client.gui.GuiGraphics

/**
 * Abstract base class for all layout containers.
 *
 * Provides:
 * - Cell-based widget management
 * - Two-phase layout (calculateSize → calculateWidgetPositions)
 * - Generic event propagation to children
 * - Viewport culling for rendering
 *
 * Subclasses implement:
 * - onCalculateSize(): Calculate container size based on children
 * - onCalculateWidgetPositions(): Position cells within container bounds
 */
abstract class Container : GuiWidget(0, 0) {
    protected val cells = mutableListOf<Cell<*>>()

    /**
     * Adds widget to container with optional cell configuration.
     *
     * @param widget Widget to add
     * @param configure DSL block for configuring cell (padding, alignment, expansion)
     * @return Cell wrapping the widget for fluent chaining
     */
    open fun <T : GuiWidget> add(
        widget: T,
        configure: Cell<T>.() -> Unit = {},
    ): Cell<T> {
        val cell = Cell(widget).apply(configure)
        cells.add(cell)
        return cell
    }

    /**
     * Removes all widgets from container.
     */
    open fun clear() {
        cells.clear()
    }

    /**
     * Removes specific cell from container.
     */
    fun remove(cell: Cell<*>) {
        cells.remove(cell)
    }

    // ===== TWO-PHASE LAYOUT =====

    /**
     * Phase 1: Calculate container size (bottom-up).
     *
     * Children calculate first, then parent aggregates.
     * Recursively calls calculateSize() on child containers.
     */
    fun calculateSize() {
        for (cell in cells) {
            if (cell.widget is Container) {
                cell.widget.calculateSize()
            }
        }
        onCalculateSize()
    }

    /**
     * Container-specific size calculation.
     * Must set this.width and this.height based on cell sizes.
     */
    protected abstract fun onCalculateSize()

    /**
     * Phase 2: Calculate widget positions (top-down).
     *
     * Parent assigns cell bounds, then children recurse.
     * Calls onCalculateWidgetPositions() then recurses into child containers.
     */
    fun calculateWidgetPositions() {
        onCalculateWidgetPositions()

        for (cell in cells) {
            cell.alignWidget()
            if (cell.widget is Container) {
                cell.widget.calculateWidgetPositions()
            }
        }
    }

    /**
     * Container-specific position calculation.
     *
     * Must set for each cell:
     * - cell.x, cell.y (absolute screen coordinates)
     * - cell.width, cell.height (available space for cell)
     *
     * Cell.alignWidget() will position the widget within bounds.
     */
    protected abstract fun onCalculateWidgetPositions()

    // ===== LAYOUT HELPERS =====

    /**
     * Calculates total width needed for cells in a row with spacing.
     *
     * @param cells List of cells to measure
     * @param spacing Spacing between cells in pixels
     * @return Total width including all cells and spacing
     */
    protected fun rowWidth(
        cells: List<Cell<*>>,
        spacing: Int,
    ): Int {
        if (cells.isEmpty()) return 0

        var width = 0
        for ((index, cell) in cells.withIndex()) {
            if (index > 0) width += spacing
            width += cell.paddedWidth
        }
        return width
    }

    /**
     * Calculates maximum height among cells in a row.
     *
     * @param cells List of cells to measure
     * @return Maximum cell height (including padding)
     */
    protected fun rowHeight(cells: List<Cell<*>>): Int = cells.maxOfOrNull { it.paddedHeight } ?: 0

    /**
     * Calculates extra space distribution for expanded cells.
     *
     * @param cells List of cells
     * @param totalWidth Current total width of all cells
     * @param availableWidth Available width for distribution
     * @return Extra width per expanded cell (0 if no expanded cells)
     */
    protected fun calculateExpansion(
        cells: List<Cell<*>>,
        totalWidth: Int,
        availableWidth: Int,
    ): Int {
        val expandCount = cells.count { it.expandX }
        return if (expandCount > 0) {
            (availableWidth - totalWidth) / expandCount
        } else {
            0
        }
    }

    // ===== RENDERING =====

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        tickDelta: Float,
    ) {
        for (cell in cells) {
            if (isWidgetVisible(cell.widget)) {
                cell.widget.updateHover(mouseX, mouseY)
                cell.widget.render(graphics, mouseX, mouseY, tickDelta)
            }
        }
    }

    /**
     * Checks if widget is visible in viewport.
     * Override for custom culling (e.g., scissor clipping).
     */
    protected open fun isWidgetVisible(widget: GuiWidget): Boolean = true

    // ===== EVENT PROPAGATION =====

    override fun handleClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        for (cell in cells) {
            if (cell.widget.isMouseOver(mouseX, mouseY)) {
                if (cell.widget.handleClick(mouseX, mouseY, button)) {
                    return true
                }
            }
        }
        return false
    }

    override fun handleDrag(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        for (cell in cells) {
            if (cell.widget.handleDrag(mouseX, mouseY, button)) {
                return true
            }
        }
        return false
    }

    override fun handleRelease(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        for (cell in cells) {
            if (cell.widget.handleRelease(mouseX, mouseY, button)) {
                return true
            }
        }
        return false
    }

    override fun handleScroll(
        mouseX: Int,
        mouseY: Int,
        amount: Double,
    ): Boolean {
        for (cell in cells) {
            if (cell.widget.handleScroll(mouseX, mouseY, amount)) {
                return true
            }
        }
        return false
    }

    override fun handleKeyPress(
        key: Int,
        scanCode: Int,
        modifiers: Int,
    ): Boolean {
        for (cell in cells) {
            if (cell.widget.handleKeyPress(key, scanCode, modifiers)) {
                return true
            }
        }
        return false
    }

    override fun handleCharTyped(
        char: Char,
        modifiers: Int,
    ): Boolean {
        for (cell in cells) {
            if (cell.widget.handleCharTyped(char, modifiers)) {
                return true
            }
        }
        return false
    }
}
