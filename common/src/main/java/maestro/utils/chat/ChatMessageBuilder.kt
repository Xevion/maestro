package maestro.utils.chat

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import org.slf4j.Logger
import org.slf4j.event.Level

/**
 * Builder API for creating chat messages with rich components (hover, click) that integrate with
 * structured logging.
 *
 * Usage example (fluent API):
 * ```
 * ChatMessageBuilder.info(log, "waypoint")
 *     .message("Death waypoint saved")
 *     .key("position", pos)
 *     .withHover("Click to teleport")
 *     .withClick("/maestro goto $x $y $z")
 *     .send()
 * ```
 *
 * Usage example (Kotlin DSL):
 * ```
 * chatMessage(log, "waypoint", Level.INFO) {
 *     message = "Death waypoint saved"
 *     key("position", pos)
 *     hoverText = "Click to teleport"
 *     clickCommand = "/maestro goto $x $y $z"
 * }
 * ```
 */
class ChatMessageBuilder private constructor(
    private val logger: Logger,
    private val category: String,
    private val level: Level,
) {
    private val renderer = ChatMessageRenderer()
    private val sender = ChatMessageSender()

    var message: String = ""
        private set
    private val keyValues: MutableList<KeyValue> = mutableListOf()
    var hoverText: String? = null
    var clickCommand: String? = null

    /**
     * Sets the message text.
     *
     * @param message The message to display
     * @return This builder
     */
    fun message(message: String): ChatMessageBuilder {
        this.message = message
        return this
    }

    /**
     * Adds a key-value pair to the message.
     *
     * @param key The key
     * @param value The value
     * @return This builder
     */
    fun key(
        key: String,
        value: Any,
    ): ChatMessageBuilder {
        keyValues.add(KeyValue(key, value))
        return this
    }

    /**
     * Adds hover text to the message.
     *
     * @param hoverText The text to show on hover
     * @return This builder
     */
    fun withHover(hoverText: String): ChatMessageBuilder {
        this.hoverText = hoverText
        return this
    }

    /**
     * Makes the message clickable, running a command when clicked.
     *
     * @param clickCommand The command to run (e.g., "/maestro goto 100 64 200")
     * @return This builder
     */
    fun withClick(clickCommand: String): ChatMessageBuilder {
        this.clickCommand = clickCommand
        return this
    }

    /**
     * Builds and sends the message to both the logger and chat.
     *
     * Logs the message with structured key-value pairs, then sends a rich component to chat with
     * the category prefix, hover, and click events.
     */
    fun send() {
        // Log to structured logger
        val logBuilder =
            when (level) {
                Level.INFO -> logger.atInfo()
                Level.WARN -> logger.atWarn()
                Level.ERROR -> logger.atError()
                else -> logger.atInfo()
            }

        for (kv in keyValues) {
            logBuilder.addKeyValue(kv.key, kv.value)
        }

        logBuilder.log(message)

        // Build rich component for chat
        if (hoverText != null || clickCommand != null) {
            val component = Component.literal("")

            // Add category prefix
            component.append(renderer.createCategoryPrefix(category))
            component.append(" ")

            // Add message with level color
            val messageComponent = Component.literal(message)
            val levelColor =
                when (level) {
                    Level.WARN -> ChatFormatting.YELLOW
                    Level.ERROR -> ChatFormatting.RED
                    else -> ChatFormatting.GRAY
                }
            messageComponent.style = messageComponent.style.withColor(levelColor)

            // Add key-value pairs
            if (keyValues.isNotEmpty()) {
                messageComponent.append(" ")
                val kvBuilder = StringBuilder()
                for (i in keyValues.indices) {
                    val kv = keyValues[i]
                    kvBuilder.append(kv.key).append("=").append(kv.value)
                    if (i < keyValues.size - 1) {
                        kvBuilder.append(" ")
                    }
                }
                val kvComponent = Component.literal(kvBuilder.toString())
                kvComponent.style = kvComponent.style.withColor(ChatFormatting.DARK_GRAY)
                messageComponent.append(kvComponent)
            }

            // Add hover event
            hoverText?.let {
                messageComponent.setStyle(
                    messageComponent.style.withHoverEvent(
                        HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(it)),
                    ),
                )
            }

            // Add click event
            clickCommand?.let {
                messageComponent.setStyle(
                    messageComponent.style.withClickEvent(
                        ClickEvent(ClickEvent.Action.RUN_COMMAND, it),
                    ),
                )
            }

            component.append(messageComponent)
            sender.send(component)
        }
        // If no rich components, the ChatAppender will handle sending via logger
    }

    private data class KeyValue(
        val key: String,
        val value: Any,
    )

    companion object {
        /**
         * Creates an INFO level message builder.
         *
         * @param logger The logger to log to
         * @param category The logger category (e.g., "waypoint", "cmd")
         * @return Builder instance
         */
        @JvmStatic
        fun info(
            logger: Logger,
            category: String,
        ): ChatMessageBuilder = ChatMessageBuilder(logger, category, Level.INFO)

        /**
         * Creates a WARN level message builder.
         *
         * @param logger The logger to log to
         * @param category The logger category
         * @return Builder instance
         */
        @JvmStatic
        fun warn(
            logger: Logger,
            category: String,
        ): ChatMessageBuilder = ChatMessageBuilder(logger, category, Level.WARN)

        /**
         * Creates an ERROR level message builder.
         *
         * @param logger The logger to log to
         * @param category The logger category
         * @return Builder instance
         */
        @JvmStatic
        fun error(
            logger: Logger,
            category: String,
        ): ChatMessageBuilder = ChatMessageBuilder(logger, category, Level.ERROR)
    }
}

/**
 * Kotlin DSL for building chat messages.
 *
 * Example:
 * ```
 * chatMessage(log, "waypoint", Level.INFO) {
 *     message = "Death waypoint saved"
 *     key("position", pos)
 *     hoverText = "Click to teleport"
 *     clickCommand = "/maestro goto $x $y $z"
 * }
 * ```
 */
inline fun chatMessage(
    logger: Logger,
    category: String,
    level: Level = Level.INFO,
    block: ChatMessageBuilder.() -> Unit,
) {
    val builder =
        when (level) {
            Level.INFO -> ChatMessageBuilder.info(logger, category)
            Level.WARN -> ChatMessageBuilder.warn(logger, category)
            Level.ERROR -> ChatMessageBuilder.error(logger, category)
            else -> ChatMessageBuilder.info(logger, category)
        }
    builder.block()
    builder.send()
}
