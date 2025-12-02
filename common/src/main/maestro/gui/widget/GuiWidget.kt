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
    val width: Int,
    val height: Int,
) {
    var x: Int = 0
        protected set
    var y: Int = 0
        protected set
    var hovered: Boolean = false
        protected set

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
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     */
    fun updateHover(
        mouseX: Int,
        mouseY: Int,
    ) {
        hovered = isMouseOver(mouseX, mouseY)
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
}
