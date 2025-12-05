package maestro.gui

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
    private const val FONT_SCALE = 0.95f

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

        // Calculate scaled dimensions
        val maxLineWidth = (lines.maxOf { font.width(it) } * FONT_SCALE).toInt()
        val scaledLineHeight = (font.lineHeight * FONT_SCALE).toInt()
        val scaledLineSpacing = (LINE_SPACING * FONT_SCALE).toInt()
        val tooltipWidth = maxLineWidth + TOOLTIP_PADDING * 2
        val tooltipHeight = lines.size * scaledLineHeight + (lines.size - 1) * scaledLineSpacing + TOOLTIP_PADDING * 2

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

        // Render text lines with scaling
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate((x + TOOLTIP_PADDING).toFloat(), (y + TOOLTIP_PADDING).toFloat(), 0f)
        pose.scale(FONT_SCALE, FONT_SCALE, 1.0f)

        var textY = 0
        for (line in lines) {
            graphics.drawString(
                font,
                line,
                0,
                textY,
                GuiColors.TEXT,
                false,
            )
            textY += font.lineHeight + LINE_SPACING
        }

        pose.popPose()
    }
}
