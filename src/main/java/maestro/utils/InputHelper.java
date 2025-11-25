package maestro.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * Provides screen-aware input validation for bot/debug keys.
 *
 * <p>This utility ensures that debug and bot control keys only activate during actual gameplay, not
 * when chat, inventory, or other GUI screens are open. This prevents unwanted key activation when
 * typing in chat or interacting with menus.
 *
 * <p><b>Key Blocking Policy:</b>
 *
 * <ul>
 *   <li>Keys are <b>BLOCKED</b> when chat, inventory, pause menu, or any other screen is open
 *   <li>Keys are <b>ALLOWED</b> during normal gameplay (no screen open)
 *   <li>Keys are <b>ALLOWED</b> on the death screen (useful for reviewing what killed you)
 * </ul>
 */
public final class InputHelper {

    private InputHelper() {
        // Utility class - no instantiation
    }

    /**
     * Checks if bot/debug keys should be active based on current game state.
     *
     * <p>Keys are blocked when any screen/GUI is open (except death screen). This prevents keys
     * from activating while typing in chat, browsing inventory, or interacting with menus.
     *
     * @return true if bot keys should process input, false otherwise
     */
    public static boolean canUseBotKeys() {
        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.screen;

        // No screen open - keys are active
        if (currentScreen == null) {
            return true;
        }

        // Death screen - allow keys (useful for reviewing what killed you)
        if (currentScreen instanceof DeathScreen) {
            return true;
        }

        // Any other screen - block keys
        return false;
    }

    /**
     * Checks if a specific GLFW key is pressed AND bot keys are active.
     *
     * <p>Combines direct GLFW polling with screen-awareness. Only returns true if the key is
     * physically pressed and no blocking screen is open.
     *
     * @param glfwKey The GLFW key constant (e.g., GLFW.GLFW_KEY_W)
     * @return true if key is physically pressed and bot keys are active
     */
    public static boolean isKeyPressed(int glfwKey) {
        if (!canUseBotKeys()) {
            return false;
        }

        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, glfwKey) == GLFW.GLFW_PRESS;
    }

    /**
     * Checks if CTRL modifier is pressed (either left or right) AND bot keys are active.
     *
     * @return true if any CTRL key is pressed and bot keys are active
     */
    public static boolean isCtrlPressed() {
        if (!canUseBotKeys()) {
            return false;
        }

        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
}
