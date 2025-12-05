package maestro.api.utils;

import java.awt.*;
import java.io.IOException;
import java.util.Calendar;
import maestro.api.AgentAPI;
import maestro.api.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;

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
                                : AgentAPI.getSettings().shortMaestroPrefix.value
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
                .execute(() -> AgentAPI.getSettings().toaster.value.accept(title, message));
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
        if (AgentAPI.getSettings().desktopNotifications.value) {
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
                .execute(() -> AgentAPI.getSettings().notifier.value.accept(message, error));
    }

    /**
     * Send a message to chat only if chatDebug is on
     *
     * @param message The message to display in chat
     */
    default void logDebug(String message) {
        if (!AgentAPI.getSettings().chatDebug.value) {
            return;
        }
        // Send directly to chat without toast
        MutableComponent component = Component.literal(message);
        component.setStyle(component.getStyle().withColor(ChatFormatting.GRAY));

        MutableComponent prefixed = Component.literal("");
        if (!AgentAPI.getSettings().useMessageTag.value) {
            prefixed.append(getPrefix());
            prefixed.append(Component.literal(" "));
        }
        prefixed.append(component);

        Minecraft.getInstance().execute(() -> AgentAPI.getSettings().logger.value.accept(prefixed));
    }

    /**
     * Send a desktop notification using platform-specific notification systems. This method is not
     * called from the main game thread and does not reference Minecraft classes to ensure thread
     * safety.
     *
     * @param text The notification message
     * @param error Whether this is an error notification
     */
    static void notifySystem(String text, boolean error) {
        if (SystemUtils.IS_OS_WINDOWS) {
            notifyWindows(text, error);
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            notifyMac(text);
        } else if (SystemUtils.IS_OS_LINUX) {
            notifyLinux(text);
        }
    }

    Logger NOTIFICATION_LOG = Loggers.get("event");

    @SuppressWarnings("MutablePublicArray") // Intentional holder for tray icon state
    TrayIcon[] TRAY_ICON_HOLDER = new TrayIcon[1];

    private static void notifyWindows(String text, boolean error) {
        if (SystemTray.isSupported()) {
            try {
                if (TRAY_ICON_HOLDER[0] == null) {
                    SystemTray tray = SystemTray.getSystemTray();
                    Image image = Toolkit.getDefaultToolkit().createImage("");

                    TRAY_ICON_HOLDER[0] = new TrayIcon(image, "Maestro");
                    TRAY_ICON_HOLDER[0].setImageAutoSize(true);
                    TRAY_ICON_HOLDER[0].setToolTip("Maestro");
                    tray.add(TRAY_ICON_HOLDER[0]);
                }

                TRAY_ICON_HOLDER[0].displayMessage(
                        "Maestro",
                        text,
                        error ? TrayIcon.MessageType.ERROR : TrayIcon.MessageType.INFO);
            } catch (Exception e) {
                NOTIFICATION_LOG.atError().setCause(e).log("Failed to show Windows notification");
            }
        } else {
            System.out.println("SystemTray is not supported");
        }
    }

    private static void notifyMac(String text) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(
                "osascript", "-e", "display notification \"" + text + "\" with title \"Maestro\"");
        try {
            processBuilder.start();
        } catch (IOException e) {
            NOTIFICATION_LOG.atError().setCause(e).log("Failed to show Mac notification");
        }
    }

    private static void notifyLinux(String text) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("notify-send", "-a", "Maestro", text);
        try {
            processBuilder.start();
        } catch (IOException e) {
            NOTIFICATION_LOG.atError().setCause(e).log("Failed to show Linux notification");
        }
    }
}
