package maestro.rendering.gfx

/**
 * Types of line end caps.
 */
enum class LineEndCap {
    /**
     * No end caps - line ends exactly at the endpoint positions.
     */
    NONE,

    /**
     * Square end caps - looks the same as none visually, but extends
     * by half the line thickness beyond the endpoints.
     */
    SQUARE,

    /**
     * Rounded end caps - semicircular caps at each endpoint,
     * extending by half the line thickness.
     */
    ROUND,
}
