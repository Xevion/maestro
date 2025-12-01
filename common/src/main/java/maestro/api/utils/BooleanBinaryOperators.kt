package maestro.api.utils

/**
 * Boolean binary operators as an enum.
 *
 * Each enum value represents a binary boolean operation (OR, AND, XOR) implemented
 * as a lambda function.
 */
enum class BooleanBinaryOperators(
    private val op: BooleanBinaryOperator,
) : BooleanBinaryOperator {
    OR({ a, b -> a || b }),
    AND({ a, b -> a && b }),
    XOR({ a, b -> a xor b }),
    ;

    override fun applyAsBoolean(
        a: Boolean,
        b: Boolean,
    ): Boolean = op.applyAsBoolean(a, b)
}
