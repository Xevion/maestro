package maestro.gui.widget

import maestro.gui.GuiColors
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/**
 * Small button widget with a reset icon ("↻") for resetting settings to default values.
 *
 * @param onReset Callback fired when the reset button is clicked
 */
class ResetButtonWidget(
    private val onReset: () -> Unit,
) : GuiWidget(BUTTON_SIZE, BUTTON_SIZE) {
    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        tickDelta: Float,
    ) {
        val font = Minecraft.getInstance().font

        // Background color (hover state)
        val bgColor =
            if (hovered) {
                GuiColors.BUTTON_HOVERED
            } else {
                GuiColors.BUTTON_NORMAL
            }

        graphics.fill(x, y, x + width, y + height, bgColor)

        // Border
        val borderColor =
            if (hovered) {
                GuiColors.BUTTON_BORDER_HOVERED
            } else {
                GuiColors.BUTTON_BORDER
            }
        graphics.fill(x, y, x + width, y + 1, borderColor) // Top
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor) // Bottom
        graphics.fill(x, y + 1, x + 1, y + height - 1, borderColor) // Left
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, borderColor) // Right

        // Reset icon "↻" centered
        val icon = "↻"
        val iconWidth = font.width(icon)
        val iconX = x + (width - iconWidth) / 2
        val iconY = y + (height - font.lineHeight) / 2

        graphics.drawString(font, icon, iconX, iconY, GuiColors.TEXT, false)
    }

    override fun handleClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (button == 0 && isMouseOver(mouseX, mouseY)) {
            onReset()
            return true
        }
        return false
    }

    companion object {
        const val BUTTON_SIZE = 16 // 16x16 reset button
    }
}
