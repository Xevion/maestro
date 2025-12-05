package maestro.gui.widget

import maestro.gui.GuiColors
import maestro.gui.drawText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/**
 * A static text label widget.
 *
 * Features:
 * - Non-interactive (no hover or click)
 * - Rendered with gray text color
 * - Smaller height than buttons
 * - Used for section headers
 */
class LabelWidget(
    private val text: String,
    width: Int,
) : GuiWidget(width, LABEL_HEIGHT) {
    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        tickDelta: Float,
    ) {
        val font = Minecraft.getInstance().font
        graphics.drawText(font, text, x, y, GuiColors.TEXT_SECONDARY)
    }

    override fun handleClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        // Labels don't handle clicks
        return false
    }

    companion object {
        private const val LABEL_HEIGHT = 12
    }
}
