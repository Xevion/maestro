package maestro.api.utils;

import java.awt.*;
import java.io.IOException;
import org.apache.commons.lang3.SystemUtils;

/**
 * This class is not called from the main game thread. Do not refer to any Minecraft classes, it
 * wouldn't be thread safe.
 *
 * @author aUniqueUser
 */
public class NotificationHelper {

    private static TrayIcon trayIcon;

    public static void notify(String text, boolean error) {
        if (SystemUtils.IS_OS_WINDOWS) {
            windows(text, error);
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            mac(text);
        } else if (SystemUtils.IS_OS_LINUX) {
            linux(text);
        }
    }

    private static void windows(String text, boolean error) {
        if (SystemTray.isSupported()) {
            try {
                if (trayIcon == null) {
                    SystemTray tray = SystemTray.getSystemTray();
                    Image image = Toolkit.getDefaultToolkit().createImage("");

                    trayIcon = new TrayIcon(image, "Maestro");
                    trayIcon.setImageAutoSize(true);
                    trayIcon.setToolTip("Maestro");
                    tray.add(trayIcon);
                }

                trayIcon.displayMessage(
                        "Maestro",
                        text,
                        error ? TrayIcon.MessageType.ERROR : TrayIcon.MessageType.INFO);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("SystemTray is not supported");
        }
    }

    private static void mac(String text) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(
                "osascript", "-e", "display notification \"" + text + "\" with title \"Maestro\"");
        try {
            processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // The only way to display notifications on linux is to use the java-gnome library,
    // or send notify-send to shell with a ProcessBuilder. Unfortunately the java-gnome
    // library is licenced under the GPL, see (https://en.wikipedia.org/wiki/Java-gnome)
    private static void linux(String text) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("notify-send", "-a", "Maestro", text);
        try {
            processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
