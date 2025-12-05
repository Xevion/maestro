package maestro.gui.chat

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import maestro.api.AgentAPI
import maestro.api.Settings
import maestro.api.utils.Helper
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import org.slf4j.Logger
import org.slf4j.event.KeyValuePair

/**
 * Unified chat message system that combines building, rendering, and sending chat messages with
 * rich components (hover, click) and structured logging integration.
 *
 * Usage example (fluent API):
 * ```
 * ChatMessage.info(log, "waypoint")
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
 *
 * Also used for rendering SLF4J log events as Minecraft chat Components with category prefixes
 * and structured formatting.
 */
class ChatMessage private constructor(
    private val logger: Logger,
    private val category: String,
    private val level: org.slf4j.event.Level,
) {
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
    fun message(message: String): ChatMessage {
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
    ): ChatMessage {
        keyValues.add(KeyValue(key, value))
        return this
    }

    /**
     * Adds hover text to the message.
     *
     * @param hoverText The text to show on hover
     * @return This builder
     */
    fun withHover(hoverText: String): ChatMessage {
        this.hoverText = hoverText
        return this
    }

    /**
     * Makes the message clickable, running a command when clicked.
     *
     * @param clickCommand The command to run (e.g., "/maestro goto 100 64 200")
     * @return This builder
     */
    fun withClick(clickCommand: String): ChatMessage {
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
                org.slf4j.event.Level.INFO -> logger.atInfo()
                org.slf4j.event.Level.WARN -> logger.atWarn()
                org.slf4j.event.Level.ERROR -> logger.atError()
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
            component.append(createCategoryPrefix(category))
            component.append(" ")

            // Add message with level color
            val messageComponent = Component.literal(message)
            val levelColor =
                when (level) {
                    org.slf4j.event.Level.WARN -> ChatFormatting.YELLOW
                    org.slf4j.event.Level.ERROR -> ChatFormatting.RED
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
            sendToChat(component)
        }
        // If no rich components, the ChatAppender will handle sending via logger
    }

    private data class KeyValue(
        val key: String,
        val value: Any,
    )

    companion object {
        private val CATEGORY_COLORS =
            mapOf(
                "cmd" to ChatFormatting.AQUA,
                "path" to ChatFormatting.GREEN,
                "mine" to ChatFormatting.GOLD,
                "farm" to ChatFormatting.DARK_GREEN,
                "build" to ChatFormatting.YELLOW,
                "combat" to ChatFormatting.RED,
                "swim" to ChatFormatting.BLUE,
                "cache" to ChatFormatting.LIGHT_PURPLE,
                "move" to ChatFormatting.WHITE,
                "rotation" to ChatFormatting.GRAY,
                "event" to ChatFormatting.DARK_AQUA,
                "api" to ChatFormatting.DARK_GRAY,
                "waypoint" to ChatFormatting.LIGHT_PURPLE,
                "inventory" to ChatFormatting.GOLD,
            )

        /**
         * Creates an INFO level message builder.
         *
         * @param logger The logger to log to
         * @param category The logger category (e.g., "waypoint", "cmd")
         * @return Builder instance
         */
        fun info(
            logger: Logger,
            category: String,
        ): ChatMessage = ChatMessage(logger, category, org.slf4j.event.Level.INFO)

        /**
         * Creates a WARN level message builder.
         *
         * @param logger The logger to log to
         * @param category The logger category
         * @return Builder instance
         */
        fun warn(
            logger: Logger,
            category: String,
        ): ChatMessage = ChatMessage(logger, category, org.slf4j.event.Level.WARN)

        /**
         * Creates an ERROR level message builder.
         *
         * @param logger The logger to log to
         * @param category The logger category
         * @return Builder instance
         */
        fun error(
            logger: Logger,
            category: String,
        ): ChatMessage = ChatMessage(logger, category, org.slf4j.event.Level.ERROR)

        /**
         * Renders a log event as a Minecraft Component.
         *
         * @param event The SLF4J logging event
         * @return Formatted Component ready to send to chat
         */
        @JvmStatic
        fun render(event: ILoggingEvent): Component {
            val category = event.loggerName
            val message = event.formattedMessage
            val keyValuePairs = event.keyValuePairs
            val level = event.level

            val result = Component.literal("")

            // Add category prefix: [category]
            result.append(createCategoryPrefix(category))
            result.append(" ")

            // Add message with level-based color
            val messageComponent = Component.literal(message)
            messageComponent.style = messageComponent.style.withColor(getLevelColor(level))
            result.append(messageComponent)

            // Add key-value pairs inline
            if (keyValuePairs != null && keyValuePairs.isNotEmpty()) {
                result.append(" ")
                result.append(formatKeyValuePairs(keyValuePairs))
            }

            return result
        }

        /**
         * Creates a colored category prefix component.
         *
         * @param category The logger category (e.g., "cmd", "mine", "path")
         * @return Component like [cmd] or [mine] with category-specific color
         */
        @JvmStatic
        fun createCategoryPrefix(category: String): Component {
            val color = CATEGORY_COLORS.getOrDefault(category, ChatFormatting.DARK_PURPLE)

            val prefix = Component.literal("[")
            prefix.style = prefix.style.withColor(color)

            val categoryText = Component.literal(category)
            categoryText.style = categoryText.style.withColor(color)

            val suffix = Component.literal("]")
            suffix.style = suffix.style.withColor(color)

            val result = Component.literal("")
            result.append(prefix)
            result.append(categoryText)
            result.append(suffix)

            return result
        }

        /**
         * Sends a component to chat or toast based on Settings.
         *
         * @param message The component to send
         */
        @JvmStatic
        fun sendToChat(message: Component) {
            val settings: Settings = AgentAPI.getSettings()

            if (settings.logAsToast.value) {
                // Send as toast notification
                settings.toaster.value.accept(Helper.getPrefix(), message)
            } else {
                // Send to chat
                // Note: useMessageTag is handled by the logger consumer
                settings.logger.value.accept(message)
            }
        }

        /**
         * Gets the chat color for a log level.
         *
         * @param level The Logback log level
         * @return ChatFormatting color (INFO=grey, WARN=yellow, ERROR=red)
         */
        private fun getLevelColor(level: Level): ChatFormatting =
            when {
                level.isGreaterOrEqual(Level.ERROR) -> ChatFormatting.RED
                level.isGreaterOrEqual(Level.WARN) -> ChatFormatting.YELLOW
                else -> ChatFormatting.GRAY
            }

        /**
         * Formats key-value pairs with colored keys and values for better readability.
         *
         * @param pairs List of key-value pairs from the log event
         * @return Component with keys in dark aqua and values in gray
         */
        private fun formatKeyValuePairs(pairs: List<KeyValuePair>): Component {
            val result: MutableComponent = Component.literal("")

            for (i in pairs.indices) {
                val pair = pairs[i]

                // Key in dark aqua
                val keyComponent = Component.literal(pair.key)
                keyComponent.style = keyComponent.style.withColor(ChatFormatting.DARK_AQUA)
                result.append(keyComponent)

                // Equals sign in dark gray
                val equalsComponent = Component.literal("=")
                equalsComponent.style = equalsComponent.style.withColor(ChatFormatting.DARK_GRAY)
                result.append(equalsComponent)

                // Value in gray
                val valueComponent = Component.literal(pair.value.toString())
                valueComponent.style = valueComponent.style.withColor(ChatFormatting.GRAY)
                result.append(valueComponent)

                // Space separator between pairs
                if (i < pairs.size - 1) {
                    result.append(" ")
                }
            }

            return result
        }
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
    level: org.slf4j.event.Level = org.slf4j.event.Level.INFO,
    block: ChatMessage.() -> Unit,
) {
    val builder =
        when (level) {
            org.slf4j.event.Level.INFO -> ChatMessage.info(logger, category)
            org.slf4j.event.Level.WARN -> ChatMessage.warn(logger, category)
            org.slf4j.event.Level.ERROR -> ChatMessage.error(logger, category)
            else -> ChatMessage.info(logger, category)
        }
    builder.block()
    builder.send()
}
