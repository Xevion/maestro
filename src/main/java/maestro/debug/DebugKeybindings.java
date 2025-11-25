package maestro.debug;

import com.mojang.blaze3d.platform.InputConstants;
import maestro.api.MaestroAPI;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Manages debug rendering keybindings.
 *
 * <p>Keybindings:
 *
 * <ul>
 *   <li>Grave (`) - Toggle debug rendering on/off
 * </ul>
 */
public class DebugKeybindings {

    private static final String CATEGORY = "Maestro Debug";

    private static KeyMapping cycleDetailKey;
    private static KeyMapping toggleDebugKey;

    private static boolean wasGravePressed = false;
    private static boolean wasCtrlGravePressed = false;

    /** Initialize keybindings. Called during client initialization. */
    public static void init() {
        cycleDetailKey =
                new KeyMapping(
                        "key.maestro.debug.cycle_detail",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_GRAVE_ACCENT,
                        CATEGORY);

        toggleDebugKey =
                new KeyMapping(
                        "key.maestro.debug.toggle",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_GRAVE_ACCENT,
                        CATEGORY);
    }

    /**
     * Register keybindings with Minecraft's keybinding system.
     *
     * <p>Must be called from client initialization.
     */
    public static void register(Minecraft minecraft) {
        // Note: Minecraft's KeyMapping.ALL list is automatically populated when KeyMapping is
        // created
        // but we may need platform-specific registration for some mod loaders
    }

    /**
     * Process keybindings. Called every client tick.
     *
     * <p>Checks for key state changes and triggers appropriate actions.
     */
    public static void tick() {
        if (cycleDetailKey == null || toggleDebugKey == null) {
            return;
        }

        boolean ctrlPressed = isCtrlPressed();
        boolean gravePressed = isGravePressed();

        // Grave (with or without CTRL): Toggle debug rendering
        if (gravePressed) {
            if (!wasGravePressed) {
                toggleDebugRendering();
                wasGravePressed = true;
            }
        } else {
            wasGravePressed = false;
        }
    }

    /** Toggle debug rendering on/off. */
    private static void toggleDebugRendering() {
        var settings = MaestroAPI.getSettings();
        boolean newValue = !settings.debugEnabled.value;
        settings.debugEnabled.value = newValue;

        String status = newValue ? "ON" : "OFF";
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal("Debug: " + status));
    }

    /** Check if CTRL key is pressed. */
    private static boolean isCtrlPressed() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    /** Check if grave accent key is pressed. */
    private static boolean isGravePressed() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_GRAVE_ACCENT) == GLFW.GLFW_PRESS;
    }
}
