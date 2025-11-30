package maestro.utils.pathing

/**
 * Represents the type of a block for pathfinding purposes.
 *
 * Each type is encoded as a 2-bit value representing different block categories:
 * - AIR (00): Passable empty space
 * - WATER (01): Liquid blocks
 * - AVOID (10): Dangerous or undesirable blocks
 * - SOLID (11): Solid impassable blocks
 *
 * The bits can be used for efficient pathfinding calculations and caching.
 */
enum class PathingBlockType(
    bits: Int,
) {
    AIR(0b00),
    WATER(0b01),
    AVOID(0b10),
    SOLID(0b11),
    ;

    private val bits: BooleanArray = booleanArrayOf((bits and 0b10) != 0, (bits and 0b01) != 0)

    @get:JvmName("getBits")
    val bitsArray: BooleanArray
        get() = bits

    companion object {
        /**
         * Reconstructs a PathingBlockType from its bit representation.
         *
         * @param b1 First bit
         * @param b2 Second bit
         * @return The corresponding PathingBlockType
         */
        @JvmStatic
        fun fromBits(
            b1: Boolean,
            b2: Boolean,
        ): PathingBlockType =
            if (b1) {
                if (b2) SOLID else AVOID
            } else {
                if (b2) WATER else AIR
            }
    }
}
