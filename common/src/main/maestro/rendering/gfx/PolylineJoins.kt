package maestro.rendering.gfx

/**
 * Various corner join types for polylines.
 */
enum class PolylineJoins {
    /**
     * Very cheap joins, useful when having many points in smooth curves.
     * No special geometry - segments simply connect.
     */
    SIMPLE,

    /**
     * Miter joins look the most natural for hard corners.
     * Can look weird with very sharp corners (spike effect).
     */
    MITER,

    /**
     * Soft rounded corners with smooth SDF-based curves.
     */
    ROUND,

    /**
     * Like miter joins but cut off (beveled) at sharp angles.
     */
    BEVEL,
    ;

    /**
     * Whether this join type requires a separate mesh for joins.
     * Round and bevel joins use additional geometry at corners.
     */
    fun hasJoinMesh(): Boolean =
        when (this) {
            SIMPLE, MITER -> false
            ROUND, BEVEL -> true
        }

    /**
     * Whether this join type uses a simplified join geometry.
     * Only bevel joins use the simple join mesh.
     */
    fun hasSimpleJoin(): Boolean =
        when (this) {
            SIMPLE, MITER, ROUND -> false
            BEVEL -> true
        }
}
