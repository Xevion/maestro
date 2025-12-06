package maestro.pathing.recovery

import maestro.utils.PackedBlockPos

/**
 * Composite key representing a specific movement from source to destination.
 *
 * Used to track failures for specific movement pairs, since a teleport failure from A→B doesn't
 * mean B is universally bad, just that the specific A→B movement failed.
 */
data class MovementKey(
    val source: PackedBlockPos,
    val destination: PackedBlockPos,
) {
    override fun toString(): String = "$source→$destination"
}
