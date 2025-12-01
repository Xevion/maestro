package maestro.behavior

import maestro.Agent
import maestro.api.event.events.TickEvent
import maestro.utils.InputHelper
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
    override fun onTick(event: TickEvent) {
        if (!maestro.isFreecamActive() || event.type != TickEvent.Type.IN) {
            return
        }

        updateFreecamPosition()
    }

    private fun updateFreecamPosition() {
        // Don't process freecam movement when screens are open
        if (!InputHelper.canUseBotKeys()) {
            return
        }

        val window = ctx.minecraft().getWindow().getWindow()

        // Read WASD/Space/Shift keys
        val forward = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS
        val back = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS
        val left = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS
        val right = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS
        val up = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS
        val down = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS

        // Calculate movement vector based on camera rotation
        val currentPos = maestro.getFreecamPosition() ?: return

        val movement = calculateMovement(forward, back, left, right, up, down)
        maestro.setFreecamPosition(currentPos.add(movement))
    }

    private fun calculateMovement(
        forward: Boolean,
        back: Boolean,
        left: Boolean,
        right: Boolean,
        up: Boolean,
        down: Boolean,
    ): Vec3 {
        val yaw = Math.toRadians(maestro.getFreeLookYaw().toDouble())

        var moveX = 0.0
        var moveY = 0.0
        var moveZ = 0.0

        // Forward/back relative to camera yaw
        if (forward) {
            moveX -= sin(yaw) * MOVEMENT_SPEED
            moveZ += cos(yaw) * MOVEMENT_SPEED
        }
        if (back) {
            moveX += sin(yaw) * MOVEMENT_SPEED
            moveZ -= cos(yaw) * MOVEMENT_SPEED
        }

        // Left/right relative to camera yaw (perpendicular to forward)
        if (left) {
            moveX += cos(yaw) * MOVEMENT_SPEED
            moveZ += sin(yaw) * MOVEMENT_SPEED
        }
        if (right) {
            moveX -= cos(yaw) * MOVEMENT_SPEED
            moveZ -= sin(yaw) * MOVEMENT_SPEED
        }

        // Up/down in world space
        if (up) {
            moveY += MOVEMENT_SPEED
        }
        if (down) {
            moveY -= MOVEMENT_SPEED
        }

        // Normalize diagonal movement to prevent faster diagonal speed
        if ((forward || back) && (left || right)) {
            val horizontalLength = sqrt(moveX * moveX + moveZ * moveZ)
            if (horizontalLength > 0) {
                val normalized = MOVEMENT_SPEED / horizontalLength
                moveX *= normalized
                moveZ *= normalized
            }
        }

        return Vec3(moveX, moveY, moveZ)
    }

    companion object {
        private const val MOVEMENT_SPEED = 0.85
    }
}
