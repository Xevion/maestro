package maestro.gui.widget

import maestro.Setting
import maestro.gui.GuiColors
import maestro.toBooleanOrNull
import maestro.toDoubleOrNull
import net.minecraft.client.gui.GuiGraphics

/**
 * Composite widget for displaying a setting with:
 * - Modified indicator (3px orange bar when value != default)
 * - Control widget (slider or checkbox)
 * - Reset button
 *
 * Layout: [Indicator(3px)] [Widget] [Reset(16px)]
 *
 * @param setting The setting this row represents
 * @param controlWidget The main control widget (slider or checkbox)
 * @param width Total row width
 */
class SettingRowWidget(
    private val setting: Setting<*>,
    private val controlWidget: GuiWidget,
    width: Int,
) : GuiWidget(width, controlWidget.height) {
    private val resetButton =
        ResetButtonWidget(
            onReset = {
                setting.reset()
                // Update control widget to reflect reset value
                updateControlWidget()
            },
        )

    private var isModified: Boolean = false

    init {
        updateModifiedState()
    }

    override fun getTooltip(): List<String>? {
        if (!hovered) return null

        return when (controlWidget) {
            is SliderWidget -> {
                if (controlWidget.isMouseOverLabel()) {
                    controlWidget.getLabelTooltip()
                } else {
                    controlWidget.getSliderTooltip()
                }
            }
            is CheckboxWidget -> {
                if (controlWidget.isMouseOverCheckbox()) {
                    controlWidget.getCheckboxTooltip()
                } else {
                    controlWidget.getLabelTooltip()
                }
            }
            else -> null
        }
    }

    /**
     * Updates the modified state by comparing current value to default.
     */
    private fun updateModifiedState() {
        isModified = setting.value != setting.defaultValue
    }

    /**
     * Updates the control widget's value to match the setting's current value.
     * Called after reset to refresh the display.
     */
    private fun updateControlWidget() {
        when (controlWidget) {
            is SliderWidget -> {
                setting.toDoubleOrNull()?.let { controlWidget.currentValue = it }
            }
            is CheckboxWidget -> {
                setting.toBooleanOrNull()?.let { controlWidget.checked = it }
            }
        }
        updateModifiedState()
    }

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        tickDelta: Float,
    ) {
        // Update modified state before rendering
        updateModifiedState()

        // Render modified indicator (3px orange bar on left)
        if (isModified) {
            // For sliders, align indicator with slider row (exclude small text above)
            val indicatorY =
                if (controlWidget is SliderWidget) {
                    y + controlWidget.sliderRowOffset
                } else {
                    y
                }
            graphics.fill(
                x,
                indicatorY,
                x + INDICATOR_WIDTH,
                y + height,
                GuiColors.MODIFIED_INDICATOR,
            )
        }

        // Position control widget (offset by indicator width + spacing)
        val controlX = x + INDICATOR_WIDTH + INDICATOR_SPACING
        // Always reserve space for reset button to ensure consistent sizing
        val resetButtonSpace = GuiColors.RESET_BUTTON_PADDING + ResetButtonWidget.BUTTON_SIZE
        val controlWidth = width - INDICATOR_WIDTH - INDICATOR_SPACING - resetButtonSpace

        // Apply calculated width and position
        controlWidget.width = controlWidth
        controlWidget.setPosition(controlX, y)

        // Update hover state for control widget
        controlWidget.updateHover(mouseX, mouseY)

        // Render control widget
        controlWidget.render(graphics, mouseX, mouseY, tickDelta)

        // Only render reset button if setting is modified
        if (isModified) {
            val resetX = x + width - ResetButtonWidget.BUTTON_SIZE
            // Align with bottom of slider track instead of centering in entire row
            val resetY = y + height - ResetButtonWidget.BUTTON_SIZE - 2
            resetButton.setPosition(resetX, resetY)
            resetButton.updateHover(mouseX, mouseY)
            resetButton.render(graphics, mouseX, mouseY, tickDelta)
        }
    }

    override fun handleClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        // Try reset button first (only if modified)
        if (isModified && resetButton.handleClick(mouseX, mouseY, button)) {
            return true
        }

        // Then try control widget
        if (controlWidget.handleClick(mouseX, mouseY, button)) {
            // Update modified state after control widget changes
            updateModifiedState()
            return true
        }

        return false
    }

    /**
     * Forwards drag events to the control widget.
     */
    override fun handleDrag(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        val result = controlWidget.handleDrag(mouseX, mouseY, button)
        if (result) {
            updateModifiedState()
        }
        return result
    }

    /**
     * Forwards release events to the control widget.
     */
    override fun handleRelease(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean = controlWidget.handleRelease(mouseX, mouseY, button)

    /**
     * Forwards scroll events to the control widget.
     */
    override fun handleScroll(
        mouseX: Int,
        mouseY: Int,
        amount: Double,
    ): Boolean {
        val result = controlWidget.handleScroll(mouseX, mouseY, amount)
        if (result) {
            updateModifiedState()
        }
        return result
    }

    /**
     * Forwards key press events to the control widget.
     */
    override fun handleKeyPress(
        key: Int,
        scanCode: Int,
        modifiers: Int,
    ): Boolean = controlWidget.handleKeyPress(key, scanCode, modifiers)

    /**
     * Forwards char typed events to the control widget.
     */
    override fun handleCharTyped(
        char: Char,
        modifiers: Int,
    ): Boolean = controlWidget.handleCharTyped(char, modifiers)

    companion object {
        private const val INDICATOR_WIDTH = 3 // Width of modified indicator bar
        private const val INDICATOR_SPACING = 4 // Space between indicator and control widget
    }
}
