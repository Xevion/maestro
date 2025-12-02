package maestro.gui.widget

import maestro.gui.GuiColors
import maestro.gui.utils.drawBorder
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.lwjgl.glfw.GLFW

/**
 * Text input widget for search/filter functionality.
 *
 * Features:
 * - Placeholder text when empty
 * - Blinking cursor when focused
 * - Keyboard input (type, backspace, delete, arrows, home, end)
 * - Focus management (click to focus, ESC to unfocus)
 * - Text selection and cursor positioning
 *
 * @param placeholder Placeholder text shown when empty
 * @param onTextChange Callback fired when text changes
 * @param width Total widget width
 */
class TextInputWidget(
    private val placeholder: String,
    private val onTextChange: (String) -> Unit,
    width: Int,
) : GuiWidget(width, TEXT_INPUT_HEIGHT) {
    var text: String = ""
        private set

    var focused: Boolean = false
        private set

    private var cursorPosition: Int = 0
    private var cursorBlinkTicks: Int = 0
    private var showCursor: Boolean = true

    /**
     * Updates cursor blink animation (called each tick).
     */
    fun tick() {
        if (focused) {
            cursorBlinkTicks++
            if (cursorBlinkTicks >= CURSOR_BLINK_RATE) {
                cursorBlinkTicks = 0
                showCursor = !showCursor
            }
        }
    }

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        tickDelta: Float,
    ) {
        val font = Minecraft.getInstance().font

        // Background color (lighter when focused)
        val bgColor =
            if (focused) {
                0xFF444444.toInt()
            } else {
                GuiColors.BUTTON_NORMAL
            }

        graphics.fill(x, y, x + width, y + height, bgColor)

        // Border
        val borderColor =
            if (focused) {
                GuiColors.BUTTON_BORDER_HOVERED
            } else {
                GuiColors.BUTTON_BORDER
            }

        graphics.drawBorder(x, y, width, height, borderColor)

        // Text or placeholder
        val displayText = if (text.isEmpty() && !focused) placeholder else text
        val textColor =
            if (text.isEmpty() && !focused) {
                GuiColors.TEXT_SECONDARY
            } else {
                GuiColors.TEXT
            }

        val textX = x + TEXT_PADDING
        val textY = y + (height - font.lineHeight) / 2

        // Enable scissor to clip text that's too long
        graphics.enableScissor(x + TEXT_PADDING, y, x + width - TEXT_PADDING, y + height)
        graphics.drawString(font, displayText, textX, textY, textColor, false)

        // Render cursor if focused
        if (focused && showCursor && text.isNotEmpty()) {
            val textBeforeCursor = text.substring(0, cursorPosition.coerceIn(0, text.length))
            val cursorX = textX + font.width(textBeforeCursor)
            graphics.fill(cursorX, textY, cursorX + 1, textY + font.lineHeight, GuiColors.TEXT)
        }

        graphics.disableScissor()
    }

    override fun handleClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (button != 0) return false

        if (isMouseOver(mouseX, mouseY)) {
            // Click inside - focus
            focused = true
            cursorBlinkTicks = 0
            showCursor = true

            // Position cursor based on click position
            val font = Minecraft.getInstance().font
            val clickX = mouseX - (x + TEXT_PADDING)
            var currentWidth = 0

            for (i in text.indices) {
                val charWidth = font.width(text.substring(i, i + 1))
                if (currentWidth + charWidth / 2 > clickX) {
                    cursorPosition = i
                    return true
                }
                currentWidth += charWidth
            }

            cursorPosition = text.length
            return true
        } else {
            // Click outside - unfocus
            focused = false
            return false
        }
    }

    /**
     * Handles keyboard input for text editing.
     *
     * @param key GLFW key code
     * @param scanCode Platform-specific scan code
     * @param modifiers Key modifiers (shift, ctrl, alt)
     * @return true if key was consumed
     */
    override fun handleKeyPress(
        key: Int,
        scanCode: Int,
        modifiers: Int,
    ): Boolean {
        if (!focused) return false

        when (key) {
            GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursorPosition > 0 && text.isNotEmpty()) {
                    text = text.substring(0, cursorPosition - 1) + text.substring(cursorPosition)
                    cursorPosition--
                    onTextChange(text)
                    resetCursorBlink()
                }
                return true
            }

            GLFW.GLFW_KEY_DELETE -> {
                if (cursorPosition < text.length) {
                    text = text.substring(0, cursorPosition) + text.substring(cursorPosition + 1)
                    onTextChange(text)
                    resetCursorBlink()
                }
                return true
            }

            GLFW.GLFW_KEY_LEFT -> {
                if (cursorPosition > 0) {
                    cursorPosition--
                    resetCursorBlink()
                }
                return true
            }

            GLFW.GLFW_KEY_RIGHT -> {
                if (cursorPosition < text.length) {
                    cursorPosition++
                    resetCursorBlink()
                }
                return true
            }

            GLFW.GLFW_KEY_HOME -> {
                cursorPosition = 0
                resetCursorBlink()
                return true
            }

            GLFW.GLFW_KEY_END -> {
                cursorPosition = text.length
                resetCursorBlink()
                return true
            }

            GLFW.GLFW_KEY_ESCAPE -> {
                focused = false
                return true
            }

            else -> {
                // Consume all other keys when focused to prevent hotkey activation
                return true
            }
        }
    }

    /**
     * Handles character input (typing letters, numbers, symbols).
     *
     * @param char Character typed
     * @param modifiers Key modifiers
     * @return true if character was consumed
     */
    override fun handleCharTyped(
        char: Char,
        modifiers: Int,
    ): Boolean {
        if (!focused) return false

        // Filter out control characters
        if (char < ' ' || char.code == 127) return false

        // Insert character at cursor position
        text = text.substring(0, cursorPosition) + char + text.substring(cursorPosition)
        cursorPosition++
        onTextChange(text)
        resetCursorBlink()

        return true
    }

    /**
     * Resets the cursor blink animation to make it visible.
     */
    private fun resetCursorBlink() {
        cursorBlinkTicks = 0
        showCursor = true
    }

    /**
     * Clears the text input.
     */
    fun clear() {
        text = ""
        cursorPosition = 0
        onTextChange(text)
        resetCursorBlink()
    }

    companion object {
        const val TEXT_INPUT_HEIGHT = 20
        private const val TEXT_PADDING = 4 // Horizontal padding inside input
        private const val CURSOR_BLINK_RATE = 30 // Ticks between blink toggles (slower blink)
    }
}
