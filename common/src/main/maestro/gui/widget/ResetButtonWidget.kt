package maestro.gui.widget

import maestro.gui.GuiColors
import maestro.gui.drawBorder
import maestro.gui.drawText
import maestro.renderer.text.TextRenderer
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
        val borderColor = if (hovered) GuiColors.BUTTON_BORDER_HOVERED else GuiColors.BUTTON_BORDER
        graphics.drawBorder(x, y, width, height, borderColor)

        val icon = "↻"
        val iconScale = 1.8f

        // Get scaled dimensions for centering calculation
        val iconWidth = TextRenderer.getWidthForVanillaFont(icon, font, iconScale)
        val iconHeight = (font.lineHeight * iconScale).toInt()

        val iconX = x + (width - iconWidth) / 2
        val iconY = y + (height - iconHeight) / 2

        graphics.drawText(font, icon, iconX, iconY, GuiColors.TEXT, scale = iconScale)
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
