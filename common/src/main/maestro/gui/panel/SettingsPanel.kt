package maestro.gui.panel

import maestro.Agent
import maestro.api.Setting
import maestro.api.SettingCategory
import maestro.gui.ControlScreen
import maestro.gui.GuiColors
import maestro.gui.container.ScrollableContainer
import maestro.gui.container.VBox
import maestro.gui.container.scrollable
import maestro.gui.container.vbox
import maestro.gui.settings.SettingWidgetFactory
import maestro.gui.settings.SettingsMetadata
import maestro.gui.widget.ButtonWidget
import maestro.gui.widget.LabelWidget
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
    private val screen: ControlScreen,
) : VBox(spacing = 4) {
    private val settings = Agent.getPrimaryAgent().settings

    private var activeCategory: SettingCategory? = null
    private var searchQuery: String = ""

    private lateinit var searchInput: TextInputWidget
    private lateinit var tabWidget: TabWidget
    private lateinit var scrollContainer: ScrollableContainer<VBox>

    init {
        add(ButtonWidget("← Back", Runnable { screen.popPanel() }, PANEL_WIDTH))

        searchInput =
            TextInputWidget(
                placeholder = "Search for settings here...",
                onTextChange = { query ->
                    searchQuery = query
                    rebuildContent()
                },
                width = PANEL_WIDTH,
            )
        add(searchInput)

        add(SeparatorWidget(PANEL_WIDTH))

        val categories = SettingCategory.entries.toList()
        val tabLabels = listOf("All") + categories.map { it.displayName }

        tabWidget =
            TabWidget(
                tabs = tabLabels,
                activeTabIndex = 0,
                onTabChange = { tabIndex ->
                    activeCategory = if (tabIndex == 0) null else categories[tabIndex - 1]
                    rebuildContent()
                },
                width = PANEL_WIDTH,
            )
        add(tabWidget)

        scrollContainer =
            vbox {
                // Content added by rebuildContent()
            }.scrollable(maxHeight = SCROLL_HEIGHT)
        add(scrollContainer)

        rebuildContent()
    }

    /**
     * Rebuilds the scroll container content based on current tab and search query.
     */
    private fun rebuildContent() {
        scrollContainer.inner.clear()

        val contentWidth = PANEL_WIDTH - GuiColors.SCROLLBAR_CONTENT_PADDING
        val filteredSettings = getFilteredSettings()

        if (filteredSettings.isEmpty()) {
            val message =
                if (searchQuery.isNotEmpty()) {
                    "No settings match \"$searchQuery\""
                } else {
                    "No settings in this category"
                }
            scrollContainer.inner.add(LabelWidget(message, contentWidth))
            scrollContainer.calculateSize()
            scrollContainer.calculateWidgetPositions()
            return
        }

        for (setting in filteredSettings) {
            val controlWidget =
                SettingWidgetFactory.createWidget(
                    setting = setting,
                    width = contentWidth,
                    onChange = { newValue ->
                        @Suppress("UNCHECKED_CAST")
                        (setting as Setting<Any>).value = newValue
                    },
                )

            if (controlWidget != null) {
                val settingRow =
                    SettingRowWidget(
                        setting = setting,
                        controlWidget = controlWidget,
                        width = contentWidth,
                    )
                scrollContainer.inner.add(settingRow)
            }
        }

        scrollContainer.calculateSize()
        scrollContainer.calculateWidgetPositions()
    }

    /**
     * Gets the list of settings to display based on current filters.
     */
    private fun getFilteredSettings(): List<Setting<*>> {
        if (searchQuery.isNotEmpty()) {
            return SettingsMetadata.search(
                query = searchQuery,
                settings = settings,
                category = null,
            )
        }

        return if (activeCategory != null) {
            SettingsMetadata
                .getByCategory(settings, activeCategory!!)
                .filter { !it.isJavaOnly() }
        } else {
            SettingsMetadata.getAllGuiSettings(settings)
        }
    }

    companion object {
        private const val PANEL_WIDTH = 500
        private const val SCROLL_HEIGHT = 400
    }
}
