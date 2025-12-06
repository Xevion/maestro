package maestro.pathing.calc

import maestro.utils.PackedBlockPos

/**
 * Converts this PathNode's position to a PackedBlockPos.
 * Useful for distance calculations, logging, and storage operations.
 */
val PathNode.packed: PackedBlockPos
    get() = PackedBlockPos(x, y, z)

/**
 * Calculates squared Euclidean distance from this node to another node.
 *
 * This avoids PackedBlockPos allocation by using primitive math directly.
 * Faster than `this.packed.distanceSq(other.packed)` in hot paths.
 */
fun PathNode.distanceSqTo(other: PathNode): Double {
    val dx = (x - other.x).toDouble()
    val dy = (y - other.y).toDouble()
    val dz = (z - other.z).toDouble()
    return dx * dx + dy * dy + dz * dz
}
