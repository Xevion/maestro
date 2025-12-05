package maestro.gui

import maestro.gui.core.Rect
import net.minecraft.client.gui.GuiGraphics

/**
 * Draws a border around a rectangle.
 *
 * @param x Left edge x-coordinate
 * @param y Top edge y-coordinate
 * @param width Rectangle width
 * @param height Rectangle height
 * @param color Border color (ARGB format)
 * @param thickness Border thickness in pixels (default 1)
 */
fun GuiGraphics.drawBorder(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    color: Int,
    thickness: Int = 1,
) {
    // Top
    fill(x, y, x + width, y + thickness, color)
    // Bottom
    fill(x, y + height - thickness, x + width, y + height, color)
    // Left
    fill(x, y + thickness, x + thickness, y + height - thickness, color)
    // Right
    fill(x + width - thickness, y + thickness, x + width, y + height - thickness, color)
}

/**
 * Draws a border around a [Rect].
 *
 * @param rect Rectangle to draw border around
 * @param color Border color (ARGB format)
 * @param thickness Border thickness in pixels (default 1)
 */
fun GuiGraphics.drawBorder(
    rect: Rect,
    color: Int,
    thickness: Int = 1,
) {
    drawBorder(rect.x, rect.y, rect.width, rect.height, color, thickness)
}

/**
 * Draws a filled rectangle with a border.
 *
 * Renders background first, then border on top.
 *
 * @param rect Rectangle bounds
 * @param backgroundColor Fill color (ARGB format)
 * @param borderColor Border color (ARGB format)
 * @param borderThickness Border thickness in pixels (default 1)
 */
fun GuiGraphics.drawPanel(
    rect: Rect,
    backgroundColor: Int,
    borderColor: Int,
    borderThickness: Int = 1,
) {
    // Background
    fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, backgroundColor)
    // Border
    drawBorder(rect, borderColor, borderThickness)
}

/**
 * Draws a filled rectangle with optional border.
 *
 * @param x Left edge x-coordinate
 * @param y Top edge y-coordinate
 * @param width Rectangle width
 * @param height Rectangle height
 * @param backgroundColor Fill color (ARGB format)
 * @param borderColor Border color (ARGB format), or null for no border
 * @param borderThickness Border thickness in pixels (default 1)
 */
fun GuiGraphics.drawPanel(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    backgroundColor: Int,
    borderColor: Int? = null,
    borderThickness: Int = 1,
) {
    // Background
    fill(x, y, x + width, y + height, backgroundColor)
    // Border (if specified)
    if (borderColor != null) {
        drawBorder(x, y, width, height, borderColor, borderThickness)
    }
}
