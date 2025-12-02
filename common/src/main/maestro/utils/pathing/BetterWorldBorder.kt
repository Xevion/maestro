package maestro.utils.pathing

import net.minecraft.world.level.border.WorldBorder

/**
 * Essentially, a "rule" for the pathfinder, prevents proposed movements from attempting to venture
 * into the world border, and prevents actual movements from placing blocks in the world border.
 */
class BetterWorldBorder(
    border: WorldBorder,
) {
    private val minX: Double = border.minX
    private val maxX: Double = border.maxX
    private val minZ: Double = border.minZ
    private val maxZ: Double = border.maxZ

    fun entirelyContains(
        x: Int,
        z: Int,
    ): Boolean = x + 1 > minX && x < maxX && z + 1 > minZ && z < maxZ

    fun canPlaceAt(
        x: Int,
        z: Int,
    ): Boolean {
        // move it in 1 block on all sides
        // because we can't place a block at the very edge against a block outside the border
        // it won't let us right-click it
        return x > minX && x + 1 < maxX && z > minZ && z + 1 < maxZ
    }
}
