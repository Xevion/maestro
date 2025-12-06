package maestro.debug

import com.mojang.blaze3d.platform.InputConstants
import maestro.Agent
import maestro.gui.ControlScreen
import maestro.gui.Toast
import maestro.gui.radial.RadialMenu
import maestro.input.InputController
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Manages debug keybindings.
 *
 * Keybindings:
 * - Grave (`) - Toggle GUI
 * - G - Toggle freecam
 * - Right-click hold - Radial debug menu
 */
object DebugKeybindings {
    private const val CATEGORY = "Maestro Debug"

    private var cycleDetailKey: KeyMapping? = null
    private var toggleDebugKey: KeyMapping? = null

    private var wasGravePressed = false
    private var wasGPressed = false
    private var wasCtrlGPressed = false
    private var wasRightClickPressed = false

    /** Initialize keybindings. Called during client initialization. */
    @JvmStatic
    fun init() {
        cycleDetailKey =
            KeyMapping(
                "key.maestro.debug.cycle_detail",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                CATEGORY,
            )

        toggleDebugKey =
            KeyMapping(
                "key.maestro.debug.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                CATEGORY,
            )
    }

    /**
     * Process keybindings. Called every client tick.
     *
     * Checks for key state changes and triggers appropriate actions.
     */
    @JvmStatic
    fun tick() {
        if (cycleDetailKey == null || toggleDebugKey == null) return

        // Grave key: Toggle GUI
        val mc = Minecraft.getInstance()
        val agent = Agent.getPrimaryAgent()
        val gravePressed = isGravePressed()
        if (gravePressed && !wasGravePressed) {
            if (mc.screen is ControlScreen) {
                mc.setScreen(null)
            } else if (mc.screen == null) {
                mc.setScreen(ControlScreen(agent))
            }
        }
        wasGravePressed = gravePressed

        // G key: Toggle freecam (but NOT when CTRL is pressed)
        val gRawPressed = isGPressed()
        val gPressed = gRawPressed && !InputController.isCtrlPressed()
        if (gPressed && !wasGPressed) {
            toggleFreecam()
        }
        wasGPressed = gRawPressed

        // CTRL+G: Toggle freecam mode (only when freecam is active)
        if (agent.isFreecamActive) {
            val ctrlGPressed = InputController.isCtrlPressed() && isGPressed()
            if (ctrlGPressed && !wasCtrlGPressed) {
                agent.toggleFreecamMode()
                val mode = agent.freecamMode.toString()
                Toast.addOrUpdate(
                    Component.literal("Freecam Mode"),
                    Component.literal(mode),
                )
            }
            wasCtrlGPressed = ctrlGPressed
        }

        // Right-click hold: Open radial debug menu
        handleRadialMenu(mc)
    }

    private fun handleRadialMenu(mc: Minecraft) {
        val agent = Agent.getPrimaryAgent()
        if (!agent.isFreecamActive) {
            wasRightClickPressed = false
            return
        }

        if (!InputController.canUseBotKeys()) {
            wasRightClickPressed = false
            return
        }

        val window = mc.window.window
        val rightClickPressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS

        // Open menu on right-click press (not already open, no other screen)
        if (rightClickPressed && !wasRightClickPressed && mc.screen == null) {
            mc.setScreen(RadialMenu())
        }

        wasRightClickPressed = rightClickPressed
    }

    /** Check if grave accent key is pressed. */
    private fun isGravePressed(): Boolean = InputController.isKeyPressed(GLFW.GLFW_KEY_GRAVE_ACCENT)

    /** Check if G key is pressed. */
    private fun isGPressed(): Boolean = InputController.isKeyPressed(GLFW.GLFW_KEY_G)

    /** Toggle freecam on/off. */
    private fun toggleFreecam() {
        val agent = Agent.getPrimaryAgent()

        if (agent.isFreecamActive) {
            agent.deactivateFreecam()
        } else {
            agent.activateFreecam()
        }
    }
}
