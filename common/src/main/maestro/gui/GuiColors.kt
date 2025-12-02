package maestro.gui

/** Color constants for Maestro's GUI system. */
object GuiColors {
    // Panel colors
    const val PANEL_BACKGROUND = 0xD0000000.toInt() // Semi-transparent black
    const val BORDER = 0xFF888888.toInt() // Gray

    // Text colors
    const val TEXT = 0xFFFFFFFF.toInt() // White
    const val TEXT_SECONDARY = 0xFFAAAAAA.toInt() // Light gray

    // Button colors
    const val BUTTON_NORMAL = 0xFF333333.toInt() // Dark gray
    const val BUTTON_HOVERED = 0xFF555555.toInt() // Medium gray
    const val BUTTON_BORDER = 0xFF888888.toInt() // Gray
    const val BUTTON_BORDER_HOVERED = 0xFFFFFFFF.toInt() // White

    // Separator color
    const val SEPARATOR = 0xFF444444.toInt() // Dark gray

    // Slider colors
    const val SLIDER_HANDLE_NORMAL = 0xFF777777.toInt() // Medium-light gray
    const val SLIDER_HANDLE_HOVERED = 0xFF999999.toInt() // Light gray
    const val SLIDER_HANDLE_DRAGGING = 0xFFBBBBBB.toInt() // Very light gray

    // Checkbox colors
    const val CHECKBOX_BACKGROUND = 0xFF333333.toInt() // Dark gray
    const val CHECKBOX_BACKGROUND_HOVERED = 0xFF555555.toInt() // Medium gray
    const val CHECKBOX_CHECKMARK = 0xFFFFFFFF.toInt() // White
    const val CHECKBOX_BORDER = 0xFF888888.toInt() // Gray

    // Modified indicator color
    const val MODIFIED_INDICATOR = 0xFFFF8800.toInt() // Orange

    // Spacing
    const val PADDING = 10
    const val WIDGET_SPACING = 5
}
