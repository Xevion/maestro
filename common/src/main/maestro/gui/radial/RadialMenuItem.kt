package maestro.gui.radial

import net.minecraft.resources.ResourceLocation

/**
 * Represents a single item in the radial debug menu.
 *
 * @param id Unique identifier for this menu item
 * @param label Display text shown on the segment
 * @param icon Optional icon texture to display
 * @param action Callback executed when this item is selected and released
 */
data class RadialMenuItem(
    val id: String,
    val label: String,
    val icon: ResourceLocation? = null,
    val action: () -> Unit,
)
