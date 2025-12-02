package maestro.gui.panel

import maestro.gui.MaestroScreen
import maestro.gui.widget.GuiWidget
import maestro.gui.widget.ScrollContainerWidget
import maestro.gui.widget.SettingRowWidget
import maestro.gui.widget.SliderWidget
import maestro.gui.widget.TextInputWidget
import net.minecraft.client.gui.GuiGraphics

/**
 * Abstract base class for GUI panels.
 *
 * Handles:
 * - Widget list management
 * - Vertical layout with spacing
 * - Click dispatch to widgets
 * - Size calculation
 */
abstract class GuiPanel(
    protected val screen: MaestroScreen,
) {
    protected val widgets = mutableListOf<GuiWidget>()

    /**
     * Renders the panel and all its widgets.
     *
     * @param graphics GuiGraphics for rendering
     * @param panelX Panel X coordinate (top-left, inside padding)
     * @param panelY Panel Y coordinate (top-left, inside padding)
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param tickDelta Partial tick for interpolation
     */
    fun render(
        graphics: GuiGraphics,
        panelX: Int,
        panelY: Int,
        mouseX: Int,
        mouseY: Int,
        tickDelta: Float,
    ) {
        var currentY = panelY

        for (widget in widgets) {
            widget.setPosition(panelX, currentY)
            widget.updateHover(mouseX, mouseY)
            widget.render(graphics, mouseX, mouseY, tickDelta)
            currentY += widget.height + WIDGET_SPACING

            // Tick TextInputWidget for cursor blink animation
            if (widget is TextInputWidget) {
                widget.tick()
            }
        }
    }

    /**
     * Handles a mouse click on the panel.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param button Mouse button (0=left, 1=right, 2=middle)
     * @return true if the click was consumed by a widget
     */
    fun handleMouseClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        for (widget in widgets) {
            if (widget.isMouseOver(mouseX, mouseY)) {
                if (widget.handleClick(mouseX, mouseY, button)) {
                    return true // Click consumed
                }
            }
        }
        return false // Click not consumed
    }

    /**
     * Handles mouse drag events on the panel.
     * First-consumer-wins pattern for SliderWidget and ScrollContainerWidget dragging.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param button Mouse button (0=left, 1=right, 2=middle)
     * @return true if the drag was consumed by a widget
     */
    fun handleMouseDrag(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        for (widget in widgets) {
            when (widget) {
                is ScrollContainerWidget -> {
                    if (widget.handleDrag(mouseX, mouseY, button)) {
                        return true // Drag consumed
                    }
                }
                is SliderWidget -> {
                    if (widget.handleDrag(mouseX, mouseY, button)) {
                        return true // Drag consumed
                    }
                }
                is SettingRowWidget -> {
                    if (widget.handleDrag(mouseX, mouseY, button)) {
                        return true // Drag consumed
                    }
                }
            }
        }
        return false // Drag not consumed
    }

    /**
     * Handles mouse release events on the panel.
     * Used to stop SliderWidget and ScrollContainerWidget dragging.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param button Mouse button (0=left, 1=right, 2=middle)
     * @return true if the release was consumed by a widget
     */
    fun handleMouseRelease(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        for (widget in widgets) {
            when (widget) {
                is ScrollContainerWidget -> {
                    if (widget.handleRelease(mouseX, mouseY, button)) {
                        return true // Release consumed
                    }
                }
                is SliderWidget -> {
                    if (widget.handleRelease(mouseX, mouseY, button)) {
                        return true // Release consumed
                    }
                }
                is SettingRowWidget -> {
                    if (widget.handleRelease(mouseX, mouseY, button)) {
                        return true // Release consumed
                    }
                }
            }
        }
        return false // Release not consumed
    }

    /**
     * Handles mouse scroll events on the panel.
     * Used for SliderWidget and ScrollContainerWidget mouse wheel scrolling.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param amount Scroll amount (positive = up, negative = down)
     * @return true if the scroll was consumed by a widget
     */
    fun handleMouseScroll(
        mouseX: Int,
        mouseY: Int,
        amount: Double,
    ): Boolean {
        for (widget in widgets) {
            when (widget) {
                is ScrollContainerWidget -> {
                    if (widget.handleScroll(mouseX, mouseY, amount)) {
                        return true // Scroll consumed
                    }
                }
                is SettingRowWidget -> {
                    if (widget.handleScroll(mouseX, mouseY, amount)) {
                        return true // Scroll consumed
                    }
                }
                is SliderWidget -> {
                    if (widget.handleScroll(mouseX, mouseY, amount)) {
                        return true // Scroll consumed
                    }
                }
            }
        }
        return false // Scroll not consumed
    }

    /**
     * Handles key press events on the panel.
     * Used for TextInputWidget keyboard navigation and editing.
     *
     * @param key GLFW key code
     * @param scanCode Platform-specific scan code
     * @param modifiers Key modifiers (shift, ctrl, alt)
     * @return true if the key press was consumed by a widget
     */
    fun handleKeyPress(
        key: Int,
        scanCode: Int,
        modifiers: Int,
    ): Boolean {
        for (widget in widgets) {
            if (widget is TextInputWidget) {
                if (widget.handleKeyPress(key, scanCode, modifiers)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Handles character typed events on the panel.
     * Used for TextInputWidget text input.
     *
     * @param char Character typed
     * @param modifiers Key modifiers (shift, ctrl, alt)
     * @return true if the character was consumed by a widget
     */
    fun handleCharTyped(
        char: Char,
        modifiers: Int,
    ): Boolean {
        for (widget in widgets) {
            if (widget is TextInputWidget) {
                if (widget.handleCharTyped(char, modifiers)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Gets the panel width (max widget width + padding).
     *
     * @return Panel width in pixels
     */
    fun getWidth(): Int {
        val maxWidth = widgets.maxOfOrNull { it.width } ?: 0
        return maxWidth + (PANEL_PADDING * 2)
    }

    /**
     * Gets the panel height (sum of widget heights + spacing + padding).
     *
     * @return Panel height in pixels
     */
    fun getHeight(): Int {
        val totalHeight =
            widgets.withIndex().sumOf { (index, widget) ->
                widget.height + if (index < widgets.size - 1) WIDGET_SPACING else 0
            }
        return totalHeight + (PANEL_PADDING * 2)
    }

    /**
     * Adds a widget to this panel.
     *
     * @param widget Widget to add
     */
    protected fun addWidget(widget: GuiWidget) {
        widgets.add(widget)
    }

    companion object {
        // Layout constants
        protected const val WIDGET_SPACING = 5 // Pixels between widgets
        protected const val PANEL_PADDING = 10 // Pixels padding inside panel
    }
}
