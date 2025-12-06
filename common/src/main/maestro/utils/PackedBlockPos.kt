package maestro.utils

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import kotlin.math.sqrt

/**
 * Type-safe wrapper for packed block positions using Minecraft's native encoding.
 *
 * Uses Minecraft's native BlockPos encoding to store positions as a single long value.
 * This provides better memory efficiency than storing three separate int fields.
 *
 * **Encoding format (Minecraft's native):**
 * - **X:** bits 38-63 (26 bits, range: -33,554,432 to +33,554,431)
 * - **Z:** bits 12-37 (26 bits, range: -33,554,432 to +33,554,431)
 * - **Y:** bits 0-11 (12 bits, range: -2,048 to +2,047)
 *
 * **Bijective mapping:** Each position has exactly one packed representation and vice versa.
 * No hash collisions are possible.
 *
 * ## Usage Examples
 *
 * ### Construction
 * ```kotlin
 * val pos1 = PackedBlockPos(100, 64, 200)
 * val pos2 = PackedBlockPos(blockPos)
 * val pos3 = pack(x, y, z)  // Hot path optimization
 * ```
 *
 * ### FastUtil Collections (Hot Path)
 * ```kotlin
 * val map = Long2ObjectOpenHashMap<PathNode>()
 * val packed = pack(x, y, z)
 * map.put(packed.packed, node)  // Primitive long key
 * ```
 *
 * ### Regular Collections
 * ```kotlin
 * val visited = HashSet<PackedBlockPos>()
 * visited.add(PackedBlockPos(x, y, z))
 * ```
 *
 * ### Java Interop
 * ```java
 * PackedBlockPos pos = new PackedBlockPos(x, y, z);
 * long packed = pos.getPacked();  // Access primitive
 * int x = pos.getX();
 * PackedBlockPos above = pos.above();
 * ```
 */
