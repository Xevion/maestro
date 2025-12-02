package maestro.gui.widget

import maestro.gui.GuiColors
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/**
 * Tab navigation widget for switching between different categories/views.
 *
 * Renders a horizontal tab bar with clickable tabs. The active tab has
 * a lighter background, white text, and no bottom border (connected to content).
 *
 * @param tabs List of tab labels
 * @param activeTabIndex Initially active tab (0-indexed)
 * @param onTabChange Callback when a different tab is selected
 * @param width Total widget width
 */
class TabWidget(
    private val tabs: List<String>,
    activeTabIndex: Int = 0,
    private val onTabChange: (Int) -> Unit,
    width: Int,
) : GuiWidget(width, TAB_HEIGHT) {
    var activeTab: Int = activeTabIndex
        private set

    private val tabWidths = mutableListOf<Int>()
    private val tabPositions = mutableListOf<Int>()

    init {
        calculateTabLayout()
    }

    /**
     * Calculates the width and X position of each tab based on text width.
     */
    private fun calculateTabLayout() {
        val font = Minecraft.getInstance().font
        tabWidths.clear()
        tabPositions.clear()

        var currentX = x
        for (tab in tabs) {
            val textWidth = font.width(tab)
            val tabWidth = textWidth + (TAB_PADDING * 2)

            tabPositions.add(currentX)
            tabWidths.add(tabWidth)
            currentX += tabWidth
        }
    }

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        tickDelta: Float,
    ) {
        val font = Minecraft.getInstance().font

        // Recalculate layout if position changed (e.g., after being added to panel)
        if (tabPositions.isEmpty() || tabPositions[0] != x) {
            calculateTabLayout()
        }

        for (i in tabs.indices) {
            val tabX = tabPositions[i]
            val tabWidth = tabWidths[i]
            val isActive = i == activeTab
            val isHovered = isMouseOverTab(mouseX, mouseY, i)

            // Background color
            val bgColor =
                when {
                    isActive -> GuiColors.BUTTON_HOVERED // Active tab is lighter
                    isHovered -> 0xFF444444.toInt() // Hover slightly lighter than normal
                    else -> GuiColors.BUTTON_NORMAL
                }

            graphics.fill(tabX, y, tabX + tabWidth, y + height, bgColor)

            // Borders (top, left, right, and bottom for inactive tabs)
            val borderColor = GuiColors.BUTTON_BORDER

            // Top border
            graphics.fill(tabX, y, tabX + tabWidth, y + 1, borderColor)
            // Left border
            graphics.fill(tabX, y, tabX + 1, y + height, borderColor)
            // Right border
            graphics.fill(tabX + tabWidth - 1, y, tabX + tabWidth, y + height, borderColor)

            // Bottom border (only for inactive tabs - active connects to content)
            if (!isActive) {
                graphics.fill(tabX, y + height - 1, tabX + tabWidth, y + height, borderColor)
            }

            // Text (white for active, gray for inactive)
            val textColor = if (isActive) GuiColors.TEXT else GuiColors.TEXT_SECONDARY
            val textX = tabX + TAB_PADDING
            val textY = y + (height - font.lineHeight) / 2

            graphics.drawString(font, tabs[i], textX, textY, textColor, false)
        }
    }

    override fun handleClick(
        mouseX: Int,
        mouseY: Int,
        button: Int,
    ): Boolean {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false

        // Find which tab was clicked
        for (i in tabs.indices) {
            if (isMouseOverTab(mouseX, mouseY, i)) {
                if (i != activeTab) {
                    activeTab = i
                    onTabChange(i)
                }
                return true
            }
        }

        return false
    }

    /**
     * Checks if the mouse is over a specific tab.
     *
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param tabIndex Index of the tab to check
     * @return true if mouse is over the specified tab
     */
    private fun isMouseOverTab(
        mouseX: Int,
        mouseY: Int,
        tabIndex: Int,
    ): Boolean {
        if (tabIndex < 0 || tabIndex >= tabs.size) return false
        if (tabPositions.isEmpty()) return false

        val tabX = tabPositions[tabIndex]
        val tabWidth = tabWidths[tabIndex]

        return mouseX >= tabX &&
            mouseX < tabX + tabWidth &&
            mouseY >= y &&
            mouseY < y + height
    }

    companion object {
        const val TAB_HEIGHT = 24
        private const val TAB_PADDING = 8 // Horizontal padding inside each tab
    }
}
