package maestro.utils

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector4f

/**
 * Utilities for projecting world coordinates to screen space.
 *
 * Used for positioning debug overlays and labels at 3D world positions.
 */
object WorldToScreen {
    /**
     * Projects a world position to screen coordinates.
     *
     * @param worldPos The position in world space
     * @param modelView The model-view matrix from the current render context
     * @param projection The projection matrix from the current render context
     * @return Screen coordinates (x, y, depth) or null if behind camera
     */
    fun project(
        worldPos: Vec3,
        modelView: Matrix4f,
        projection: Matrix4f,
    ): Vec3? {
        val mc = Minecraft.getInstance()
        val camera = mc.gameRenderer.mainCamera

        // Transform to camera-relative coordinates
        val camPos = camera.position
        val relX = (worldPos.x - camPos.x).toFloat()
        val relY = (worldPos.y - camPos.y).toFloat()
        val relZ = (worldPos.z - camPos.z).toFloat()

        // Apply model-view transformation
        val viewPos = Vector4f(relX, relY, relZ, 1.0f)
        viewPos.mul(modelView)

        // Apply projection transformation
        val clipPos = Vector4f(viewPos.x, viewPos.y, viewPos.z, viewPos.w)
        clipPos.mul(projection)

        // Check if behind camera (w <= 0 means behind or at camera plane)
        if (clipPos.w <= 0.0001f) return null

        // Perspective division to get normalized device coordinates
        val ndcX = clipPos.x / clipPos.w
        val ndcY = clipPos.y / clipPos.w
        val ndcZ = clipPos.z / clipPos.w

        // Convert to screen coordinates
        val window = mc.window
        val screenX = (ndcX + 1.0f) * 0.5f * window.guiScaledWidth
        val screenY = (1.0f - ndcY) * 0.5f * window.guiScaledHeight

        return Vec3(screenX.toDouble(), screenY.toDouble(), ndcZ.toDouble())
    }

    /**
     * Projects a block position (center) to screen coordinates.
     *
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate
     * @param modelView The model-view matrix
     * @param projection The projection matrix
     * @return Screen coordinates or null if behind camera
     */
    fun projectBlock(
        x: Int,
        y: Int,
        z: Int,
        modelView: Matrix4f,
        projection: Matrix4f,
    ): Vec3? = project(Vec3(x + 0.5, y + 0.5, z + 0.5), modelView, projection)

    /**
     * Gets a scale factor for rendering elements at a world position.
     *
     * Scales elements inversely with distance so they appear roughly
     * the same size regardless of distance (up to a maximum scale).
     *
     * @param worldPos The world position
     * @param cameraPos The camera position
     * @param baseScale The base scale at 1 block distance
     * @param maxScale Maximum scale factor to prevent overly large elements
     * @return Scale factor to use for rendering
     */
    fun getDistanceScale(
        worldPos: Vec3,
        cameraPos: Vec3,
        baseScale: Double = 1.0,
        maxScale: Double = 3.0,
    ): Double {
        val distance = worldPos.distanceTo(cameraPos)
        if (distance < 0.1) return maxScale

        // Scale inversely with distance, clamped to maxScale
        return (baseScale / distance).coerceAtMost(maxScale)
    }

    /**
     * Checks if a world position is in front of the camera.
     *
     * @param worldPos The world position to check
     * @return True if the position is in front of the camera
     */
    fun isInFrontOfCamera(worldPos: Vec3): Boolean {
        val mc = Minecraft.getInstance()
        val camera = mc.gameRenderer.mainCamera
        val camPos = camera.position
        val cameraDir = Vec3.directionFromRotation(camera.xRot, camera.yRot)

        val toPos = worldPos.subtract(camPos).normalize()
        return cameraDir.dot(toPos) > 0
    }

    /**
     * Gets the distance from the camera to a world position.
     */
    fun distanceToCamera(worldPos: Vec3): Double {
        val mc = Minecraft.getInstance()
        return worldPos.distanceTo(mc.gameRenderer.mainCamera.position)
    }

    /**
     * Gets the current camera position.
     */
    fun getCameraPos(): Vec3 =
        Minecraft
            .getInstance()
            .gameRenderer.mainCamera.position

    /**
     * Gets the current camera look direction (normalized).
     */
    fun getCameraDirection(): Vec3 {
        val camera = Minecraft.getInstance().gameRenderer.mainCamera
        return Vec3.directionFromRotation(camera.xRot, camera.yRot)
    }
}
