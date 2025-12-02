package maestro.gui.panel

import maestro.Agent
import maestro.api.Setting
import maestro.api.SettingCategory
import maestro.gui.MaestroScreen
import maestro.gui.settings.SettingWidgetFactory
import maestro.gui.settings.SettingsMetadata
import maestro.gui.widget.ButtonWidget
import maestro.gui.widget.LabelWidget
import maestro.gui.widget.ScrollContainerWidget
import maestro.gui.widget.SeparatorWidget
import maestro.gui.widget.SettingRowWidget
import maestro.gui.widget.TabWidget
import maestro.gui.widget.TextInputWidget

/**
 * Settings panel with category tabs, search, and scrollable settings list.
 *
 * Features:
 * - Category tabs for organizing settings
 * - Search box with fuzzy matching
 * - Scrollable container for viewing many settings
 * - Modified indicators and reset buttons
 * - Dynamic content updates based on tab/search
 */
class SettingsPanel(
    screen: MaestroScreen,
) : GuiPanel(screen) {
    private val settings = Agent.settings()

    // UI state
    private var activeCategory: SettingCategory? = null
    private var searchQuery: String = ""

    // Widgets that need to be accessible
    private lateinit var searchInput: TextInputWidget
    private lateinit var tabWidget: TabWidget
    private lateinit var scrollContainer: ScrollContainerWidget

    init {
        initializeWidgets()
    }

    private fun initializeWidgets() {
        // Back button (fixed at top)
        addWidget(ButtonWidget("← Back", Runnable { screen.popPanel() }, PANEL_WIDTH))

        // Header
        addWidget(LabelWidget("Settings", PANEL_WIDTH))

        // Search box
        searchInput =
            TextInputWidget(
                placeholder = "Search settings...",
                onTextChange = { query ->
                    searchQuery = query
                    rebuildContent()
                },
                width = PANEL_WIDTH,
            )
        addWidget(searchInput)

        addWidget(SeparatorWidget(PANEL_WIDTH))

        // Category tabs
        val categories = SettingCategory.entries.toList()
        val tabLabels = listOf("All") + categories.map { it.displayName }

        tabWidget =
            TabWidget(
                tabs = tabLabels,
                activeTabIndex = 0,
                onTabChange = { tabIndex ->
                    // Tab 0 = "All", others map to categories
                    activeCategory = if (tabIndex == 0) null else categories[tabIndex - 1]
                    rebuildContent()
                },
                width = PANEL_WIDTH,
            )
        addWidget(tabWidget)

        // Create scroll container for settings
        scrollContainer = ScrollContainerWidget(PANEL_WIDTH, SCROLL_HEIGHT)
        addWidget(scrollContainer)

        // Initial content
        rebuildContent()
    }

    /**
     * Rebuilds the scroll container content based on current tab and search query.
     */
    private fun rebuildContent() {
        scrollContainer.clearChildren()

        val contentWidth = PANEL_WIDTH - SCROLLBAR_SPACE

        // Get filtered settings
        val filteredSettings = getFilteredSettings()

        // If no results, show message
        if (filteredSettings.isEmpty()) {
            val message =
                if (searchQuery.isNotEmpty()) {
                    "No settings match \"$searchQuery\""
                } else {
                    "No settings in this category"
                }
            scrollContainer.addChild(LabelWidget(message, contentWidth))
            return
        }

        // Create setting rows
        for (setting in filteredSettings) {
            val controlWidget =
                SettingWidgetFactory.createWidget(
                    setting = setting,
                    width = contentWidth,
                    onChange = { newValue ->
                        // Update setting value
                        @Suppress("UNCHECKED_CAST")
                        (setting as Setting<Any>).value = newValue
                    },
                )

            // Only add if widget creation succeeded
            if (controlWidget != null) {
                val settingRow =
                    SettingRowWidget(
                        setting = setting,
                        controlWidget = controlWidget,
                        width = contentWidth,
                    )
                scrollContainer.addChild(settingRow)
            }
        }
    }

    /**
     * Gets the list of settings to display based on current filters.
     */
    private fun getFilteredSettings(): List<Setting<*>> {
        // If search query is active, use fuzzy search (ignores category)
        if (searchQuery.isNotEmpty()) {
            return SettingsMetadata.search(
                query = searchQuery,
                settings = settings,
                category = null, // Search across all categories
            )
        }

        // Otherwise, filter by category
        return if (activeCategory != null) {
            SettingsMetadata
                .getByCategory(settings, activeCategory!!)
                .filter { !it.isJavaOnly() }
        } else {
            SettingsMetadata.getAllGuiSettings(settings)
        }
    }

    companion object {
        private const val PANEL_WIDTH = 500 // Wider panel for settings
        private const val SCROLL_HEIGHT = 400 // Max viewport height
        private const val SCROLLBAR_SPACE = 8 // 6px scrollbar + 2px padding
    }
}
