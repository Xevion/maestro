package maestro.gui.widget

import maestro.gui.GuiColors
import maestro.gui.drawBorder
import maestro.gui.drawText
import maestro.renderer.text.TextRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/**
 * A clickable button widget with hover effects.
 *
 * Features:
 * - Hover state (color change on mouse over)
 * - Click callback execution
 * - Centered text rendering
 * - Native Minecraft styling
 */
class ButtonWidget(
    private val text: String,
    private val onClick: Runnable,
    width: Int,
) : GuiWidget(width, BUTTON_HEIGHT) {
    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        tickDelta: Float,
    ) {
        // Choose colors based on hover state
        val bgColor = if (hovered) GuiColors.BUTTON_HOVERED else GuiColors.BUTTON_NORMAL
        val borderColor = if (hovered) GuiColors.BUTTON_BORDER_HOVERED else GuiColors.BUTTON_BORDER

        // Draw button background
        graphics.fill(x, y, x + width, y + height, bgColor)

        // Draw button border
        graphics.drawBorder(x, y, width, height, borderColor)

        // Draw centered text
        val font = Minecraft.getInstance().font
        val textWidth = TextRenderer.getWidthForVanillaFont(text, font)
        val textX = x + (width - textWidth) / 2
        val textY = y + (height - font.lineHeight) / 2
        graphics.drawText(font, text, textX, textY, GuiColors.TEXT)
    }

    override fun handleClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        // Only handle left clicks
        if (button == 0 && isMouseOver(mouseX, mouseY)) {
            onClick.run()
            return true // Consumed
        }
        return false // Not consumed
    }

    companion object {
        private const val BUTTON_HEIGHT = 20
    }
}
