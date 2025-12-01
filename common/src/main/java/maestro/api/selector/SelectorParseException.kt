package maestro.api.selector

/**
 * Exception thrown when selector parsing fails.
 *
 * Provides user-friendly error messages suitable for display in Minecraft chat.
 * Extends [IllegalArgumentException] for compatibility with existing error handling.
 */
class SelectorParseException(
    message: String,
    val rawInput: String,
) : IllegalArgumentException("Invalid selector '$rawInput': $message") {
    companion object {
        @JvmStatic
        fun blankInput(): SelectorParseException = SelectorParseException("selector cannot be empty", "")

        @JvmStatic
        fun unknownCategory(
            name: String,
            validCategories: Set<String>,
        ): SelectorParseException =
            SelectorParseException(
                "unknown category '@$name'. Valid: ${validCategories.joinToString(", ") { "@$it" }}",
                "@$name",
            )

        @JvmStatic
        fun unknownTag(tagId: String): SelectorParseException = SelectorParseException("unknown or empty tag", "#$tagId")

        @JvmStatic
        fun invalidTagFormat(tagId: String): SelectorParseException = SelectorParseException("invalid tag format", "#$tagId")

        @JvmStatic
        fun invalidBlockId(blockId: String): SelectorParseException = SelectorParseException("invalid block ID format", blockId)

        @JvmStatic
        fun unknownBlock(blockId: String): SelectorParseException = SelectorParseException("unknown block", blockId)

        @JvmStatic
        fun invalidEntityId(entityId: String): SelectorParseException = SelectorParseException("invalid entity ID format", entityId)

        @JvmStatic
        fun unknownEntity(entityId: String): SelectorParseException = SelectorParseException("unknown entity type", entityId)
    }
}
