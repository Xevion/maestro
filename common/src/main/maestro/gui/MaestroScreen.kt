package maestro.gui

import maestro.Agent
import maestro.gui.core.Container
import maestro.gui.core.Insets
import maestro.gui.core.Rect
import maestro.gui.panel.MainMenuPanel
import maestro.gui.utils.drawPanel
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.ArrayDeque
import kotlin.math.min

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
    private val panelStack = ArrayDeque<Container>()

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
    fun pushPanel(panel: Container) {
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

        // Two-phase layout
        currentPanel.calculateSize()

        // Calculate panel insets (border + padding)
        val panelInsets = Insets(all = 1) + Insets(all = GuiColors.PADDING) // border + padding

        // Calculate requested panel size (content + insets)
        val requestedPanelWidth = currentPanel.width + panelInsets.horizontal
        val requestedPanelHeight = currentPanel.height + panelInsets.vertical

        // Constrain to screen with margins
        val maxPanelWidth = width - GuiColors.SCREEN_MARGIN * 2
        val maxPanelHeight = height - GuiColors.SCREEN_MARGIN * 2

        val panelWidth = min(requestedPanelWidth, maxPanelWidth)
        val panelHeight = min(requestedPanelHeight, maxPanelHeight)

        // Center panel on screen
        val panelRect =
            Rect(
                (width - panelWidth) / 2,
                (height - panelHeight) / 2,
                panelWidth,
                panelHeight,
            )

        // Draw panel background and border
        graphics.drawPanel(panelRect, GuiColors.PANEL_BACKGROUND, GuiColors.BORDER)

        // Calculate content area (panel inset by border + padding)
        val contentRect = panelRect.inset(panelInsets)

        // Position and render content
        currentPanel.setPosition(contentRect.x, contentRect.y)
        currentPanel.calculateWidgetPositions()

        // Clip to content area
        graphics.enableScissor(
            contentRect.x,
            contentRect.y,
            contentRect.right,
            contentRect.bottom,
        )
        currentPanel.render(graphics, mouseX, mouseY, delta)
        graphics.disableScissor()

        super.render(graphics, mouseX, mouseY, delta)
    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        val currentPanel = panelStack.peek()
        if (currentPanel != null) {
            if (currentPanel.handleClick(mouseX.toInt(), mouseY.toInt(), button)) {
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
            if (currentPanel.handleDrag(mouseX.toInt(), mouseY.toInt(), button)) {
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
            if (currentPanel.handleRelease(mouseX.toInt(), mouseY.toInt(), button)) {
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
            if (currentPanel.handleScroll(mouseX.toInt(), mouseY.toInt(), verticalAmount)) {
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
