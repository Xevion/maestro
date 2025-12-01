package maestro.utils

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW

/**
 * Provides screen-aware input validation for bot/debug keys.
 *
 * This utility ensures that debug and bot control keys only activate during actual gameplay, not
 * when chat, inventory, or other GUI screens are open. This prevents unwanted key activation when
 * typing in chat or interacting with menus.
 *
 * **Key Blocking Policy:**
 * - Keys are **BLOCKED** when chat, inventory, pause menu, or any other screen is open
 * - Keys are **ALLOWED** during normal gameplay (no screen open)
 * - Keys are **ALLOWED** on the death screen (useful for reviewing what killed you)
 */
object InputHelper {
    /**
     * Checks if bot/debug keys should be active based on current game state.
     *
     * Keys are blocked when any screen/GUI is open (except death screen). This prevents keys
     * from activating while typing in chat, browsing inventory, or interacting with menus.
     *
     * @return true if bot keys should process input, false otherwise
     */
    @JvmStatic
    fun canUseBotKeys(): Boolean {
        val mc = Minecraft.getInstance()
        val currentScreen: Screen? = mc.screen

        return when (currentScreen) {
            // No screen open - keys are active
            null -> true
            // Death screen - allow keys (useful for reviewing what killed you)
            is DeathScreen -> true
            // Any other screen - block keys
            else -> false
        }
    }

    /**
     * Checks if a specific GLFW key is pressed AND bot keys are active.
     *
     * Combines direct GLFW polling with screen-awareness. Only returns true if the key is
     * physically pressed and no blocking screen is open.
     *
     * @param glfwKey The GLFW key constant (e.g., GLFW.GLFW_KEY_W)
     * @return true if key is physically pressed and bot keys are active
     */
    @JvmStatic
    fun isKeyPressed(glfwKey: Int): Boolean {
        if (!canUseBotKeys()) {
            return false
        }

        val window = Minecraft.getInstance().window.window
        return GLFW.glfwGetKey(window, glfwKey) == GLFW.GLFW_PRESS
    }

    /**
     * Checks if CTRL modifier is pressed (either left or right) AND bot keys are active.
     *
     * @return true if any CTRL key is pressed and bot keys are active
     */
    @JvmStatic
    fun isCtrlPressed(): Boolean {
        if (!canUseBotKeys()) {
            return false
        }

        val window = Minecraft.getInstance().window.window
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
    }
}
