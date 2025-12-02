package maestro.gui.widget

import maestro.api.Setting
import maestro.gui.GuiColors
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
                // Update slider's current value
                val valueClass = setting.getValueClass()
                val newValue =
                    when {
                        valueClass == java.lang.Integer::class.java -> (setting.value as Int).toDouble()
                        valueClass == java.lang.Long::class.java -> (setting.value as Long).toDouble()
                        valueClass == java.lang.Float::class.java -> (setting.value as Float).toDouble()
                        valueClass == java.lang.Double::class.java -> setting.value as Double
                        else -> controlWidget.currentValue
                    }
                controlWidget.currentValue = newValue
            }
            is CheckboxWidget -> {
                // Update checkbox's checked state
                if (setting.value is Boolean) {
                    controlWidget.checked = setting.value as Boolean
                }
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
            graphics.fill(
                x,
                y,
                x + INDICATOR_WIDTH,
                y + height,
                GuiColors.MODIFIED_INDICATOR,
            )
        }

        // Position control widget (offset by indicator width + spacing)
        val controlX = x + INDICATOR_WIDTH + INDICATOR_SPACING
        // Reserve space for reset button only if modified
        val resetButtonSpace = if (isModified) RESET_SPACING + ResetButtonWidget.BUTTON_SIZE else 0
        val controlWidth = width - INDICATOR_WIDTH - INDICATOR_SPACING - resetButtonSpace
        controlWidget.setPosition(controlX, y)

        // Update hover state for control widget
        controlWidget.updateHover(mouseX, mouseY)

        // Render control widget
        controlWidget.render(graphics, mouseX, mouseY, tickDelta)

        // Only render reset button if setting is modified
        if (isModified) {
            val resetX = x + width - ResetButtonWidget.BUTTON_SIZE
            val resetY = y + (height - ResetButtonWidget.BUTTON_SIZE) / 2
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
     * Forwards drag events to the control widget (for SliderWidget).
     */
    fun handleDrag(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (controlWidget is SliderWidget) {
            val result = controlWidget.handleDrag(mouseX, mouseY, button)
            if (result) {
                updateModifiedState()
            }
            return result
        }
        return false
    }

    /**
     * Forwards release events to the control widget (for SliderWidget).
     */
    fun handleRelease(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (controlWidget is SliderWidget) {
            return controlWidget.handleRelease(mouseX, mouseY, button)
        }
        return false
    }

    /**
     * Forwards scroll events to the control widget (for SliderWidget).
     */
    fun handleScroll(
        mouseX: Int,
        mouseY: Int,
        amount: Double,
    ): Boolean {
        if (controlWidget is SliderWidget) {
            val result = controlWidget.handleScroll(mouseX, mouseY, amount)
            if (result) {
                updateModifiedState()
            }
            return result
        }
        return false
    }

    companion object {
        private const val INDICATOR_WIDTH = 3 // Width of modified indicator bar
        private const val INDICATOR_SPACING = 4 // Space between indicator and control widget
        private const val RESET_SPACING = 4 // Space between control widget and reset button
    }
}
