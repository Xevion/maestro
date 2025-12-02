package maestro.gui.widget

import maestro.gui.GuiColors
import net.minecraft.client.gui.GuiGraphics

/**
 * A horizontal separator line widget.
 *
 * Features:
 * - Non-interactive (no hover or click)
 * - Renders as a thin horizontal line
 * - Used for visual grouping
 */
class SeparatorWidget(
    width: Int,
) : GuiWidget(width, SEPARATOR_HEIGHT) {
    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        tickDelta: Float,
    ) {
        graphics.fill(x, y, x + width, y + height, GuiColors.SEPARATOR)
    }

    override fun handleClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        // Separators don't handle clicks
        return false
    }

    companion object {
        private const val SEPARATOR_HEIGHT = 1
    }
}
