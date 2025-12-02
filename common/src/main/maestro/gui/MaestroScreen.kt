package maestro.gui

import maestro.Agent
import maestro.gui.panel.GuiPanel
import maestro.gui.panel.MainMenuPanel
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.ArrayDeque

/**
 * Main GUI screen for Maestro, using Screen-based architecture for proper input handling.
 *
 * Features:
 * - Center-screen panel rendering with background and border
 * - Panel stack navigation (push/pop)
 * - Native mouse and keyboard input handling
 * - Toggle with GRAVE key, ESC also closes
 */
class MaestroScreen(
    private val agent: Agent,
) : Screen(Component.literal("Maestro")) {
    private val panelStack = ArrayDeque<GuiPanel>()

    init {
        panelStack.push(MainMenuPanel(this))
    }

    /**
     * Gets the Agent instance associated with this screen.
     *
     * @return The agent
     */
    fun getAgent(): Agent = agent

    /**
     * Pushes a new panel onto the stack, making it the active panel.
     *
     * @param panel The panel to push
     */
    fun pushPanel(panel: GuiPanel) {
        panelStack.push(panel)
    }

    /**
     * Pops the current panel from the stack, returning to the previous panel.
     *
     * Does nothing if there's only one panel (the main menu).
     */
    fun popPanel() {
        if (panelStack.size > 1) {
            panelStack.pop()
        }
    }

    override fun renderBackground(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        // Don't render blur background when in-game (world != null)
        if (minecraft?.level == null) {
            super.renderBackground(graphics, mouseX, mouseY, delta)
        }
    }

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        // Render background (only panorama if no world loaded)
        renderBackground(graphics, mouseX, mouseY, delta)

        val currentPanel = panelStack.peek() ?: return

        // Calculate panel dimensions
        val panelWidth = currentPanel.getWidth()
        val panelHeight = currentPanel.getHeight()

        // Center panel on screen
        val panelX = (width - panelWidth) / 2
        val panelY = (height - panelHeight) / 2

        // Draw panel background
        graphics.fill(
            panelX,
            panelY,
            panelX + panelWidth,
            panelY + panelHeight,
            GuiColors.PANEL_BACKGROUND,
        )

        // Draw panel border
        drawBorder(graphics, panelX, panelY, panelWidth, panelHeight)

        // Render panel content (inside padding)
        currentPanel.render(
            graphics,
            panelX + GuiColors.PADDING,
            panelY + GuiColors.PADDING,
            mouseX,
            mouseY,
            delta,
        )

        super.render(graphics, mouseX, mouseY, delta)
    }

    /**
     * Draws a border around the panel.
     *
     * @param graphics GuiGraphics for rendering
     * @param x Panel X coordinate
     * @param y Panel Y coordinate
     * @param width Panel width
     * @param height Panel height
     */
    private fun drawBorder(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        // Top
        graphics.fill(x, y, x + width, y + 1, GuiColors.BORDER)
        // Bottom
        graphics.fill(x, y + height - 1, x + width, y + height, GuiColors.BORDER)
        // Left
        graphics.fill(x, y + 1, x + 1, y + height - 1, GuiColors.BORDER)
        // Right
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, GuiColors.BORDER)
    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        val currentPanel = panelStack.peek()
        if (currentPanel != null) {
            if (currentPanel.handleMouseClick(mouseX.toInt(), mouseY.toInt(), button)) {
                return true // Click consumed by panel
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(
        mouseX: Double,
        mouseY: Double,
        button: Int,
        dragX: Double,
        dragY: Double,
    ): Boolean {
        val currentPanel = panelStack.peek()
        if (currentPanel != null) {
            if (currentPanel.handleMouseDrag(mouseX.toInt(), mouseY.toInt(), button)) {
                return true // Drag consumed by panel
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        val currentPanel = panelStack.peek()
        if (currentPanel != null) {
            if (currentPanel.handleMouseRelease(mouseX.toInt(), mouseY.toInt(), button)) {
                return true // Release consumed by panel
            }
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        val currentPanel = panelStack.peek()
        if (currentPanel != null) {
            if (currentPanel.handleMouseScroll(mouseX.toInt(), mouseY.toInt(), verticalAmount)) {
                return true // Scroll consumed by panel
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(
        key: Int,
        scanCode: Int,
        modifiers: Int,
    ): Boolean {
        val currentPanel = panelStack.peek()
        if (currentPanel != null) {
            if (currentPanel.handleKeyPress(key, scanCode, modifiers)) {
                return true // Key press consumed by panel
            }
        }
        return super.keyPressed(key, scanCode, modifiers)
    }

    override fun charTyped(
        char: Char,
        modifiers: Int,
    ): Boolean {
        val currentPanel = panelStack.peek()
        if (currentPanel != null) {
            if (currentPanel.handleCharTyped(char, modifiers)) {
                return true // Char typed consumed by panel
            }
        }
        return super.charTyped(char, modifiers)
    }

    override fun shouldCloseOnEsc(): Boolean = true // ESC closes the screen

    override fun isPauseScreen(): Boolean = false // Don't pause game - user wants to watch bot
}
