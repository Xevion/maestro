package maestro.debug

import com.mojang.blaze3d.platform.InputConstants
import maestro.Agent
import maestro.api.MaestroAPI
import maestro.api.utils.gui.MaestroToast
import maestro.gui.MaestroScreen
import maestro.input.InputOverrideHandler
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
 */
object DebugKeybindings {
    private const val CATEGORY = "Maestro Debug"

    private var cycleDetailKey: KeyMapping? = null
    private var toggleDebugKey: KeyMapping? = null

    private var wasGravePressed = false
    private var wasGPressed = false
    private var wasCtrlGPressed = false

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
     * Register keybindings with Minecraft's keybinding system.
     *
     * Must be called from client initialization.
     */
    fun register(minecraft: Minecraft) {
        // Note: Minecraft's KeyMapping.ALL list is automatically populated when KeyMapping is
        // created, but we may need platform-specific registration for some mod loaders
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
        val agent = MaestroAPI.getProvider().primaryAgent as? Agent
        if (agent != null) {
            val gravePressed = isGravePressed()
            if (gravePressed && !wasGravePressed) {
                if (mc.screen is MaestroScreen) {
                    mc.setScreen(null)
                } else if (mc.screen == null) {
                    mc.setScreen(MaestroScreen(agent))
                }
            }
            wasGravePressed = gravePressed
        }

        // G key: Toggle freecam (but NOT when CTRL is pressed)
        val gRawPressed = isGPressed()
        val gPressed = gRawPressed && !InputOverrideHandler.isCtrlPressed()
        if (gPressed && !wasGPressed) {
            toggleFreecam()
        }
        wasGPressed = gRawPressed

        // CTRL+G: Toggle freecam mode (only when freecam is active)
        if (agent != null && agent.isFreecamActive) {
            val ctrlGPressed = InputOverrideHandler.isCtrlPressed() && isGPressed()
            if (ctrlGPressed && !wasCtrlGPressed) {
                agent.toggleFreecamMode()
                val mode = agent.freecamMode.toString()
                MaestroToast.addOrUpdate(
                    Component.literal("Freecam Mode"),
                    Component.literal(mode),
                )
            }
            wasCtrlGPressed = ctrlGPressed
        }
    }

    /** Check if grave accent key is pressed. */
    private fun isGravePressed(): Boolean = InputOverrideHandler.isKeyPressed(GLFW.GLFW_KEY_GRAVE_ACCENT)

    /** Check if G key is pressed. */
    private fun isGPressed(): Boolean = InputOverrideHandler.isKeyPressed(GLFW.GLFW_KEY_G)

    /** Toggle freecam on/off. */
    private fun toggleFreecam() {
        val agent = MaestroAPI.getProvider().primaryAgent as? Agent ?: return

        if (agent.isFreecamActive) {
            agent.deactivateFreecam()
        } else {
            agent.activateFreecam()
        }
    }
}
