package maestro.selector

/**
 * A target selector that can match registry entries using various patterns.
 *
 * Selectors support four syntaxes:
 * - **Literal**: Exact ID match (e.g., `diamond_ore`, `zombie`)
 * - **Wildcard**: Glob patterns (e.g., `*_ore`, `*spruce*`)
 * - **Category**: Semantic groups prefixed with `@` (e.g., `@hostile`, `@ores`)
 * - **Tag**: Minecraft tags prefixed with `#` (e.g., `#minecraft:logs`)
 *
 * @param T The type of registry entry this selector matches (e.g., Block, EntityType)
 */
interface TargetSelector<T> {
    /** The original user input that created this selector */
    val rawInput: String

    /** The type of selector pattern */
    val type: SelectorType

    /**
     * Resolves this selector to a set of matching registry entries.
     *
     * For wildcards and categories, this may return many entries.
     * For literals, this returns a single entry.
     *
     * @return Set of all matching entries
     */
    fun resolve(): Set<T>

    /**
     * Tests if the given target matches this selector.
     *
     * @param target The target to test
     * @return true if the target matches
     */
    fun matches(target: T): Boolean

    /**
     * Returns a human-readable description of what this selector matches.
     */
    fun toDisplayString(): String
}
