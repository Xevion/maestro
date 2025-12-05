package maestro.gui

import maestro.renderer.text.TextRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/**
 * Utility for rendering tooltips in the GUI.
 *
 * Tooltips are rendered with a semi-transparent background, border, and text lines.
 * Positioning automatically adjusts to stay within screen bounds.
 */
object Tooltip {
    private const val TOOLTIP_PADDING = 6
    private const val LINE_SPACING = 2
    private const val CURSOR_OFFSET_X = 12
    private const val CURSOR_OFFSET_Y = -12

    /**
     * Renders a tooltip with multiple text lines.
     *
     * @param graphics Graphics context
     * @param lines Text lines to display
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param screenWidth Screen width for boundary clamping
     * @param screenHeight Screen height for boundary clamping
     */
    fun render(
        graphics: GuiGraphics,
        lines: List<String>,
        mouseX: Int,
        mouseY: Int,
        screenWidth: Int,
        screenHeight: Int,
    ) {
        if (lines.isEmpty()) return

        val font = Minecraft.getInstance().font
        val lineHeight = font.lineHeight

        // Calculate dimensions using custom renderer's width calculation
        // Clamp width to screen if tooltip content is too wide
        val maxLineWidth = lines.maxOf { TextRenderer.getWidthForVanillaFont(it, font) }
        val tooltipWidth = minOf(maxLineWidth + TOOLTIP_PADDING * 2, screenWidth)
        val tooltipHeight =
            minOf(
                lines.size * lineHeight + (lines.size - 1) * LINE_SPACING + TOOLTIP_PADDING * 2,
                screenHeight,
            )

        // Calculate position (offset from cursor, clamped to screen)
        var x = mouseX + CURSOR_OFFSET_X
        var y = mouseY + CURSOR_OFFSET_Y

        // Clamp to screen bounds
        if (x + tooltipWidth > screenWidth) {
            x = mouseX - tooltipWidth - CURSOR_OFFSET_X
        }
        if (y + tooltipHeight > screenHeight) {
            y = screenHeight - tooltipHeight
        }
        if (x < 0) x = 0
        if (y < 0) y = 0

        // Render background
        graphics.fill(x, y, x + tooltipWidth, y + tooltipHeight, GuiColors.PANEL_BACKGROUND)

        // Render border
        graphics.renderOutline(x, y, tooltipWidth, tooltipHeight, GuiColors.BORDER)

        // Render text lines
        var textY = y + TOOLTIP_PADDING
        for (line in lines) {
            graphics.drawText(
                font,
                line,
                x + TOOLTIP_PADDING,
                textY,
                GuiColors.TEXT,
                false,
            )
            textY += lineHeight + LINE_SPACING
        }
    }
}
