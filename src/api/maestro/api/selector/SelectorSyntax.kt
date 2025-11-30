package maestro.api.selector

/**
 * Constants and utilities for selector syntax parsing.
 */
object SelectorSyntax {
    /** Prefix for category selectors (e.g., @hostile, @ores) */
    const val CATEGORY_PREFIX = '@'

    /** Prefix for Minecraft tag selectors (e.g., #minecraft:logs) */
    const val TAG_PREFIX = '#'

    /** Wildcard character for glob patterns */
    const val WILDCARD_CHAR = '*'

    /** Single character wildcard (optional, for future use) */
    const val SINGLE_WILDCARD_CHAR = '?'

    /**
     * Determines the selector type from raw input.
     */
    fun detectType(input: String): SelectorType =
        when {
            input.startsWith(CATEGORY_PREFIX) -> SelectorType.CATEGORY
            input.startsWith(TAG_PREFIX) -> SelectorType.TAG
            input.contains(WILDCARD_CHAR) || input.contains(SINGLE_WILDCARD_CHAR) -> SelectorType.WILDCARD
            else -> SelectorType.LITERAL
        }

    /**
     * Converts a glob pattern to a regex pattern.
     *
     * - `*` matches any sequence of characters
     * - `?` matches any single character
     * - Other special regex characters are escaped
     *
     * @param glob The glob pattern (e.g., `*_ore`, `*spruce*`)
     * @return A compiled Regex
     */
    fun globToRegex(glob: String): Regex {
        val regex =
            buildString {
                append('^')
                for (char in glob) {
                    when (char) {
                        '*' -> append(".*")
                        '?' -> append(".")
                        '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> {
                            append('\\')
                            append(char)
                        }

                        else -> append(char)
                    }
                }
                append('$')
            }
        return Regex(regex, RegexOption.IGNORE_CASE)
    }

    /**
     * Extracts the category name from a category selector input.
     *
     * @param input The raw input (e.g., "@hostile")
     * @return The category name (e.g., "hostile")
     */
    fun extractCategoryName(input: String): String {
        require(input.startsWith(CATEGORY_PREFIX)) { "Input must start with $CATEGORY_PREFIX" }
        return input.substring(1)
    }

    /**
     * Extracts the tag ID from a tag selector input.
     *
     * @param input The raw input (e.g., "#minecraft:logs")
     * @return The tag ID (e.g., "minecraft:logs")
     */
    fun extractTagId(input: String): String {
        require(input.startsWith(TAG_PREFIX)) { "Input must start with $TAG_PREFIX" }
        return input.substring(1)
    }
}
