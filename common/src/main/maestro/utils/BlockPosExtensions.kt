package maestro.utils

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB

/**
 * Converts a BlockPos to a PackedBlockPos.
 *
 * Example:
 * ```kotlin
 * val blockPos = BlockPos(100, 64, 200)
 * val packed = blockPos.pack()
 * ```
 */
fun BlockPos.pack(): PackedBlockPos = PackedBlockPos(this)

/**
 * Creates a PackedBlockPos from x, y, z coordinates without allocating a BlockPos.
 *
 * **Hot path optimization:** This inline function avoids BlockPos allocation by directly
 * computing the packed long value using bit operations.
 *
 * Uses Minecraft's native BlockPos encoding format:
 * - X: bits 38-63 (26 bits)
 * - Z: bits 12-37 (26 bits)
 * - Y: bits 0-11 (12 bits)
 *
 * Use this in performance-critical code like pathfinding where millions of positions are created.
 *
 * Example:
 * ```kotlin
 * val packed = pack(x, y, z)
 * map.put(packed.packed, node)  // FastUtil collection key
 * ```
 */
@JvmName("pack")
fun pack(
    x: Int,
    y: Int,
    z: Int,
): PackedBlockPos =
    PackedBlockPos(
        ((x.toLong() and 0x3FFFFFF) shl 38) or
            ((z.toLong() and 0x3FFFFFF) shl 12) or
            (y.toLong() and 0xFFF),
    )

/**
 * Converts a collection of BlockPos to PackedBlockPos.
 *
 * Example:
 * ```kotlin
 * val positions: List<BlockPos> = listOf(...)
 * val packed: List<PackedBlockPos> = positions.pack()
 * ```
 */
fun Iterable<BlockPos>.pack(): List<PackedBlockPos> = map { it.pack() }

/**
 * Converts a collection of PackedBlockPos to BlockPos.
 *
 * Example:
 * ```kotlin
 * val packed: List<PackedBlockPos> = listOf(...)
 * val positions: List<BlockPos> = packed.unpack()
 * ```
 */
fun Iterable<PackedBlockPos>.unpack(): List<BlockPos> = map { it.toBlockPos() }

/**
 * Lazily converts a sequence of BlockPos to PackedBlockPos.
 *
 * Useful for processing large position collections without materializing intermediate lists.
 *
 * Example:
 * ```kotlin
 * val packed = positions.asSequence()
 *     .pack()
 *     .filter { it.y > 60 }
 *     .toList()
 * ```
 */
fun Sequence<BlockPos>.pack(): Sequence<PackedBlockPos> = map { it.pack() }

/**
 * Lazily converts a sequence of PackedBlockPos to BlockPos.
 *
 * Example:
 * ```kotlin
 * val positions = packed.asSequence()
 *     .unpack()
 *     .filter { world.getBlockState(it).isAir }
 *     .toList()
 * ```
 */
fun Sequence<PackedBlockPos>.unpack(): Sequence<BlockPos> = map { it.toBlockPos() }

/**
 * Creates an AABB from two PackedBlockPos corners (inclusive).
 * The AABB is expanded by +1 on max coordinates to include the full block volume.
 *
 * Useful for creating selection boxes from two corner positions.
 *
 * Example:
 * ```kotlin
 * val corner1 = PackedBlockPos(0, 0, 0)
 * val corner2 = PackedBlockPos(5, 3, 5)
 * val aabb = corner1.toAABB(corner2)  // AABB from (0,0,0) to (6,4,6)
 * ```
 */
fun PackedBlockPos.toAABB(other: PackedBlockPos): AABB =
    AABB(
        minOf(x, other.x).toDouble(),
        minOf(y, other.y).toDouble(),
        minOf(z, other.z).toDouble(),
        maxOf(x, other.x).toDouble() + 1,
        maxOf(y, other.y).toDouble() + 1,
        maxOf(z, other.z).toDouble() + 1,
    )
