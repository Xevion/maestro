package maestro.behavior;

import maestro.Agent;
import maestro.api.event.events.TickEvent;
import maestro.utils.InputHelper;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Handles freecam movement input and position updates. When freecam is active, this behavior
 * captures WASD/Space/Shift input and calculates camera movement independently from the player
 * entity.
 */
public class FreecamBehavior extends Behavior {

    private static final double MOVEMENT_SPEED = 0.5;

    public FreecamBehavior(Agent maestro) {
        super(maestro);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!maestro.isFreecamActive() || event.getType() != TickEvent.Type.IN) {
            return;
        }

        updateFreecamPosition();
    }

    private void updateFreecamPosition() {
        // Don't process freecam movement when screens are open
        if (!InputHelper.canUseBotKeys()) {
            return;
        }

        long window = ctx.minecraft().getWindow().getWindow();

        // Read WASD/Space/Shift keys
        boolean forward = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        boolean back = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
        boolean left = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
        boolean right = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;
        boolean up = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        boolean down = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;

        // Calculate movement vector based on camera rotation
        Vec3 currentPos = maestro.getFreecamPosition();
        if (currentPos == null) {
            return;
        }

        Vec3 movement = calculateMovement(forward, back, left, right, up, down);
        maestro.setFreecamPosition(currentPos.add(movement));
    }

    private Vec3 calculateMovement(
            boolean forward, boolean back, boolean left, boolean right, boolean up, boolean down) {
        double yaw = Math.toRadians(maestro.getFreeLookYaw());

        double moveX = 0;
        double moveY = 0;
        double moveZ = 0;

        // Forward/back relative to camera yaw
        if (forward) {
            moveX -= Math.sin(yaw) * MOVEMENT_SPEED;
            moveZ += Math.cos(yaw) * MOVEMENT_SPEED;
        }
        if (back) {
            moveX += Math.sin(yaw) * MOVEMENT_SPEED;
            moveZ -= Math.cos(yaw) * MOVEMENT_SPEED;
        }

        // Left/right relative to camera yaw (perpendicular to forward)
        if (left) {
            moveX += Math.cos(yaw) * MOVEMENT_SPEED;
            moveZ += Math.sin(yaw) * MOVEMENT_SPEED;
        }
        if (right) {
            moveX -= Math.cos(yaw) * MOVEMENT_SPEED;
            moveZ -= Math.sin(yaw) * MOVEMENT_SPEED;
        }

        // Up/down in world space
        if (up) {
            moveY += MOVEMENT_SPEED;
        }
        if (down) {
            moveY -= MOVEMENT_SPEED;
        }

        // Normalize diagonal movement to prevent faster diagonal speed
        if ((forward || back) && (left || right)) {
            double horizontalLength = Math.sqrt(moveX * moveX + moveZ * moveZ);
            if (horizontalLength > 0) {
                double normalized = MOVEMENT_SPEED / horizontalLength;
                moveX *= normalized;
                moveZ *= normalized;
            }
        }

        return new Vec3(moveX, moveY, moveZ);
    }
}
