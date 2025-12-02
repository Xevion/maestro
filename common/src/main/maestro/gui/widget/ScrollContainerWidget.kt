package maestro.gui.widget

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.util.Mth

/**
 * Scrollable container widget for displaying many widgets with vertical scrolling.
 *
 * Features:
 * - Scissor clipping to hide content outside viewport
 * - Smooth scroll interpolation
 * - Viewport culling (only renders visible widgets)
 * - Scrollbar with draggable handle
 * - Mouse wheel scrolling
 *
 * @param maxHeight Maximum viewport height in pixels
 * @param width Container width
 */
class ScrollContainerWidget(
    width: Int,
    private val maxHeight: Int,
) : GuiWidget(width, maxHeight) {
    private val children = mutableListOf<GuiWidget>()

    // Scroll state
    private var scroll: Double = 0.0
    private var targetScroll: Double = 0.0

    // Scrollbar dragging state
    private var draggingScrollbar: Boolean = false
    private var dragStartY: Int = 0
    private var dragStartScroll: Double = 0.0

    /**
     * Adds a child widget to this container.
     * Children are laid out vertically with spacing.
     *
     * @param widget Widget to add
     */
    fun addChild(widget: GuiWidget) {
        children.add(widget)
    }

    /**
     * Removes all child widgets from this container.
     */
    fun clearChildren() {
        children.clear()
        scroll = 0.0
        targetScroll = 0.0
    }

    /**
     * Gets the total height of all child widgets including spacing.
     *
     * @return Total content height in pixels
     */
    private fun getContentHeight(): Int {
        if (children.isEmpty()) return 0

        return children.withIndex().sumOf { (index, widget) ->
            widget.height + if (index < children.size - 1) WIDGET_SPACING else 0
        }
    }

    /**
     * Checks if scrolling is needed (content taller than viewport).
     *
     * @return true if scrollbar should be shown
     */
    private fun needsScrollbar(): Boolean = getContentHeight() > maxHeight

    /**
     * Gets the maximum scroll position.
     *
     * @return Max scroll value (0 if no scrolling needed)
     */
    private fun getMaxScroll(): Double {
        val contentHeight = getContentHeight()
        return if (contentHeight > maxHeight) {
            (contentHeight - maxHeight).toDouble()
        } else {
            0.0
        }
    }

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        tickDelta: Float,
    ) {
        if (children.isEmpty()) return

        // Smooth scroll interpolation
        val maxScroll = getMaxScroll()
        targetScroll = Mth.clamp(targetScroll, 0.0, maxScroll)
        scroll = Mth.lerp(0.3, scroll, targetScroll) // Smooth interpolation
        scroll = Mth.clamp(scroll, 0.0, maxScroll)

        // Enable scissor clipping for content area
        val contentWidth = if (needsScrollbar()) width - SCROLLBAR_WIDTH - SCROLLBAR_PADDING else width
        graphics.enableScissor(x, y, x + contentWidth, y + maxHeight)

        // Render children with viewport culling
        var currentY = y - scroll.toInt()

        for (widget in children) {
            widget.setPosition(x, currentY)

            // Viewport culling: only render if visible
            if (currentY + widget.height >= y && currentY <= y + maxHeight) {
                widget.updateHover(mouseX, mouseY)
                widget.render(graphics, mouseX, mouseY, tickDelta)
            }

            currentY += widget.height + WIDGET_SPACING
        }

        graphics.disableScissor()

        // Render scrollbar if needed
        if (needsScrollbar()) {
            renderScrollbar(graphics, mouseX, mouseY)
        }
    }

    /**
     * Renders the scrollbar track and handle.
     *
     * @param graphics GuiGraphics for rendering
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     */
    private fun renderScrollbar(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        val scrollbarX = x + width - SCROLLBAR_WIDTH
        val scrollbarY = y

        // Render track
        graphics.fill(
            scrollbarX,
            scrollbarY,
            scrollbarX + SCROLLBAR_WIDTH,
            scrollbarY + maxHeight,
            SCROLLBAR_TRACK_COLOR,
        )

        // Calculate handle dimensions
        val contentHeight = getContentHeight()
        val handleHeight = ((maxHeight.toDouble() / contentHeight.toDouble()) * maxHeight).toInt().coerceAtLeast(MIN_HANDLE_HEIGHT)
        val maxHandleY = maxHeight - handleHeight
        val handleY = scrollbarY + ((scroll / getMaxScroll()) * maxHandleY).toInt()

        // Check if mouse is over handle
        val handleHovered =
            mouseX >= scrollbarX &&
                mouseX < scrollbarX + SCROLLBAR_WIDTH &&
                mouseY >= handleY &&
                mouseY < handleY + handleHeight

        // Render handle
        val handleColor =
            when {
                draggingScrollbar -> SCROLLBAR_HANDLE_DRAGGING
                handleHovered -> SCROLLBAR_HANDLE_HOVERED
                else -> SCROLLBAR_HANDLE_NORMAL
            }

        graphics.fill(
            scrollbarX,
            handleY,
            scrollbarX + SCROLLBAR_WIDTH,
            handleY + handleHeight,
            handleColor,
        )
    }

    override fun handleClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (button != 0) return false // Only left click

        // Check scrollbar click
        if (needsScrollbar()) {
            val scrollbarX = x + width - SCROLLBAR_WIDTH
            if (mouseX >= scrollbarX &&
                mouseX < scrollbarX + SCROLLBAR_WIDTH &&
                mouseY >= y &&
                mouseY < y + maxHeight
            ) {
                // Start scrollbar dragging
                draggingScrollbar = true
                dragStartY = mouseY
                dragStartScroll = scroll
                return true
            }
        }

        // Forward click to visible children
        for (widget in children) {
            if (widget.isMouseOver(mouseX, mouseY)) {
                if (widget.handleClick(mouseX, mouseY, button)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Handles mouse dragging for scrollbar and children.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param button Mouse button
     * @return true if drag was consumed
     */
    fun handleDrag(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        // First check scrollbar dragging
        if (draggingScrollbar && button == 0) {
            val deltaY = mouseY - dragStartY
            val contentHeight = getContentHeight()
            val handleHeight = ((maxHeight.toDouble() / contentHeight.toDouble()) * maxHeight).toInt().coerceAtLeast(MIN_HANDLE_HEIGHT)
            val maxHandleY = maxHeight - handleHeight

            // Convert pixel delta to scroll delta
            val scrollDelta = (deltaY.toDouble() / maxHandleY) * getMaxScroll()
            targetScroll = (dragStartScroll + scrollDelta).coerceIn(0.0, getMaxScroll())

            return true
        }

        // Forward to children (e.g., SettingRowWidget with sliders)
        for (child in children) {
            if (child is SettingRowWidget) {
                if (child.handleDrag(mouseX, mouseY, button)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Handles mouse release to stop scrollbar dragging and forward to children.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param button Mouse button
     * @return true if release was consumed
     */
    fun handleRelease(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false
            return true
        }

        // Forward to children
        for (child in children) {
            if (child is SettingRowWidget) {
                if (child.handleRelease(mouseX, mouseY, button)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Handles mouse wheel scrolling for container and children.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param amount Scroll amount (positive = up, negative = down)
     * @return true if scroll was consumed
     */
    fun handleScroll(
        mouseX: Int,
        mouseY: Int,
        amount: Double,
    ): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false

        // First try children (e.g., sliders in SettingRowWidget)
        for (child in children) {
            if (child is SettingRowWidget) {
                if (child.handleScroll(mouseX, mouseY, amount)) {
                    return true
                }
            }
        }

        // Then try container scroll
        if (needsScrollbar()) {
            targetScroll = (targetScroll - amount * SCROLL_SPEED).coerceIn(0.0, getMaxScroll())
            return true
        }

        return false
    }

    companion object {
        // Layout constants
        private const val WIDGET_SPACING = 5 // Spacing between child widgets
        private const val SCROLLBAR_PADDING = 2 // Gap between content and scrollbar

        // Scrollbar constants
        private const val SCROLLBAR_WIDTH = 6
        private const val MIN_HANDLE_HEIGHT = 20
        private const val SCROLL_SPEED = 20.0

        // Scrollbar colors
        private const val SCROLLBAR_TRACK_COLOR = 0xFF222222.toInt() // Dark gray
        private const val SCROLLBAR_HANDLE_NORMAL = 0xFF555555.toInt() // Medium gray
        private const val SCROLLBAR_HANDLE_HOVERED = 0xFF777777.toInt() // Light gray
        private const val SCROLLBAR_HANDLE_DRAGGING = 0xFF999999.toInt() // Very light gray
    }
}
