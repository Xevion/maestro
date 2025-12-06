package maestro.rendering.gfx

import net.minecraft.world.phys.Vec3

/**
 * Geometry positioning modes for lines and polylines.
 */
enum class LineGeometry {
    /**
     * Flat 2D lines on the XY plane (vertical wall facing +Z).
     * The line expands in the XY plane with consistent perpendiculars.
     * Good for UI elements or flat surfaces facing a fixed direction.
     */
    FLAT_XY,

    /**
     * Flat 2D lines on the XZ plane (horizontal floor/ceiling).
     * The line expands in the XZ plane with Y as the "up" direction.
     * Good for floor markers, paths, and horizontal wireframes.
     */
    FLAT_XZ,

    /**
     * Flat 2D lines on the YZ plane (vertical wall facing +X).
     * The line expands in the YZ plane with consistent perpendiculars.
     */
    FLAT_YZ,

    /**
     * Billboard lines that always face the camera.
     * The line expands perpendicular to both the line direction and camera direction.
     * This creates lines that appear to have consistent thickness from any viewing angle,
     * but joins between non-coplanar segments will not connect properly.
     *
     * Best for: isolated lines, effects, or when proper joins aren't critical.
     */
    BILLBOARD,
    ;

    /**
     * Get the normal vector for this geometry mode.
     * For flat modes, this is the fixed plane normal.
     * For billboard mode, returns null (computed per-vertex based on camera).
     */
    fun getPlaneNormal(): Vec3? =
        when (this) {
            FLAT_XY -> Vec3(0.0, 0.0, 1.0) // Normal points +Z
            FLAT_XZ -> Vec3(0.0, 1.0, 0.0) // Normal points +Y
            FLAT_YZ -> Vec3(1.0, 0.0, 0.0) // Normal points +X
            BILLBOARD -> null
        }

    /**
     * Calculate the perpendicular direction for a line segment in this geometry mode.
     *
     * @param tangent The normalized direction of the line segment
     * @param toCamera Direction from the point to the camera (only used for BILLBOARD mode)
     * @return The perpendicular direction for line expansion
     */
    fun calculatePerpendicular(
        tangent: Vec3,
        toCamera: Vec3? = null,
    ): Vec3 {
        val planeNormal = getPlaneNormal()
        return if (planeNormal != null) {
            // Flat mode: perpendicular is tangent × planeNormal
            val perp = tangent.cross(planeNormal)
            if (perp.lengthSqr() < 0.0001) {
                // Tangent is parallel to plane normal - use fallback
                when (this) {
                    FLAT_XY -> Vec3(1.0, 0.0, 0.0)
                    FLAT_XZ -> Vec3(1.0, 0.0, 0.0)
                    FLAT_YZ -> Vec3(0.0, 1.0, 0.0)
                    BILLBOARD -> Vec3(0.0, 1.0, 0.0)
                }
            } else {
                perp.normalize()
            }
        } else {
            // Billboard mode: perpendicular is tangent × toCamera
            val cam = toCamera ?: Vec3(0.0, 0.0, 1.0)
            val perp = tangent.cross(cam)
            if (perp.lengthSqr() < 0.0001) {
                // Tangent is parallel to camera direction - use fallback
                val altAxis = if (kotlin.math.abs(tangent.y) < 0.9) Vec3(0.0, 1.0, 0.0) else Vec3(1.0, 0.0, 0.0)
                tangent.cross(altAxis).normalize()
            } else {
                perp.normalize()
            }
        }
    }
}
