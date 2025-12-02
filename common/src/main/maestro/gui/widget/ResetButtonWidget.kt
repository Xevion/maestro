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

        val icon = "↻"
        val scale = 1.8f

        graphics.pose().pushPose()
        graphics.pose().scale(scale, scale, 1.0f)

        val iconWidth = font.width(icon)
        val iconHeight = font.lineHeight

        val iconX = (x / scale + (width / scale - iconWidth) / 2).toInt()
        val iconY = (y / scale + (height / scale - iconHeight) / 2).toInt()

        graphics.drawString(font, icon, iconX, iconY, GuiColors.TEXT, false)

        graphics.pose().popPose()
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
