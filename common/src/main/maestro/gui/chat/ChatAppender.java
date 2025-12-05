package maestro.gui.chat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import maestro.api.utils.MaestroLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Custom Logback appender that routes MaestroLogger events to Minecraft chat.
 *
 * <p>This appender:
 *
 * <ul>
 *   <li>Filters by level: INFO, WARN, ERROR only (no DEBUG)
 *   <li>Filters by category: Only MaestroLogger categories
 *   <li>Routes to ChatMessage for formatting
 *   <li>Sends to chat via ChatMessage on Minecraft main thread
 * </ul>
 *
 * <p>Configured in logback.xml to attach to all MaestroLogger categories.
 */
public class ChatAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
        // Filter: INFO+ only (no DEBUG)
        if (!event.getLevel().isGreaterOrEqual(Level.INFO)) {
            return;
        }

        // Filter: MaestroLogger categories only
        String loggerName = event.getLoggerName();
        if (!MaestroLogger.hasCategory(loggerName)) {
            return;
        }

        // Render and send on Minecraft main thread
        Minecraft.getInstance()
                .execute(
                        () -> {
                            Component message = ChatMessage.render(event);
                            ChatMessage.sendToChat(message);
                        });
    }
}
