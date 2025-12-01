package maestro.utils.chat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.event.KeyValuePair;

/**
 * Renders SLF4J log events as Minecraft chat Components with category prefixes and structured
 * formatting.
 *
 * <p>Format: {@code [category] message key1=value1 key2=value2}
 *
 * <p>Category prefixes are colored by category, message text is colored by level (INFO=grey,
 * WARN=yellow, ERROR=red), and key-value pairs are displayed inline in dark grey.
 */
public class ChatMessageRenderer {

    private static final Map<String, ChatFormatting> CATEGORY_COLORS =
            Map.ofEntries(
                    Map.entry("cmd", ChatFormatting.AQUA),
                    Map.entry("path", ChatFormatting.GREEN),
                    Map.entry("mine", ChatFormatting.GOLD),
                    Map.entry("farm", ChatFormatting.DARK_GREEN),
                    Map.entry("build", ChatFormatting.YELLOW),
                    Map.entry("combat", ChatFormatting.RED),
                    Map.entry("swim", ChatFormatting.BLUE),
                    Map.entry("cache", ChatFormatting.LIGHT_PURPLE),
                    Map.entry("move", ChatFormatting.WHITE),
                    Map.entry("rotation", ChatFormatting.GRAY),
                    Map.entry("event", ChatFormatting.DARK_AQUA),
                    Map.entry("api", ChatFormatting.DARK_GRAY),
                    Map.entry("waypoint", ChatFormatting.LIGHT_PURPLE),
                    Map.entry("inventory", ChatFormatting.GOLD));

    /**
     * Renders a log event as a Minecraft Component.
     *
     * @param event The SLF4J logging event
     * @return Formatted Component ready to send to chat
     */
    public Component render(ILoggingEvent event) {
        String category = event.getLoggerName();
        String message = event.getFormattedMessage();
        List<KeyValuePair> keyValuePairs = event.getKeyValuePairs();
        Level level = event.getLevel();

        MutableComponent result = Component.literal("");

        // Add category prefix: [category]
        result.append(createCategoryPrefix(category));
        result.append(" ");

        // Add message with level-based color
        MutableComponent messageComponent = Component.literal(message);
        messageComponent.setStyle(messageComponent.getStyle().withColor(getLevelColor(level)));
        result.append(messageComponent);

        // Add key-value pairs inline
        if (keyValuePairs != null && !keyValuePairs.isEmpty()) {
            result.append(" ");
            result.append(formatKeyValuePairs(keyValuePairs));
        }

        return result;
    }

    /**
     * Creates a colored category prefix component.
     *
     * @param category The logger category (e.g., "cmd", "mine", "path")
     * @return Component like [cmd] or [mine] with category-specific color
     */
    public Component createCategoryPrefix(String category) {
        ChatFormatting color = CATEGORY_COLORS.getOrDefault(category, ChatFormatting.DARK_PURPLE);

        MutableComponent prefix = Component.literal("[");
        prefix.setStyle(prefix.getStyle().withColor(color));

        MutableComponent categoryText = Component.literal(category);
        categoryText.setStyle(categoryText.getStyle().withColor(color));

        MutableComponent suffix = Component.literal("]");
        suffix.setStyle(suffix.getStyle().withColor(color));

        MutableComponent result = Component.literal("");
        result.append(prefix);
        result.append(categoryText);
        result.append(suffix);

        return result;
    }

    /**
     * Gets the chat color for a log level.
     *
     * @param level The Logback log level
     * @return ChatFormatting color (INFO=grey, WARN=yellow, ERROR=red)
     */
    private ChatFormatting getLevelColor(Level level) {
        if (level.isGreaterOrEqual(Level.ERROR)) {
            return ChatFormatting.RED;
        } else if (level.isGreaterOrEqual(Level.WARN)) {
            return ChatFormatting.YELLOW;
        } else {
            return ChatFormatting.GRAY;
        }
    }

    /**
     * Formats key-value pairs with colored keys and values for better readability.
     *
     * @param pairs List of key-value pairs from the log event
     * @return Component with keys in dark aqua and values in gray
     */
    private Component formatKeyValuePairs(List<KeyValuePair> pairs) {
        MutableComponent result = Component.literal("");

        for (int i = 0; i < pairs.size(); i++) {
            KeyValuePair pair = pairs.get(i);

            // Key in dark aqua
            MutableComponent keyComponent = Component.literal(pair.key);
            keyComponent.setStyle(keyComponent.getStyle().withColor(ChatFormatting.DARK_AQUA));
            result.append(keyComponent);

            // Equals sign in dark gray
            MutableComponent equalsComponent = Component.literal("=");
            equalsComponent.setStyle(
                    equalsComponent.getStyle().withColor(ChatFormatting.DARK_GRAY));
            result.append(equalsComponent);

            // Value in gray
            MutableComponent valueComponent = Component.literal(String.valueOf(pair.value));
            valueComponent.setStyle(valueComponent.getStyle().withColor(ChatFormatting.GRAY));
            result.append(valueComponent);

            // Space separator between pairs
            if (i < pairs.size() - 1) {
                result.append(" ");
            }
        }

        return result;
    }
}
