package maestro.gui

import maestro.rendering.text.TextColor
import maestro.rendering.text.TextRenderer
import maestro.utils.Loggers
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Extension functions for GuiGraphics that intercept text rendering calls
 * and route them to the custom font renderer.
 *
 * In Kotlin, extension functions have higher priority than Java methods when called
 * from Kotlin code, so all Maestro widgets automatically use the custom renderer.
 */

private val log = Loggers.Text.get()
private val firstRender = AtomicBoolean(true)

/**
 * Draws a string using the custom font renderer.
 *
 * Named drawText to avoid collision with GuiGraphics.drawString Java method.
 * Kotlin extension functions cannot shadow instance methods.
 *
 * @param scale Optional scale factor for the text (default 1.0). Use this instead of
 *              pose().scale() since our custom renderer bypasses the pose matrix.
 */
fun GuiGraphics.drawText(
    font: Font,
    text: String,
    x: Int,
    y: Int,
    color: Int,
    shadow: Boolean = false,
    scale: Float = 1.0f,
): Int {
    if (!TextRenderer.isInitialized()) {
        TextRenderer.init()

        // init() may fail silently - verify initialization succeeded
        if (!TextRenderer.isInitialized()) {
            if (firstRender.compareAndSet(true, false)) {
                log.atWarn().log("Custom renderer unavailable, using vanilla fallback")
            }
            return VanillaTextHelper.drawString(this, font, text, x, y, color, shadow)
        }
    }

    if (firstRender.compareAndSet(true, false)) {
        log.atInfo().log("First text render using custom renderer")
    }

    // Flush GuiGraphics batch before custom rendering to ensure proper z-order
    flush()

    TextRenderer.begin(requestedScale = scale.toDouble())
    val result =
        TextRenderer.render(
            text = text,
            x = x.toDouble(),
            y = y.toDouble(),
            color = TextColor.fromARGB(color),
            shadow = shadow,
        )
    TextRenderer.end()

    return result.toInt()
}

/**
 * Draws a Component using the custom font renderer.
 */
fun GuiGraphics.drawText(
    font: Font,
    text: Component,
    x: Int,
    y: Int,
    color: Int,
    shadow: Boolean = false,
    scale: Float = 1.0f,
): Int = drawText(font, text.string, x, y, color, shadow, scale)

/**
 * Draws a centered string using the custom font renderer.
 */
fun GuiGraphics.drawCenteredText(
    font: Font,
    text: String,
    centerX: Int,
    y: Int,
    color: Int,
): Int {
    val width = TextRenderer.getWidthForVanillaFont(text, font)
    return drawText(font, text, centerX - width / 2, y, color, shadow = true)
}

/**
 * Draws a centered Component using the custom font renderer.
 */
fun GuiGraphics.drawCenteredText(
    font: Font,
    text: Component,
    centerX: Int,
    y: Int,
    color: Int,
): Int {
    val textString = text.string
    val width = TextRenderer.getWidthForVanillaFont(textString, font)
    return drawText(font, textString, centerX - width / 2, y, color, shadow = true)
}
