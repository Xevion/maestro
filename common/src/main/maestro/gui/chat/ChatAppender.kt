package maestro.gui.chat

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import maestro.utils.Loggers
import net.minecraft.client.Minecraft

/**
 * Custom Logback appender that routes Loggers events to Minecraft chat.
 *
 * This appender:
 * - Filters by level: INFO, WARN, ERROR only (no DEBUG)
 * - Filters by category: Only Loggers categories
 * - Routes to ChatMessage for formatting
 * - Sends to chat via ChatMessage on Minecraft main thread
 *
 * Configured in logback.xml to attach to all Loggers categories.
 */
class ChatAppender : AppenderBase<ILoggingEvent>() {
    override fun append(event: ILoggingEvent) {
        // Filter: INFO+ only (no DEBUG)
        if (!event.level.isGreaterOrEqual(Level.INFO)) {
            return
        }

        // Filter: Loggers categories only
        val loggerName = event.loggerName
        if (!Loggers.hasCategory(loggerName)) {
            return
        }

        // Render and send on Minecraft main thread
        Minecraft.getInstance().execute {
            val message = ChatMessage.render(event)
            ChatMessage.sendToChat(message)
        }
    }
}
