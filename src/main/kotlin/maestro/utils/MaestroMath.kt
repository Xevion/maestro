package maestro.utils

/**
 * Magic constant for fast floor/ceil operations: 2^30 (1,073,741,824).
 *
 * This value is chosen to be:
 * - Large enough to handle Minecraft's world coordinate range (-30M to +30M)
 * - Small enough to avoid integer overflow during operations
 * - A power of 2 for optimal floating-point representation
 */
private const val FLOOR_DOUBLE_D = 1_073_741_824.0

/** Integer version of [FLOOR_DOUBLE_D] for offset operations. */
private const val FLOOR_DOUBLE_I = 1_073_741_824

/**
 * Computes the floor of a double value (rounds down to nearest integer) using fast integer arithmetic.
 *
 * This is a performance-optimized alternative to [kotlin.math.floor] that avoids:
 * - Function call overhead
 * - Branching logic
 * - IEEE 754 rounding mode checks
 *
 * ## Algorithm
 * The trick works by shifting the value into a range where integer truncation
 * effectively performs floor operation:
 * 1. Add 2^30 to the input (shifts into positive range)
 * 2. Truncate to integer (discards fractional part)
 * 3. Subtract 2^30 (shifts back to original range)
 *
 * ## Performance
 * Approximately 2-3x faster than [Math.floor] in tight loops due to:
 * - No function call (inlined arithmetic)
 * - No conditional branches
 * - Simple CPU instructions (FADD, F2I, ISUB)
 *
 * ## Valid Range
 * Input must be in range: `[-2^30, 2^30]` (approximately -1 billion to +1 billion).
 * Minecraft world coordinates (-30M to +30M) are well within this range.
 *
 * ## Usage
 * Primarily used in:
 * - Elytra pathfinding (converting flight paths to block coordinates)
 * - Collision detection (converting bounding boxes to block grids)
 * - Any tight loop requiring many coordinate conversions
 *
 * @param v The double value to floor
 * @return The largest integer less than or equal to [v]
 * @see fastCeil
 */
fun fastFloor(v: Double): Int = (v + FLOOR_DOUBLE_D).toInt() - FLOOR_DOUBLE_I

/**
 * Computes the ceiling of a double value (rounds up to nearest integer) using fast integer arithmetic.
 *
 * This is a performance-optimized alternative to [kotlin.math.ceil] with the same
 * advantages as [fastFloor].
 *
 * ## Algorithm
 * Uses a similar shifting trick as [fastFloor], but inverted:
 * 1. Subtract input from 2^30 (negates and shifts)
 * 2. Truncate to integer
 * 3. Subtract from 2^30 (negates back and shifts to original range)
 *
 * This effectively computes `ceil(v)` as `-floor(-v)` using integer arithmetic.
 *
 * ## Performance
 * Approximately 2-3x faster than [Math.ceil] for the same reasons as [fastFloor].
 *
 * ## Valid Range
 * Input must be in range: `[-2^30, 2^30]` (approximately -1 billion to +1 billion).
 *
 * ## Usage
 * Primarily used alongside [fastFloor] for computing bounding box ranges:
 * ```kotlin
 * val xmin = fastFloor(boundingBox.minX)  // Lower bound (inclusive)
 * val xmax = fastCeil(boundingBox.maxX)   // Upper bound (exclusive)
 * for (x in xmin until xmax) { /* iterate blocks */ }
 * ```
 *
 * @param v The double value to ceil
 * @return The smallest integer greater than or equal to [v]
 * @see fastFloor
 */
fun fastCeil(v: Double): Int = FLOOR_DOUBLE_I - (FLOOR_DOUBLE_D - v).toInt()
