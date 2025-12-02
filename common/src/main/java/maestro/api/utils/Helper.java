package maestro.api.utils;

import java.util.Calendar;
import maestro.api.MaestroAPI;
import maestro.api.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * An ease-of-access interface to provide the {@link Minecraft} game instance, chat and console
 * logging mechanisms, and the Maestro chat prefix.
 */
public interface Helper {

    /** Instance of {@link Helper}. Used for static-context reference. */
    Helper HELPER = new Helper() {};

    /** The tag to assign to chat messages when {@link Settings#useMessageTag} is {@code true}. */
    GuiMessageTag MESSAGE_TAG =
            new GuiMessageTag(0xFF55FF, null, Component.literal("Maestro message."), "Maestro");

    static Component getPrefix() {
        // Inner text component
        final Calendar now = Calendar.getInstance();
        final boolean xd =
                now.get(Calendar.MONTH) == Calendar.APRIL && now.get(Calendar.DAY_OF_MONTH) <= 3;
        MutableComponent maestro =
                Component.literal(
                        xd
                                ? "Maestro"
                                : MaestroAPI.getSettings().shortMaestroPrefix.value
                                        ? "M"
                                        : "Maestro");
        maestro.setStyle(maestro.getStyle().withColor(ChatFormatting.LIGHT_PURPLE));

        // Outer brackets
        MutableComponent prefix = Component.literal("");
        prefix.setStyle(maestro.getStyle().withColor(ChatFormatting.DARK_PURPLE));
        prefix.append("[");
        prefix.append(maestro);
        prefix.append("]");

        return prefix;
    }

    /**
     * Send a message to display as a toast popup
     *
     * @param title The title to display in the popup
     * @param message The message to display in the popup
     */
    default void logToast(Component title, Component message) {
        Minecraft.getInstance()
                .execute(() -> MaestroAPI.getSettings().toaster.value.accept(title, message));
    }

    /**
     * Send a message to display as a toast popup
     *
     * @param title The title to display in the popup
     * @param message The message to display in the popup
     */
    default void logToast(String title, String message) {
        logToast(Component.literal(title), Component.literal(message));
    }

    /**
     * Send a message to display as a toast popup
     *
     * @param message The message to display in the popup
     */
    default void logToast(String message) {
        logToast(Helper.getPrefix(), Component.literal(message));
    }

    /**
     * Send a message as a desktop notification
     *
     * @param message The message to display in the notification
     */
    default void logNotification(String message) {
        logNotification(message, false);
    }

    /**
     * Send a message as a desktop notification
     *
     * @param message The message to display in the notification
     * @param error Whether to log as an error
     */
    default void logNotification(String message, boolean error) {
        if (MaestroAPI.getSettings().desktopNotifications.value) {
            logNotificationDirect(message, error);
        }
    }

    /**
     * Send a message as a desktop notification regardless of desktopNotifications (should only be
     * used for critically important messages)
     *
     * @param message The message to display in the notification
     */
    default void logNotificationDirect(String message) {
        logNotificationDirect(message, false);
    }

    /**
     * Send a message as a desktop notification regardless of desktopNotifications (should only be
     * used for critically important messages)
     *
     * @param message The message to display in the notification
     * @param error Whether to log as an error
     */
    default void logNotificationDirect(String message, boolean error) {
        Minecraft.getInstance()
                .execute(() -> MaestroAPI.getSettings().notifier.value.accept(message, error));
    }

    /**
     * Send a message to chat only if chatDebug is on
     *
     * @param message The message to display in chat
     */
    default void logDebug(String message) {
        if (!MaestroAPI.getSettings().chatDebug.value) {
            return;
        }
        // Send directly to chat without toast
        MutableComponent component = Component.literal(message);
        component.setStyle(component.getStyle().withColor(ChatFormatting.GRAY));

        MutableComponent prefixed = Component.literal("");
        if (!MaestroAPI.getSettings().useMessageTag.value) {
            prefixed.append(getPrefix());
            prefixed.append(Component.literal(" "));
        }
        prefixed.append(component);

        Minecraft.getInstance()
                .execute(() -> MaestroAPI.getSettings().logger.value.accept(prefixed));
    }
}
