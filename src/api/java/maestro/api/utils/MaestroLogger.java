package maestro.api.utils;

import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logger factory for Maestro with short, scannable category names.
 *
 * <p>Provides structured logging with SLF4J 2.0+ Fluent API for key-value pairs. Replaces the
 * Helper interface logging methods with proper log levels and structured data.
 *
 * <p>Example usage:
 *
 * <pre>
 * private static final Logger log = MaestroLogger.get("path");
 *
 * log.atInfo()
 *     .addKeyValue("reason", "timeout")
 *     .addKeyValue("timeout_ms", 3400)
 *     .log("Recalculating path");
 * </pre>
 *
 * <p>Available categories: path, swim, combat, coord, mine, farm, build, cache, move, rotation,
 * event, cmd, api, waypoint, inventory
 *
 * <p>Logger configuration (DEBUG level, CONSOLE + CHAT appenders) is performed programmatically via
 * {@link LoggerConfigurator} during class initialization.
 */
public final class MaestroLogger {

    private static final Map<String, String> CATEGORIES =
            Map.ofEntries(
                    Map.entry("dev", "dev"),
                    Map.entry("path", "path"),
                    Map.entry("swim", "swim"),
                    Map.entry("combat", "combat"),
                    Map.entry("coord", "coord"),
                    Map.entry("mine", "mine"),
                    Map.entry("farm", "farm"),
                    Map.entry("build", "build"),
                    Map.entry("cache", "cache"),
                    Map.entry("move", "move"),
                    Map.entry("rotation", "rotation"),
                    Map.entry("event", "event"),
                    Map.entry("cmd", "cmd"),
                    Map.entry("api", "api"),
                    Map.entry("waypoint", "waypoint"),
                    Map.entry("inventory", "inventory"));

    static {
        // Configure all loggers programmatically on class initialization
        LoggerConfigurator.configure();
    }

    private MaestroLogger() {}

    /**
     * Get logger for a category.
     *
     * @param category Short category name (e.g., "path", "mine", "combat")
     * @return SLF4J logger instance
     * @throws IllegalArgumentException if category is not recognized
     */
    public static Logger get(String category) {
        if (!CATEGORIES.containsKey(category)) {
            throw new IllegalArgumentException(
                    "Unknown logger category: "
                            + category
                            + ". Valid categories: "
                            + CATEGORIES.keySet());
        }
        return LoggerFactory.getLogger(category);
    }

    /**
     * Check if a category exists.
     *
     * @param category Category name to check
     * @return true if category is valid
     */
    public static boolean hasCategory(String category) {
        return CATEGORIES.containsKey(category);
    }

    /**
     * Get all valid category names.
     *
     * <p>Package-private for LoggerConfigurator access.
     *
     * @return set of all category names
     */
    static Set<String> getAllCategories() {
        return CATEGORIES.keySet();
    }
}
