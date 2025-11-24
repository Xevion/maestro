package maestro.api.utils

/**
 * A simple immutable pair of two values.
 *
 * This is a generic data class that holds two values of potentially different types.
 * It provides automatic implementations of equals(), hashCode(), toString(), and
 * component destructuring (component1(), component2()).
 *
 * @param A the type of the first value
 * @param B the type of the second value
 * @property first the first value in the pair
 * @property second the second value in the pair
 */
data class Pair<A, B>(
    @get:JvmName("first") val first: A,
    @get:JvmName("second") val second: B,
)
