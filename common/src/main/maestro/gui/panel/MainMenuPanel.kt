package maestro.gui.panel

import maestro.Agent
import maestro.gui.MaestroScreen
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
    screen: MaestroScreen,
) : GuiPanel(screen) {
    init {
        initializeWidgets()
    }

    private fun initializeWidgets() {
        // Toggle Debug button
        addWidget(ButtonWidget("Toggle Debug", Runnable { toggleDebug() }, BUTTON_WIDTH))

        // Navigation buttons
        addWidget(
            ButtonWidget(
                "Settings →",
                Runnable { screen.pushPanel(SettingsPanel(screen)) },
                BUTTON_WIDTH,
            ),
        )
        addWidget(
            ButtonWidget(
                "Commands →",
                Runnable { screen.pushPanel(CommandsPanel(screen)) },
                BUTTON_WIDTH,
            ),
        )

        // Separator
        addWidget(SeparatorWidget(BUTTON_WIDTH))

        // Demo features label
        addWidget(LabelWidget("Demo Features:", BUTTON_WIDTH))

        // Placeholder demo buttons
        addWidget(ButtonWidget("Start Mining", Runnable { placeholderAction() }, BUTTON_WIDTH))
        addWidget(ButtonWidget("Start Pathing", Runnable { placeholderAction() }, BUTTON_WIDTH))
        addWidget(ButtonWidget("Stop All", Runnable { placeholderAction() }, BUTTON_WIDTH))
    }

    private fun toggleDebug() {
        val settings = Agent.settings()
        settings.debugEnabled.value = !settings.debugEnabled.value
    }

    private fun placeholderAction() {
    }

    companion object {
        private const val BUTTON_WIDTH = 150
    }
}
