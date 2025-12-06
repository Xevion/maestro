package maestro

/**
 * Categories for organizing settings in the GUI.
 *
 * Each category has a display name used in the UI.
 */
enum class SettingCategory(
    val displayName: String,
) {
    MOVEMENT("Movement"),
    COMBAT("Combat"),
    PATHFINDING("Pathfinding"),
    RENDERING("Rendering"),
    BUILDING("Building"),
    MINING("Mining"),
    COORDINATION("Coordination"),
    ADVANCED("Advanced"),
    ;

    override fun toString(): String = displayName
}
