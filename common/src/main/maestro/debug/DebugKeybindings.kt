package maestro.debug

import com.mojang.blaze3d.platform.InputConstants
import maestro.Agent
import maestro.api.MaestroAPI
import maestro.utils.InputOverrideHandler
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Manages debug rendering keybindings.
 *
 * Keybindings:
 * - Grave (`) - Toggle debug rendering on/off
 * - G - Toggle freecam
 */
object DebugKeybindings {
    private const val CATEGORY = "Maestro Debug"

    private var cycleDetailKey: KeyMapping? = null
    private var toggleDebugKey: KeyMapping? = null

    private var wasGravePressed = false
    private var wasGPressed = false

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
    @JvmStatic
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

        // Grave: Toggle debug rendering
        val gravePressed = isGravePressed()
        if (gravePressed && !wasGravePressed) {
            toggleDebugRendering()
        }
        wasGravePressed = gravePressed

        // G key: Toggle freecam
        val gPressed = isGPressed()
        if (gPressed && !wasGPressed) {
            toggleFreecam()
        }
        wasGPressed = gPressed
    }

    /** Toggle debug rendering on/off. */
    private fun toggleDebugRendering() {
        val settings = MaestroAPI.getSettings()
        val newValue = !settings.debugEnabled.value
        settings.debugEnabled.value = newValue

        val status = if (newValue) "ON" else "OFF"
        Minecraft
            .getInstance()
            .gui.chat
            .addMessage(Component.literal("Debug: $status"))
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
