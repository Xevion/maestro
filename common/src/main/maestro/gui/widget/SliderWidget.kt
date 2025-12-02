package maestro.gui.widget

import maestro.gui.GuiColors
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.util.Mth
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Layout calculator for slider widget dimensions.
 *
 * Memoizes font width calculations and track positioning to avoid redundant
 * calculations across render(), handleClick(), handleDrag(), and handleScroll().
 *
 * @property labelWidth Width of the label text
 * @property valueText Formatted value string
 * @property valueWidth Width of the value text
 * @property trackStartX Left edge of the track
 * @property trackEndX Right edge of the track
 * @property trackWidth Total width of the track
 */
private class SliderLayout(
    font: Font,
    label: String,
    currentValue: Double,
    precision: Int,
    x: Int,
    width: Int,
) {
    val labelWidth: Int = font.width(label)
    val valueText: String = formatValue(currentValue, precision)
    val valueWidth: Int = font.width(valueText)

    val trackStartX: Int = x + labelWidth + LABEL_SPACING
    val trackEndX: Int = x + width - valueWidth - GuiColors.SLIDER_VALUE_SPACING
    val trackWidth: Int = trackEndX - trackStartX

    private fun formatValue(
        value: Double,
        precision: Int,
    ): String =
        if (precision == 0) {
            value.toInt().toString()
        } else {
            String.format("%.${precision}f", value)
        }

    companion object {
        private const val LABEL_SPACING = 5
    }
}

/**
 * Interactive slider widget for numeric settings.
 *
 * Supports click-to-position, drag, and mouse wheel scrolling.
 * Displays label on left, track with handle in middle, and value on right.
 *
 * @param label Display label for the setting
 * @param min Minimum value
 * @param max Maximum value
 * @param initialValue Initial value (clamped to range)
 * @param precision Number of decimal places (0 for integers, 1-3 for decimals)
 * @param onChange Callback fired on every value change
 * @param width Total widget width
 */
