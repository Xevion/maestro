package maestro.gui.panel

import maestro.gui.MaestroScreen
import maestro.gui.container.VBox
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
    private val screen: MaestroScreen,
) : VBox(spacing = 5) {
    init {
        add(ButtonWidget("← Back", Runnable { screen.popPanel() }, 150))
        add(LabelWidget("Commands (Placeholder):", 150))
        add(ButtonWidget("Mine Diamond", Runnable { placeholderCommand() }, 150))
        add(ButtonWidget("Build Structure", Runnable { placeholderCommand() }, 150))
        add(ButtonWidget("Follow Player", Runnable { placeholderCommand() }, 150))
        add(ButtonWidget("Farm Wheat", Runnable { placeholderCommand() }, 150))
    }

    private fun placeholderCommand() {
        Minecraft
            .getInstance()
            .gui
            .chat
            .addMessage(Component.literal("[Maestro] Command executed (demo)"))
    }
}
