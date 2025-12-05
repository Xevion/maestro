package maestro.gui.widget

import maestro.gui.GuiColors
import maestro.gui.drawBorder
import maestro.gui.drawText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/**
 * Traditional checkbox widget for boolean settings.
 *
 * Displays a checkbox box with checkmark when checked, and a label.
 * Entire widget area is clickable for easy interaction.
 *
 * @param label Display label for the setting
 * @param initialChecked Initial checked state
 * @param onChange Callback fired when checkbox is toggled
 * @param width Total widget width
 */
class CheckboxWidget(
    private val label: String,
    initialChecked: Boolean,
    val defaultValue: Boolean,
    private val description: String? = null,
    private val onChange: (Boolean) -> Unit,
    width: Int,
) : GuiWidget(width, CHECKBOX_HEIGHT) {
    var checked: Boolean = initialChecked
        internal set
    private var lastMouseX: Int = 0
    private var lastMouseY: Int = 0

    override fun updateHover(
        mouseX: Int,
        mouseY: Int,
    ) {
        lastMouseX = mouseX
        lastMouseY = mouseY
        super.updateHover(mouseX, mouseY)
    }

    fun getLabelTooltip(): List<String>? {
        val lines = mutableListOf<String>()

        if (description != null && description.isNotEmpty()) {
            lines.add(description)
        }

        lines.add("Default: ${if (defaultValue) "Enabled" else "Disabled"}")

        return lines.ifEmpty { null }
    }

    fun getCheckboxTooltip(): List<String>? {
        val lines = mutableListOf<String>()

        val isModified = checked != defaultValue
        val currentState = if (checked) "Enabled" else "Disabled"

        if (isModified) {
            lines.add("Current: $currentState")
            lines.add("Default: ${if (defaultValue) "Enabled" else "Disabled"}")
        } else {
            lines.add("$currentState (Default)")
        }

        return lines
    }

    fun isMouseOverCheckbox(): Boolean {
        val checkboxX = x
        val checkboxY = y + (height - CHECKBOX_SIZE) / 2
        return lastMouseX >= checkboxX &&
            lastMouseX < checkboxX + CHECKBOX_SIZE &&
            lastMouseY >= checkboxY &&
            lastMouseY < checkboxY + CHECKBOX_SIZE
    }

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        tickDelta: Float,
    ) {
        val font = Minecraft.getInstance().font

        // Checkbox box position (vertically centered)
        val checkboxX = x
        val checkboxY = y + (height - CHECKBOX_SIZE) / 2

        // Label position (shifted down 2 pixels for better alignment)
        val labelX = checkboxX + CHECKBOX_SIZE + LABEL_SPACING
        val labelY = y + (height - font.lineHeight) / 2 + 2

        // Render checkbox background
        val backgroundColor =
            if (hovered) {
                GuiColors.CHECKBOX_BACKGROUND_HOVERED
            } else {
                GuiColors.CHECKBOX_BACKGROUND
            }
        graphics.fill(
            checkboxX,
            checkboxY,
            checkboxX + CHECKBOX_SIZE,
            checkboxY + CHECKBOX_SIZE,
            backgroundColor,
        )

        // Render checkbox border
        graphics.drawBorder(checkboxX, checkboxY, CHECKBOX_SIZE, CHECKBOX_SIZE, GuiColors.CHECKBOX_BORDER)

        // Render checkmark if checked
        if (checked) {
            val checkmarkPadding = CHECKBOX_PADDING
            graphics.fill(
                checkboxX + checkmarkPadding,
                checkboxY + checkmarkPadding,
                checkboxX + CHECKBOX_SIZE - checkmarkPadding,
                checkboxY + CHECKBOX_SIZE - checkmarkPadding,
                GuiColors.CHECKBOX_CHECKMARK,
            )
        }

        // Render label
        graphics.drawText(font, label, labelX, labelY, GuiColors.TEXT)
    }

    override fun handleClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false

        checked = !checked
        onChange(checked)
        return true
    }

    companion object {
        private const val CHECKBOX_HEIGHT = 20
        private const val CHECKBOX_SIZE = 10
        private const val CHECKBOX_PADDING = 2
        private const val LABEL_SPACING = 5
    }
}
