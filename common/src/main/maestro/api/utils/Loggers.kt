package maestro.api.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Typesafe logger enum for Maestro with short, scannable category names.
 *
 * Provides structured logging with SLF4J 2.0+ Fluent API for key-value pairs.
 *
 * Example usage:
 * ```
 * private val log = Loggers.Path.get()
 *
 * log.atInfo()
 *     .addKeyValue("reason", "timeout")
 *     .addKeyValue("timeout_ms", 3400)
 *     .log("Recalculating path")
 * ```
 *
 * Logger configuration (DEBUG level, CONSOLE + CHAT appenders) is performed programmatically via
 * [LoggerConfigurator] during class initialization.
 */
enum class Loggers(
    private val category: String,
) {
    /** Development/debugging logger */
    Dev("dev"),

    /** Pathfinding operations */
    Path("path"),

    /** Swimming movement */
    Swim("swim"),

    /** Combat AI */
    Combat("combat"),

    /** Coordinate operations */
    Coord("coord"),

    /** Mining tasks */
    Mine("mine"),

    /** Farming tasks */
    Farm("farm"),

    /** Building/schematic tasks */
    Build("build"),

    /** Cache operations */
    Cache("cache"),

    /** Movement operations */
    Move("move"),

    /** Rotation management */
    Rotation("rotation"),

    /** Event system */
    Event("event"),

    /** Command processing */
    Cmd("cmd"),

    /** API operations */
    Api("api"),

    /** Waypoint management */
    Waypoint("waypoint"),

    /** Inventory operations */
    Inventory("inventory"),
    ;

    /**
     * Get the SLF4J logger instance for this category.
     *
     * @return SLF4J logger instance
     */
    fun get(): Logger = LoggerFactory.getLogger(category)

    companion object {
        init {
            // Configure all loggers programmatically on class initialization
            LoggerConfigurator.configure()
        }

        /**
         * Get all category names for programmatic configuration.
         *
         * Package-private for LoggerConfigurator access.
         *
         * @return set of all category names
         */
        @JvmStatic
        fun getAllCategories(): Set<String> = entries.map { it.category }.toSet()

        /**
         * Find logger enum by category name.
         *
         * @param category Category name to find
         * @return Logger enum value or null if not found
         */
        @JvmStatic
        fun fromCategory(category: String): Loggers? = entries.find { it.category == category }

        /**
         * Check if a category exists.
         *
         * @param category Category name to check
         * @return true if category is valid
         */
        @JvmStatic
        fun hasCategory(category: String): Boolean = entries.any { it.category == category }
    }
}