data class PackedBlockPos(
    val packed: Long,
) {
    companion object {
        /**
         * Origin position at (0, 0, 0).
         */
        val ORIGIN: PackedBlockPos = PackedBlockPos(0, 0, 0)

        /**
         * Creates a PackedBlockPos from a Minecraft BlockPos.
         * Returns null if the input is null.
         */
        @JvmStatic
        fun from(pos: BlockPos?): PackedBlockPos? = pos?.let { PackedBlockPos(it) }
    }

    /**
     * Constructs a PackedBlockPos from a Minecraft BlockPos.
     */
    constructor(pos: BlockPos) : this(pos.asLong())

    /**
     * Constructs a PackedBlockPos from x, y, z coordinates.
     */
    constructor(x: Int, y: Int, z: Int) : this(BlockPos(x, y, z).asLong())

    /**
     * X coordinate (26-bit signed integer).
     */
    val x: Int get() = BlockPos.getX(packed)

    /**
     * Y coordinate (12-bit signed integer).
     */
    val y: Int get() = BlockPos.getY(packed)

    /**
     * Z coordinate (26-bit signed integer).
     */
    val z: Int get() = BlockPos.getZ(packed)

    /**
     * Converts this packed position to a Minecraft BlockPos.
     */
    fun toBlockPos(): BlockPos = BlockPos.of(packed)

    /**
     * Calculates squared Euclidean distance to another position.
     * Avoids expensive sqrt() call for distance comparisons.
     */
    fun distanceSq(other: PackedBlockPos): Double {
        val dx = (this.x - other.x).toDouble()
        val dy = (this.y - other.y).toDouble()
        val dz = (this.z - other.z).toDouble()
        return dx * dx + dy * dy + dz * dz
    }

    /**
     * Calculates Euclidean distance to another position.
     */
    fun distanceTo(other: PackedBlockPos): Double = sqrt(distanceSq(other))

    /**
     * Returns position 1 block above (y+1).
     */
    fun above(): PackedBlockPos = PackedBlockPos(x, y + 1, z)

    /**
     * Returns position n blocks above (y+n).
     * Returns this position unchanged if n == 0 (optimization).
     */
    fun above(n: Int): PackedBlockPos = if (n == 0) this else PackedBlockPos(x, y + n, z)

    /**
     * Returns position 1 block below (y-1).
     */
    fun below(): PackedBlockPos = PackedBlockPos(x, y - 1, z)

    /**
     * Returns position n blocks below (y-n).
     * Returns this position unchanged if n == 0 (optimization).
     */
    fun below(n: Int): PackedBlockPos = if (n == 0) this else PackedBlockPos(x, y - n, z)

    /**
     * Returns position 1 block north (z-1).
     */
    fun north(): PackedBlockPos = PackedBlockPos(x, y, z - 1)

    /**
     * Returns position n blocks north (z-n).
     * Returns this position unchanged if n == 0 (optimization).
     */
    fun north(n: Int): PackedBlockPos = if (n == 0) this else PackedBlockPos(x, y, z - n)

    /**
     * Returns position 1 block south (z+1).
     */
    fun south(): PackedBlockPos = PackedBlockPos(x, y, z + 1)

    /**
     * Returns position n blocks south (z+n).
     * Returns this position unchanged if n == 0 (optimization).
     */
    fun south(n: Int): PackedBlockPos = if (n == 0) this else PackedBlockPos(x, y, z + n)

    /**
     * Returns position 1 block east (x+1).
     */
    fun east(): PackedBlockPos = PackedBlockPos(x + 1, y, z)

    /**
     * Returns position n blocks east (x+n).
     * Returns this position unchanged if n == 0 (optimization).
     */
    fun east(n: Int): PackedBlockPos = if (n == 0) this else PackedBlockPos(x + n, y, z)

    /**
     * Returns position 1 block west (x-1).
     */
    fun west(): PackedBlockPos = PackedBlockPos(x - 1, y, z)

    /**
     * Returns position n blocks west (x-n).
     * Returns this position unchanged if n == 0 (optimization).
     */
    fun west(n: Int): PackedBlockPos = if (n == 0) this else PackedBlockPos(x - n, y, z)

    /**
     * Returns position offset by (dx, dy, dz).
     * Returns this position unchanged if all deltas are zero (optimization).
     */
    fun offset(
        dx: Int,
        dy: Int,
        dz: Int,
    ): PackedBlockPos = if (dx == 0 && dy == 0 && dz == 0) this else PackedBlockPos(x + dx, y + dy, z + dz)

    /**
     * Returns position offset by a Vec3i direction.
     */
    fun offset(dir: Vec3i): PackedBlockPos = offset(dir.x, dir.y, dir.z)

    /**
     * Returns position offset by negative direction (subtraction).
     */
    fun subtract(dir: Vec3i): PackedBlockPos = offset(-dir.x, -dir.y, -dir.z)

    /**
     * Alias for distanceSq() for compatibility.
     */
    fun distSqr(other: PackedBlockPos): Double = distanceSq(other)

    /**
     * Returns position offset in the given direction.
     */
    fun relative(direction: Direction): PackedBlockPos = offset(direction.stepX, direction.stepY, direction.stepZ)

    /**
     * Returns position offset in the given direction by n steps.
     */
    fun relative(
        direction: Direction,
        n: Int,
    ): PackedBlockPos = offset(direction.stepX * n, direction.stepY * n, direction.stepZ * n)

    /**
     * Compares this position with another object for equality.
     * Supports comparison with both PackedBlockPos and BlockPos.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        // Compare with PackedBlockPos (data class comparison)
        if (other is PackedBlockPos) {
            return packed == other.packed
        }

        // Compare with BlockPos (coordinate comparison)
        if (other is BlockPos) {
            return x == other.x && y == other.y && z == other.z
        }

        return false
    }

    /**
     * Hash code based on packed representation.
     * Must be consistent with equals() override.
     */
    override fun hashCode(): Int = packed.hashCode()

    /**
     * String representation showing coordinates.
     */
    override fun toString(): String = "PackedBlockPos($x, $y, $z)"
}
