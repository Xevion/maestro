package maestro.gui.settings

import maestro.Setting
import maestro.SettingCategory
import maestro.Settings
import maestro.command.helpers.FuzzySearchHelper

/**
 * Metadata and search utilities for settings in the GUI.
 *
 * Provides fuzzy search capabilities using the existing FuzzySearchHelper.
 */
object SettingsMetadata {
    /**
     * Searches settings using fuzzy matching on setting names.
     *
     * Uses the existing FuzzySearchHelper with Levenshtein distance for
     * intelligent typo tolerance and prefix matching.
     *
     * @param query Search query from user
     * @param settings Settings instance containing all settings
     * @param category Optional category filter (null = all categories)
     * @return List of matching settings, ranked by relevance
     */
    fun search(
        query: String,
        settings: Settings,
        category: SettingCategory? = null,
    ): List<Setting<*>> {
        // Filter by category if specified
        val candidates =
            if (category != null) {
                settings.allSettings.filter { it.category == category }
            } else {
                settings.allSettings
            }

        // Use existing FuzzySearchHelper for search
        return FuzzySearchHelper.search(
            query,
            candidates.toMutableList(),
            { setting -> setting.getName() },
            FuzzySearchHelper.DEFAULT_THRESHOLD,
            FuzzySearchHelper.DEFAULT_LIMIT,
        )
    }

    /**
     * Gets all settings in a specific category.
     *
     * @param settings Settings instance
     * @param category Category to filter by
     * @return List of settings in the category, sorted by name
     */
    fun getByCategory(
        settings: Settings,
        category: SettingCategory,
    ): List<Setting<*>> =
        settings.allSettings
            .filter { it.category == category }
            .sortedBy { it.getName().lowercase() }

    /**
     * Gets all settings that can be displayed in the GUI.
     *
     * Filters out Java-only settings and returns the rest sorted by name.
     *
     * @param settings Settings instance
     * @return List of GUI-compatible settings
     */
    fun getAllGuiSettings(settings: Settings): List<Setting<*>> =
        settings.allSettings
            .filter { !it.isJavaOnly() }
            .sortedBy { it.getName().lowercase() }
}
