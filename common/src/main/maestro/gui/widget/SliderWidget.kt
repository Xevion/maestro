package maestro.gui.widget

import maestro.gui.GuiColors
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.util.Mth
import kotlin.math.roundToInt

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

    // Ghost handle for scroll-after-move behavior (Meteor Client pattern)
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

        // Calculate layout dimensions
        val labelWidth = font.width(label)
        val valueText = formatValue(currentValue)
        val valueWidth = font.width(valueText)

        val labelX = x
        val labelY = y + (height - font.lineHeight) / 2

        val valueX = x + width - valueWidth
        val valueY = labelY

        // Track area between label and value
        val trackStartX = labelX + labelWidth + LABEL_SPACING
        val trackEndX = valueX - LABEL_SPACING
        val trackWidth = trackEndX - trackStartX
        val trackY = y + (height - TRACK_HEIGHT) / 2

        // Handle position and dimensions (same height as track for boxy look)
        val handleWidth = HANDLE_WIDTH
        val handleX = calculateHandleX(trackStartX, trackWidth, handleWidth)
        val handleY = y + (height - TRACK_HEIGHT) / 2

        // Update handle hover state
        updateHandleHover(mouseX, mouseY, handleX, handleY, handleWidth, TRACK_HEIGHT)

        // Render label
        graphics.drawString(font, label, labelX, labelY, GuiColors.TEXT, false)

        // Render track
        graphics.fill(
            trackStartX,
            trackY,
            trackEndX,
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
        graphics.fill(handleX, handleY, handleX + handleWidth, handleY + TRACK_HEIGHT, handleColor)

        // Render value
        graphics.drawString(font, valueText, valueX, valueY, GuiColors.TEXT, false)
    }

    override fun handleClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false

        valueAtDragStart = currentValue

        // Calculate track bounds
        val font = Minecraft.getInstance().font
        val labelWidth = font.width(label)
        val valueText = formatValue(currentValue)
        val valueWidth = font.width(valueText)

        val trackStartX = x + labelWidth + LABEL_SPACING
        val trackEndX = x + width - valueWidth - LABEL_SPACING
        val trackWidth = trackEndX - trackStartX
        val handleWidth = HANDLE_WIDTH

        // Set value based on click position
        val valueWidthCalc = mouseX - (trackStartX + handleWidth / 2)
        val normalizedValue = valueWidthCalc.toDouble() / (trackWidth - handleWidth)
        setValue(normalizedValue * (max - min) + min)

        dragging = true
        return true
    }

    /**
     * Handles mouse drag events. Called by GuiPanel.
     */
    fun handleDrag(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (!dragging || button != 0) return false

        // Calculate track bounds
        val font = Minecraft.getInstance().font
        val labelWidth = font.width(label)
        val valueText = formatValue(currentValue)
        val valueWidth = font.width(valueText)

        val trackStartX = x + labelWidth + LABEL_SPACING
        val trackEndX = x + width - valueWidth - LABEL_SPACING
        val trackWidth = trackEndX - trackStartX
        val handleWidth = HANDLE_WIDTH

        val mouseOverX = mouseX >= trackStartX + handleWidth / 2 && mouseX <= trackEndX - handleWidth / 2

        if (mouseOverX) {
            val valueWidthCalc = mouseX - (trackStartX + handleWidth / 2)
            val clampedValueWidth = Mth.clamp(valueWidthCalc, 0, trackWidth - handleWidth)
            val normalizedValue = clampedValueWidth.toDouble() / (trackWidth - handleWidth)
            setValue(normalizedValue * (max - min) + min)
        } else {
            // Clamp to min/max when dragging outside track
            if (currentValue > min && mouseX < trackStartX + handleWidth / 2) {
                setValue(min)
            } else if (currentValue < max && mouseX > trackEndX - handleWidth / 2) {
                setValue(max)
            }
        }

        return true
    }

    /**
     * Handles mouse release events. Called by GuiPanel.
     */
    fun handleRelease(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (!dragging) return false
        dragging = false
        return true
    }

    /**
     * Handles mouse scroll events. Called by GuiPanel.
     * Implements ghost handle behavior from Meteor Client.
     */
    fun handleScroll(
        mouseX: Int,
        mouseY: Int,
        amount: Double,
    ): Boolean {
        // When user starts to scroll over regular handle, remember its position
        if (!scrollHandleMouseOver && handleHovered) {
            val font = Minecraft.getInstance().font
            val labelWidth = font.width(label)
            val valueText = formatValue(currentValue)
            val valueWidth = font.width(valueText)

            val trackStartX = x + labelWidth + LABEL_SPACING
            val trackWidth = (x + width - valueWidth - LABEL_SPACING) - trackStartX
            val handleWidth = HANDLE_WIDTH
            val handleX = calculateHandleX(trackStartX, trackWidth, handleWidth)

            scrollHandleX = handleX
            scrollHandleY = y
            scrollHandleWidth = handleWidth
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
                val factor = Math.pow(10.0, precision.toDouble())
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

    /**
     * Formats the value for display based on precision.
     */
    private fun formatValue(value: Double): String =
        if (precision == 0) {
            value.toInt().toString()
        } else {
            String.format("%.${precision}f", value)
        }

    companion object {
        private const val SLIDER_HEIGHT = 20
        private const val HANDLE_WIDTH = 8
        private const val TRACK_HEIGHT = 12
        private const val LABEL_SPACING = 5
    }
}
