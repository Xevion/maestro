package maestro.gui.panel

import maestro.gui.MaestroScreen
import maestro.gui.widget.ButtonWidget
import maestro.gui.widget.LabelWidget
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * Commands panel with placeholder command shortcuts.
 *
 * Demonstrates sub-menu navigation with a back button.
 */
class CommandsPanel(
    screen: MaestroScreen,
) : GuiPanel(screen) {
    init {
        initializeWidgets()
    }

    private fun initializeWidgets() {
        // Back button
        addWidget(ButtonWidget("← Back", Runnable { screen.popPanel() }, BUTTON_WIDTH))

        // Commands label
        addWidget(LabelWidget("Commands (Placeholder):", BUTTON_WIDTH))

        // Placeholder command shortcuts
        addWidget(ButtonWidget("Mine Diamond", Runnable { placeholderCommand() }, BUTTON_WIDTH))
        addWidget(ButtonWidget("Build Structure", Runnable { placeholderCommand() }, BUTTON_WIDTH))
        addWidget(ButtonWidget("Follow Player", Runnable { placeholderCommand() }, BUTTON_WIDTH))
        addWidget(ButtonWidget("Farm Wheat", Runnable { placeholderCommand() }, BUTTON_WIDTH))
    }

    private fun placeholderCommand() {
        Minecraft
            .getInstance()
            .gui
            .chat
            .addMessage(Component.literal("[Maestro] Command executed (demo)"))
    }

    companion object {
        private const val BUTTON_WIDTH = 150
    }
}
