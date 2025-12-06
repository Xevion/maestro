package maestro.utils

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseFireBlock
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

/**
 * Returns the geometric center of this block position.
 * Simple 0.5 offset - does not consider block collision shape.
 *
 * For collision-aware centering (e.g., slabs, fire), use [Level.getBlockCenter].
 */
val BlockPos.center: Vec3 get() = Vec3(x + 0.5, y + 0.5, z + 0.5)

/**
 * Returns the horizontal (XZ plane) center of this block position.
 * Returns Vec2(x + 0.5, z + 0.5).
 */
val BlockPos.centerXZ: Vec2 get() = Vec2(x + 0.5f, z + 0.5f)

/**
 * Returns the XY plane center of this block position.
 * Returns Vec2(x + 0.5, y + 0.5).
 */
val BlockPos.centerXY: Vec2 get() = Vec2(x + 0.5f, y + 0.5f)

/**
 * Returns the YZ plane center of this block position.
 * Returns Vec2(y + 0.5, z + 0.5).
 */
val BlockPos.centerYZ: Vec2 get() = Vec2(y + 0.5f, z + 0.5f)

/**
 * Returns the center of this block position with a custom Y coordinate.
 * Useful for targeting a specific height within or near a block.
 */
fun BlockPos.centerWithY(y: Double): Vec3 = Vec3(x + 0.5, y, z + 0.5)

/**
 * Returns the center of this block at eye height (1.62 blocks above the base).
 * Useful for targeting player eye position for line-of-sight calculations.
 */
val BlockPos.centerWithEyes: Vec3 get() = Vec3(x + 0.5, y + 1.62, z + 0.5)

/**
 * Calculates the center of the block's collision box at this position.
 *
 * This accounts for non-standard block shapes:
 * - Slabs (half-height blocks)
 * - Stairs (partial blocks)
 * - Fire (targets bottom of block for extinguishing)
 * - Air/passable blocks (falls back to geometric center)
 *
 * Use this for precise block targeting (e.g., rotation calculations).
 * For simple movement, [BlockPos.center] is sufficient and faster.
 *
 * Replaces: VecUtils.calculateBlockCenter(Level, BlockPos)
 */
fun Level.getBlockCenter(pos: BlockPos): Vec3 {
    val state = getBlockState(pos)
    val shape = state.getCollisionShape(this, pos)

    // No collision box - use geometric center
    if (shape.isEmpty) {
        return pos.center
    }

    // Calculate center of collision box
    val xDiff = (shape.min(Direction.Axis.X) + shape.max(Direction.Axis.X)) / 2
    val yDiff = (shape.min(Direction.Axis.Y) + shape.max(Direction.Axis.Y)) / 2
    val zDiff = (shape.min(Direction.Axis.Z) + shape.max(Direction.Axis.Z)) / 2

    if (xDiff.isNaN() || yDiff.isNaN() || zDiff.isNaN()) {
        throw IllegalStateException("Invalid collision shape for $state at $pos: $shape")
    }

    // Special case: fire blocks - target bottom for extinguishing
    val finalYDiff = if (state.block is BaseFireBlock) 0.0 else yDiff

    return Vec3(pos.x + xDiff, pos.y + finalYDiff, pos.z + zDiff)
}

/**
 * Calculates the distance from this entity to the center of a block position.
 *
 * Replaces: VecUtils.entityDistanceToCenter(Entity, BlockPos)
 */
fun Entity.distanceTo(pos: BlockPos): Double = position().distanceTo(pos.center)

/**
 * Calculates the horizontal (XZ plane) distance from this entity to the center
 * of a block position, ignoring Y coordinate.
 *
 * Useful for checking if an entity is within horizontal range of a block.
 *
 * Replaces: VecUtils.entityFlatDistanceToCenter(Entity, BlockPos)
 */
fun Entity.horizontalDistanceTo(pos: BlockPos): Double = position().horizontalDistanceTo(pos.centerXZ)

/**
 * Calculates the squared distance from this entity to the center of a block position.
 * Avoids expensive sqrt() for distance comparisons.
 */
fun Entity.distanceSquaredTo(pos: BlockPos): Double = position().distanceSquaredTo(pos.center)

/**
 * Calculates the squared horizontal distance from this entity to the center
 * of a block position.
 */
fun Entity.horizontalDistanceSquaredTo(pos: BlockPos): Double = position().horizontalDistanceSquaredTo(pos.centerXZ)
