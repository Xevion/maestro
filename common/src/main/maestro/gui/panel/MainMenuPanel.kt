package maestro.gui.panel

import maestro.Agent
import maestro.gui.ControlScreen
import maestro.gui.container.VBox
import maestro.gui.widget.ButtonWidget
import maestro.gui.widget.LabelWidget
import maestro.gui.widget.SeparatorWidget

/**
 * The main menu panel for the GUI overlay.
 *
 * Contains:
 * - Toggle Debug button (replaces GRAVE key toggle)
 * - Navigation to Settings and Commands sub-menus
 * - Demo feature buttons (placeholders)
 */
class MainMenuPanel(
    private val screen: ControlScreen,
) : VBox(spacing = 5) {
    init {
        add(ButtonWidget("Toggle Debug", Runnable { toggleDebug() }, 150))
        add(ButtonWidget("Settings →", Runnable { screen.pushPanel(SettingsPanel(screen)) }, 150))
        add(ButtonWidget("Commands →", Runnable { screen.pushPanel(CommandsPanel(screen)) }, 150))
        add(SeparatorWidget(150))
        add(LabelWidget("Demo Features:", 150))
        add(ButtonWidget("Start Mining", Runnable { placeholderAction() }, 150))
        add(ButtonWidget("Start Pathing", Runnable { placeholderAction() }, 150))
        add(ButtonWidget("Stop All", Runnable { placeholderAction() }, 150))
    }

    private fun toggleDebug() {
        val settings = Agent.getPrimaryAgent().settings
        settings.debugEnabled.value = !settings.debugEnabled.value
    }

    private fun placeholderAction() {
    }
}
