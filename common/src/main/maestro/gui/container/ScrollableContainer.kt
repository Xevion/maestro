package maestro.gui.container

import maestro.gui.core.Container
import maestro.gui.widget.GuiWidget
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.util.Mth

/**
 * Generic scrollable container wrapper.
 *
 * Wraps any container and adds:
 * - Vertical scrollbar (if content exceeds maxHeight)
 * - Scissor clipping to viewport
 * - Smooth scroll interpolation
 * - Mouse wheel scrolling
 * - Scrollbar drag interaction
 *
 * Uses composition pattern - works with any Container type.
 *
 * @param T Container type being wrapped
 * @property inner The wrapped container
 * @property maxHeight Maximum viewport height in pixels
 */
class ScrollableContainer<T : Container>(
    val inner: T,
    private val maxHeight: Int,
) : Container() {
    private var scroll: Double = 0.0
    private var targetScroll: Double = 0.0
    private var draggingScrollbar: Boolean = false
    private var dragStartY: Int = 0
    private var dragStartScroll: Double = 0.0

    override fun onCalculateSize() {
        // Manually trigger inner size calculation (not managed via cells)
        inner.calculateSize()
        width = inner.width + if (needsScrollbar()) SCROLLBAR_WIDTH + SCROLLBAR_PADDING else 0
        height = minOf(maxHeight, inner.height)
    }

    override fun onCalculateWidgetPositions() {
        // Position inner with current scroll offset
        inner.setPosition(x, y - scroll.toInt())
        inner.calculateWidgetPositions()
    }

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        tickDelta: Float,
    ) {
        // Smooth scroll interpolation
        val maxScroll = getMaxScroll()
        targetScroll = Mth.clamp(targetScroll, 0.0, maxScroll)
        scroll = Mth.lerp(0.3, scroll, targetScroll)
        scroll = Mth.clamp(scroll, 0.0, maxScroll)

        // Update inner container position with interpolated scroll offset
        inner.setPosition(x, y - scroll.toInt())
        // Reposition child widgets to match new scroll position
        inner.calculateWidgetPositions()

        // Scissor clipping for content
        val contentWidth =
            if (needsScrollbar()) {
                width - SCROLLBAR_WIDTH - SCROLLBAR_PADDING
            } else {
                width
            }

        graphics.enableScissor(x, y, x + contentWidth, y + height)
        inner.render(graphics, mouseX, mouseY, tickDelta)
        graphics.disableScissor()

        // Render scrollbar
        if (needsScrollbar()) {
            renderScrollbar(graphics, mouseX, mouseY)
        }
    }

    private fun renderScrollbar(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        val scrollbarX = x + width - SCROLLBAR_WIDTH
        val scrollbarY = y

        // Scrollbar track
        graphics.fill(
            scrollbarX,
            scrollbarY,
            scrollbarX + SCROLLBAR_WIDTH,
            scrollbarY + height,
            TRACK_COLOR,
        )

        // Scrollbar handle
        val contentHeight = inner.height
        val handleHeight =
            ((height.toDouble() / contentHeight) * height)
                .toInt()
                .coerceAtLeast(MIN_HANDLE_HEIGHT)
        val maxHandleY = height - handleHeight
        val handleY = scrollbarY + ((scroll / getMaxScroll()) * maxHandleY).toInt()

        val handleHovered =
            mouseX in scrollbarX..(scrollbarX + SCROLLBAR_WIDTH) &&
                mouseY in handleY..(handleY + handleHeight)

        val handleColor =
            when {
                draggingScrollbar -> HANDLE_DRAGGING
                handleHovered -> HANDLE_HOVERED
                else -> HANDLE_NORMAL
            }

        graphics.fill(
            scrollbarX,
            handleY,
            scrollbarX + SCROLLBAR_WIDTH,
            handleY + handleHeight,
            handleColor,
        )
    }

    override fun isWidgetVisible(widget: GuiWidget): Boolean {
        // Viewport culling: only render widgets in view
        return widget.y + widget.height >= y && widget.y <= y + height
    }

    override fun handleClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (button != 0) return false

        // Check scrollbar click
        if (needsScrollbar()) {
            val scrollbarX = x + width - SCROLLBAR_WIDTH
            if (mouseX in scrollbarX..(scrollbarX + SCROLLBAR_WIDTH) &&
                mouseY in y..(y + height)
            ) {
                draggingScrollbar = true
                dragStartY = mouseY
                dragStartScroll = scroll
                return true
            }
        }

        // Forward to inner container
        return inner.handleClick(mouseX, mouseY, button)
    }

    override fun handleDrag(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (draggingScrollbar && button == 0) {
            val deltaY = mouseY - dragStartY
            val contentHeight = inner.height
            val handleHeight =
                ((height.toDouble() / contentHeight) * height)
                    .toInt()
                    .coerceAtLeast(MIN_HANDLE_HEIGHT)
            val maxHandleY = height - handleHeight

            val scrollDelta = (deltaY.toDouble() / maxHandleY) * getMaxScroll()
            targetScroll = (dragStartScroll + scrollDelta).coerceIn(0.0, getMaxScroll())
            return true
        }

        return inner.handleDrag(mouseX, mouseY, button)
    }

    override fun handleRelease(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false
            return true
        }
        return inner.handleRelease(mouseX, mouseY, button)
    }

    override fun handleScroll(
        mouseX: Int,
        mouseY: Int,
        amount: Double,
    ): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false

        // Try forwarding to inner container first
        if (inner.handleScroll(mouseX, mouseY, amount)) {
            return true
        }

        // Handle scrolling if scrollbar needed
        if (needsScrollbar()) {
            targetScroll = (targetScroll - amount * SCROLL_SPEED).coerceIn(0.0, getMaxScroll())
            return true
        }

        return false
    }

    override fun handleKeyPress(
        key: Int,
        scanCode: Int,
        modifiers: Int,
    ): Boolean = inner.handleKeyPress(key, scanCode, modifiers)

    override fun handleCharTyped(
        char: Char,
        modifiers: Int,
    ): Boolean = inner.handleCharTyped(char, modifiers)

    private fun needsScrollbar() = inner.height > maxHeight

    private fun getMaxScroll() = if (needsScrollbar()) (inner.height - maxHeight).toDouble() else 0.0

    companion object {
        private const val SCROLLBAR_WIDTH = 6
        private const val SCROLLBAR_PADDING = 2
        private const val MIN_HANDLE_HEIGHT = 20
        private const val SCROLL_SPEED = 23.0

        private const val TRACK_COLOR = 0xFF222222.toInt()
        private const val HANDLE_NORMAL = 0xFF555555.toInt()
        private const val HANDLE_HOVERED = 0xFF777777.toInt()
        private const val HANDLE_DRAGGING = 0xFF999999.toInt()
    }
}

/**
 * Extension function for creating scrollable containers.
 *
 * Example:
 * ```
 * vbox {
 *     for (i in 1..50) {
 *         add(LabelWidget("Item $i", 200))
 *     }
 * }.scrollable(maxHeight = 400)
 * ```
 */
fun <T : Container> T.scrollable(maxHeight: Int): ScrollableContainer<T> = ScrollableContainer(this, maxHeight)
