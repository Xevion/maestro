package maestro.gui.widget

import net.minecraft.client.gui.GuiGraphics

/**
 * Abstract base class for all GUI widgets.
 *
 * Provides:
 * - Position and bounds management
 * - Mouse hover detection
 * - Abstract render and click handling
 */
abstract class GuiWidget(
    var width: Int,
    var height: Int,
) {
    var x: Int = 0
    var y: Int = 0
    var hovered: Boolean = false
        protected set

    protected var tooltipLines: List<String>? = null
    private var hoverStartTime: Long = 0

    companion object {
        const val TOOLTIP_DELAY_MS = 500L
    }

    /**
     * Renders the widget at its current position.
     *
     * @param graphics GuiGraphics for rendering
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param tickDelta Partial tick for interpolation
     */
    abstract fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        tickDelta: Float,
    )

    /**
     * Handles a mouse click on this widget.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param button Mouse button (0=left, 1=right, 2=middle)
     * @return true if the click was consumed by this widget
     */
    abstract fun handleClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean

    /**
     * Checks if the mouse is currently over this widget.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @return true if mouse is over widget bounds
     */
    fun isMouseOver(
        mouseX: Int,
        mouseY: Int,
    ): Boolean = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    /**
     * Updates hover state based on mouse position.
     *
     * Tracks hover start time for tooltip delay.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     */
    open fun updateHover(
        mouseX: Int,
        mouseY: Int,
    ) {
        val wasHovered = hovered
        hovered = isMouseOver(mouseX, mouseY)

        // Track hover start time for tooltip delay
        if (hovered && !wasHovered) {
            hoverStartTime = System.currentTimeMillis()
        }
    }

    /**
     * Sets the tooltip text for this widget.
     *
     * Tooltip will be displayed after hovering for TOOLTIP_DELAY_MS milliseconds.
     *
     * @param lines Text lines to display in tooltip
     */
    fun setTooltip(lines: List<String>) {
        tooltipLines = lines
    }

    /**
     * Gets the raw tooltip lines without hover/delay checks.
     * Used by composite widgets to copy tooltips from child widgets.
     *
     * @return Tooltip lines, or null if no tooltip set
     */
    fun getRawTooltipLines(): List<String>? = tooltipLines

    /**
     * Gets the tooltip lines if hovering and delay has elapsed.
     *
     * @return Tooltip lines, or null if not ready to display
     */
    open fun getTooltip(): List<String>? {
        if (tooltipLines == null || !hovered) return null

        val hoverDuration = System.currentTimeMillis() - hoverStartTime
        return if (hoverDuration >= TOOLTIP_DELAY_MS) tooltipLines else null
    }

    /**
     * Sets the position of this widget.
     *
     * @param x X coordinate (top-left)
     * @param y Y coordinate (top-left)
     */
    fun setPosition(
        x: Int,
        y: Int,
    ) {
        this.x = x
        this.y = y
    }

    /**
     * Handles mouse drag event.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param button Mouse button being dragged
     * @return true if the drag was consumed by this widget
     */
    open fun handleDrag(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean = false

    /**
     * Handles mouse release event.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param button Mouse button released
     * @return true if the release was consumed by this widget
     */
    open fun handleRelease(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean = false

    /**
     * Handles mouse scroll event.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param amount Scroll amount (positive = scroll up, negative = scroll down)
     * @return true if the scroll was consumed by this widget
     */
    open fun handleScroll(
        mouseX: Int,
        mouseY: Int,
        amount: Double,
    ): Boolean = false

    /**
     * Handles key press event.
     *
     * @param key Key code
     * @param scanCode Scan code
     * @param modifiers Modifier keys (shift, ctrl, alt)
     * @return true if the key press was consumed by this widget
     */
    open fun handleKeyPress(
        key: Int,
        scanCode: Int,
        modifiers: Int,
    ): Boolean = false

    /**
     * Handles character typed event.
     *
     * @param char Character typed
     * @param modifiers Modifier keys (shift, ctrl, alt)
     * @return true if the character was consumed by this widget
     */
    open fun handleCharTyped(
        char: Char,
        modifiers: Int,
    ): Boolean = false
}
