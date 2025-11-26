package maestro.utils.chat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.event.Level;

/**
 * Builder API for creating chat messages with rich components (hover, click) that integrate with
 * structured logging.
 *
 * <p>Usage example:
 *
 * <pre>
 * ChatMessageBuilder.info(log, "waypoint")
 *     .message("Death waypoint saved")
 *     .key("position", pos)
 *     .withHover("Click to teleport")
 *     .withClick("/maestro goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ())
 *     .send();
 * </pre>
 */
public class ChatMessageBuilder {

    private final Logger logger;
    private final String category;
    private final Level level;
    private final ChatMessageRenderer renderer = new ChatMessageRenderer();
    private final ChatMessageSender sender = new ChatMessageSender();

    private String message = "";
    private final List<KeyValue> keyValues = new ArrayList<>();
    private String hoverText = null;
    private String clickCommand = null;

    private ChatMessageBuilder(Logger logger, String category, Level level) {
        this.logger = logger;
        this.category = category;
        this.level = level;
    }

    /**
     * Creates an INFO level message builder.
     *
     * @param logger The logger to log to
     * @param category The logger category (e.g., "waypoint", "cmd")
     * @return Builder instance
     */
    public static ChatMessageBuilder info(Logger logger, String category) {
        return new ChatMessageBuilder(logger, category, Level.INFO);
    }

    /**
     * Creates a WARN level message builder.
     *
     * @param logger The logger to log to
     * @param category The logger category
     * @return Builder instance
     */
    public static ChatMessageBuilder warn(Logger logger, String category) {
        return new ChatMessageBuilder(logger, category, Level.WARN);
    }

    /**
     * Creates an ERROR level message builder.
     *
     * @param logger The logger to log to
     * @param category The logger category
     * @return Builder instance
     */
    public static ChatMessageBuilder error(Logger logger, String category) {
        return new ChatMessageBuilder(logger, category, Level.ERROR);
    }

    /**
     * Sets the message text.
     *
     * @param message The message to display
     * @return This builder
     */
    public ChatMessageBuilder message(String message) {
        this.message = message;
        return this;
    }

    /**
     * Adds a key-value pair to the message.
     *
     * @param key The key
     * @param value The value
     * @return This builder
     */
    public ChatMessageBuilder key(String key, Object value) {
        this.keyValues.add(new KeyValue(key, value));
        return this;
    }

    /**
     * Adds hover text to the message.
     *
     * @param hoverText The text to show on hover
     * @return This builder
     */
    public ChatMessageBuilder withHover(String hoverText) {
        this.hoverText = hoverText;
        return this;
    }

    /**
     * Makes the message clickable, running a command when clicked.
     *
     * @param clickCommand The command to run (e.g., "/maestro goto 100 64 200")
     * @return This builder
     */
    public ChatMessageBuilder withClick(String clickCommand) {
        this.clickCommand = clickCommand;
        return this;
    }

    /**
     * Builds and sends the message to both the logger and chat.
     *
     * <p>Logs the message with structured key-value pairs, then sends a rich component to chat with
     * the category prefix, hover, and click events.
     */
    public void send() {
        // Log to structured logger
        var logBuilder =
                switch (level) {
                    case INFO -> logger.atInfo();
                    case WARN -> logger.atWarn();
                    case ERROR -> logger.atError();
                    default -> logger.atInfo();
                };

        for (KeyValue kv : keyValues) {
            logBuilder.addKeyValue(kv.key, kv.value);
        }

        logBuilder.log(message);

        // Build rich component for chat
        if (hoverText != null || clickCommand != null) {
            MutableComponent component = Component.literal("");

            // Add category prefix
            component.append(renderer.createCategoryPrefix(category));
            component.append(" ");

            // Add message with level color
            MutableComponent messageComponent = Component.literal(message);
            ChatFormatting levelColor =
                    switch (level) {
                        case WARN -> ChatFormatting.YELLOW;
                        case ERROR -> ChatFormatting.RED;
                        default -> ChatFormatting.GRAY;
                    };
            messageComponent.setStyle(messageComponent.getStyle().withColor(levelColor));

            // Add key-value pairs
            if (!keyValues.isEmpty()) {
                messageComponent.append(" ");
                StringBuilder kvBuilder = new StringBuilder();
                for (int i = 0; i < keyValues.size(); i++) {
                    KeyValue kv = keyValues.get(i);
                    kvBuilder.append(kv.key).append("=").append(kv.value);
                    if (i < keyValues.size() - 1) {
                        kvBuilder.append(" ");
                    }
                }
                MutableComponent kvComponent = Component.literal(kvBuilder.toString());
                kvComponent.setStyle(kvComponent.getStyle().withColor(ChatFormatting.DARK_GRAY));
                messageComponent.append(kvComponent);
            }

            // Add hover event
            if (hoverText != null) {
                messageComponent.setStyle(
                        messageComponent
                                .getStyle()
                                .withHoverEvent(
                                        new HoverEvent(
                                                HoverEvent.Action.SHOW_TEXT,
                                                Component.literal(hoverText))));
            }

            // Add click event
            if (clickCommand != null) {
                messageComponent.setStyle(
                        messageComponent
                                .getStyle()
                                .withClickEvent(
                                        new ClickEvent(
                                                ClickEvent.Action.RUN_COMMAND, clickCommand)));
            }

            component.append(messageComponent);
            sender.send(component);
        }
        // If no rich components, the ChatAppender will handle sending via logger
    }

    private record KeyValue(String key, Object value) {}
}
