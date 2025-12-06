package maestro.selector

/**
 * Represents the type of target selector pattern.
 */
enum class SelectorType {
    /** Exact match against a single registry entry (e.g., `diamond_ore`, `zombie`) */
    LITERAL,

    /** Glob pattern matching against registry entries (e.g., `*_ore`, `*zombie*`) */
    WILDCARD,

    /** Predefined semantic category (e.g., `@hostile`, `@ores`) */
    CATEGORY,

    /** Minecraft tag reference (e.g., `#minecraft:logs`) */
    TAG,
}
