package maestro.utils.chat;

import maestro.api.MaestroAPI;
import maestro.api.Settings;
import maestro.api.utils.Helper;
import net.minecraft.network.chat.Component;

/**
 * Sends chat messages to the Minecraft client, respecting Settings configuration.
 *
 * <p>Handles routing to:
 *
 * <ul>
 *   <li>Toast notifications when {@link Settings#logAsToast} is enabled
 *   <li>Chat with message tag when {@link Settings#useMessageTag} is enabled
 *   <li>Normal chat otherwise
 * </ul>
 */
public class ChatMessageSender {

    /**
     * Sends a component to chat or toast based on Settings.
     *
     * @param message The component to send
     */
    public void send(Component message) {
        Settings settings = MaestroAPI.getSettings();

        if (settings.logAsToast.value) {
            // Send as toast notification
            settings.toaster.value.accept(Helper.getPrefix(), message);
        } else {
            // Send to chat
            // Note: useMessageTag is handled by the logger consumer
            settings.logger.value.accept(message);
        }
    }
}
