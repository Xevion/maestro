package maestro.behavior

import maestro.Agent
import maestro.api.event.events.TickEvent
import maestro.utils.InputOverrideHandler
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Handles freecam movement input and position updates. When freecam is active, this behavior
 * captures WASD/Space/Shift input and calculates camera movement independently of the player
 * entity.
 */
class FreecamBehavior(
    maestro: Agent,
) : Behavior(maestro) {
    // Trig cache to avoid recalculating sin/cos every tick
    private var cachedYaw = Float.NaN
    private var cachedPitch = Float.NaN
    private var sinYaw = 0.0
    private var cosYaw = 0.0
    private var sinPitch = 0.0
    private var cosPitch = 0.0

    override fun onTick(event: TickEvent) {
        if (!maestro.isFreecamActive || event.type != TickEvent.Type.IN) {
            return
        }

        updateFreecamPosition()
    }

    private fun updateFreecamPosition() {
        // Don't process freecam movement when screens are open
        if (!InputOverrideHandler.canUseBotKeys()) {
            return
        }

        val window = ctx.minecraft().window.window

        // Read WASD/Space/Shift keys
        val forward = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS
        val back = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS
        val left = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS
        val right = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS
        val up = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS
        val down = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS

        // Calculate movement vector based on camera rotation
        val currentPos = maestro.freecamPosition ?: return

        val movement = calculateMovement(forward, back, left, right, up, down, isSprintKeyPressed())
        maestro.setFreecamPosition(currentPos.add(movement))
    }

    private fun isSprintKeyPressed(): Boolean =
        ctx
            .minecraft()
            .options.keySprint.isDown

    private fun updateTrigCache() {
        val currentYaw = maestro.freeLookYaw
        val currentPitch = maestro.freeLookPitch

        // Only recalculate if camera rotated
        if (currentYaw != cachedYaw || currentPitch != cachedPitch) {
            val yawRad = currentYaw.toDouble() * DEG_TO_RAD
            val pitchRad = currentPitch.toDouble() * DEG_TO_RAD

            sinYaw = sin(yawRad)
            cosYaw = cos(yawRad)
            sinPitch = sin(pitchRad)
            cosPitch = cos(pitchRad)

            cachedYaw = currentYaw
            cachedPitch = currentPitch
        }
    }

    private inline fun calculateMovement(
        forward: Boolean,
        back: Boolean,
        left: Boolean,
        right: Boolean,
        up: Boolean,
        down: Boolean,
        sprint: Boolean,
    ): Vec3 {
        updateTrigCache()

        var moveX = 0.0
        var moveY = 0.0
        var moveZ = 0.0

        // Calculate effective speed with sprint modifier
        val speedMultiplier = if (sprint) 2.0 else 1.0
        val effectiveSpeed = MOVEMENT_SPEED * speedMultiplier

        // Forward/back relative to camera pitch and yaw (3D movement)
        if (forward) {
            moveX -= sinYaw * cosPitch * effectiveSpeed
            moveY -= sinPitch * effectiveSpeed // Vertical component
            moveZ += cosYaw * cosPitch * effectiveSpeed
        }
        if (back) {
            moveX += sinYaw * cosPitch * effectiveSpeed
            moveY += sinPitch * effectiveSpeed // Opposite vertical
            moveZ -= cosYaw * cosPitch * effectiveSpeed
        }

        // Left/right relative to camera yaw (perpendicular to forward, horizontal only)
        if (left) {
            moveX += cosYaw * effectiveSpeed
            moveZ += sinYaw * effectiveSpeed
        }
        if (right) {
            moveX -= cosYaw * effectiveSpeed
            moveZ -= sinYaw * effectiveSpeed
        }

        // Up/down in world space (overrides pitch-based vertical)
        if (up) {
            moveY = effectiveSpeed // Override
        } else if (down) {
            moveY = -effectiveSpeed // Override
        }

        // Normalize 3D movement vector to prevent faster movement
        val totalLength = sqrt(moveX * moveX + moveY * moveY + moveZ * moveZ)
        if (totalLength > effectiveSpeed + 0.001) { // Epsilon for float precision
            val normalized = effectiveSpeed / totalLength
            moveX *= normalized
            moveY *= normalized
            moveZ *= normalized
        }

        return Vec3(moveX, moveY, moveZ)
    }

    companion object {
        private const val MOVEMENT_SPEED = 0.85
        private const val SPRINT_MULTIPLIER = 2.0
        private const val DEG_TO_RAD = Math.PI / 180.0
    }
}