class SliderWidget(
    private val label: String,
    private val min: Double,
    private val max: Double,
    initialValue: Double,
    private val precision: Int = 0,
    private val onChange: (Double) -> Unit,
    width: Int,
) : GuiWidget(width, SLIDER_HEIGHT) {
    var currentValue: Double = Mth.clamp(initialValue, min, max)
        internal set

    var dragging: Boolean = false
        private set

    private var handleHovered: Boolean = false
    private var valueAtDragStart: Double = currentValue

    /**
     * Ghost handle for scroll-after-move behavior.
     *
     * When the user starts scrolling while hovering over the handle, we remember
     * the handle's position at that moment. This allows continued scrolling even
     * if the mouse moves away from the handle (as the handle moves while scrolling).
     *
     * Without this pattern, scrolling would stop as soon as the handle moved under
     * the cursor, making scroll-to-value awkward and requiring users to chase the handle.
     *
     * The "ghost" handle stays at the initial position until the mouse moves away
     * completely, then resets to track the real handle again.
     */
    private var scrollHandleX: Int = 0
    private var scrollHandleY: Int = 0
    private var scrollHandleWidth: Int = 0
    private var scrollHandleMouseOver: Boolean = false

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        tickDelta: Float,
    ) {
        val font = Minecraft.getInstance().font
        val layout = SliderLayout(font, label, currentValue, precision, x, width)

        val labelX = x
        val labelY = y + (height - font.lineHeight) / 2 + 1
        val valueX = x + width - layout.valueWidth
        val trackY = y + (height - TRACK_HEIGHT) / 2

        // Handle position and dimensions
        val handleX = calculateHandleX(layout.trackStartX, layout.trackWidth, HANDLE_WIDTH)
        val handleY = y + (height - TRACK_HEIGHT) / 2

        // Update handle hover state
        updateHandleHover(mouseX, mouseY, handleX, handleY, HANDLE_WIDTH, TRACK_HEIGHT)

        // Render label
        graphics.drawString(font, label, labelX, labelY, GuiColors.TEXT, false)

        // Render track
        graphics.fill(
            layout.trackStartX,
            trackY,
            layout.trackEndX,
            trackY + TRACK_HEIGHT,
            GuiColors.BUTTON_NORMAL,
        )

        // Render handle
        val handleColor =
            when {
                dragging -> GuiColors.SLIDER_HANDLE_DRAGGING
                handleHovered -> GuiColors.SLIDER_HANDLE_HOVERED
                else -> GuiColors.SLIDER_HANDLE_NORMAL
            }
        graphics.fill(handleX, handleY, handleX + HANDLE_WIDTH, handleY + TRACK_HEIGHT, handleColor)

        // Render value
        graphics.drawString(font, layout.valueText, valueX, labelY, GuiColors.TEXT, false)
    }

    override fun handleClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false

        valueAtDragStart = currentValue

        val layout = SliderLayout(Minecraft.getInstance().font, label, currentValue, precision, x, width)

        // Set value based on click position
        val valueWidthCalc = mouseX - (layout.trackStartX + HANDLE_WIDTH / 2)
        val normalizedValue = valueWidthCalc.toDouble() / (layout.trackWidth - HANDLE_WIDTH)
        setValue(normalizedValue * (max - min) + min)

        dragging = true
        return true
    }

    /**
     * Handles mouse drag events.
     */
    override fun handleDrag(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (!dragging || button != 0) return false

        val layout = SliderLayout(Minecraft.getInstance().font, label, currentValue, precision, x, width)

        val mouseOverX = mouseX >= layout.trackStartX + HANDLE_WIDTH / 2 && mouseX <= layout.trackEndX - HANDLE_WIDTH / 2

        if (mouseOverX) {
            val valueWidthCalc = mouseX - (layout.trackStartX + HANDLE_WIDTH / 2)
            val clampedValueWidth = Mth.clamp(valueWidthCalc, 0, layout.trackWidth - HANDLE_WIDTH)
            val normalizedValue = clampedValueWidth.toDouble() / (layout.trackWidth - HANDLE_WIDTH)
            setValue(normalizedValue * (max - min) + min)
        } else {
            // Clamp to min/max when dragging outside track
            if (currentValue > min && mouseX < layout.trackStartX + HANDLE_WIDTH / 2) {
                setValue(min)
            } else if (currentValue < max && mouseX > layout.trackEndX - HANDLE_WIDTH / 2) {
                setValue(max)
            }
        }

        return true
    }

    /**
     * Handles mouse release events.
     */
    override fun handleRelease(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (!dragging) return false
        dragging = false
        return true
    }

    /**
     * Handles mouse scroll events.
     * Implements ghost handle behavior for improved scroll interaction.
     */
    override fun handleScroll(
        mouseX: Int,
        mouseY: Int,
        amount: Double,
    ): Boolean {
        // When user starts to scroll over regular handle, remember its position
        if (!scrollHandleMouseOver && handleHovered) {
            val layout = SliderLayout(Minecraft.getInstance().font, label, currentValue, precision, x, width)
            val handleX = calculateHandleX(layout.trackStartX, layout.trackWidth, HANDLE_WIDTH)

            scrollHandleX = handleX
            scrollHandleY = y
            scrollHandleWidth = HANDLE_WIDTH
            scrollHandleMouseOver = true
        }

        // Check if mouse is still over ghost handle
        if (scrollHandleMouseOver) {
            scrollHandleMouseOver =
                mouseX >= scrollHandleX &&
                mouseX <= scrollHandleX + scrollHandleWidth &&
                mouseY >= scrollHandleY &&
                mouseY <= scrollHandleY + TRACK_HEIGHT
        }

        if (scrollHandleMouseOver) {
            // Scroll increment based on precision
            val increment =
                if (precision == 0) {
                    amount // Integer: scroll by 1
                } else {
                    amount * 0.05 * (max - min) // Decimal: scroll by 5% of range
                }
            setValue(currentValue + increment)
            return true
        }

        return false
    }

    /**
     * Sets the slider value, clamping to range and applying precision.
     */
    private fun setValue(value: Double) {
        val clampedValue = Mth.clamp(value, min, max)

        // Apply precision rounding
        val newValue =
            if (precision == 0) {
                clampedValue.roundToInt().toDouble()
            } else {
                val factor = 10.0.pow(precision)
                (clampedValue * factor).roundToInt() / factor
            }

        if (newValue != currentValue) {
            currentValue = newValue
            onChange(currentValue)
        }
    }

    /**
     * Calculates the handle X position based on current value.
     */
    private fun calculateHandleX(
        trackStartX: Int,
        trackWidth: Int,
        handleWidth: Int,
    ): Int {
        val normalizedValue = (currentValue - min) / (max - min)
        val valueWidth = normalizedValue * (trackWidth - handleWidth)
        return trackStartX + valueWidth.toInt()
    }

    /**
     * Updates handle hover state based on mouse position.
     */
    private fun updateHandleHover(
        mouseX: Int,
        mouseY: Int,
        handleX: Int,
        handleY: Int,
        handleWidth: Int,
        handleHeight: Int,
    ) {
        handleHovered =
            mouseX >= handleX &&
            mouseX <= handleX + handleWidth &&
            mouseY >= handleY &&
            mouseY <= handleY + handleHeight

        // Reset ghost handle when mouse moves away
        if (!scrollHandleMouseOver) {
            if (handleHovered) {
                scrollHandleX = handleX
                scrollHandleY = handleY
                scrollHandleWidth = handleWidth
            }
        }
    }

    companion object {
        private const val SLIDER_HEIGHT = 20
        private const val HANDLE_WIDTH = 8
        private const val TRACK_HEIGHT = 12
    }
}
