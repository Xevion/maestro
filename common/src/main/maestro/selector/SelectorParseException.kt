package maestro.selector

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
        fun blankInput(): SelectorParseException = SelectorParseException("selector cannot be empty", "")

        fun unknownCategory(
            name: String,
            validCategories: Set<String>,
        ): SelectorParseException =
            SelectorParseException(
                "unknown category '@$name'. Valid: ${validCategories.joinToString(", ") { "@$it" }}",
                "@$name",
            )

        fun unknownTag(tagId: String): SelectorParseException = SelectorParseException("unknown or empty tag", "#$tagId")

        fun invalidTagFormat(tagId: String): SelectorParseException = SelectorParseException("invalid tag format", "#$tagId")

        fun invalidBlockId(blockId: String): SelectorParseException = SelectorParseException("invalid block ID format", blockId)

        fun unknownBlock(blockId: String): SelectorParseException = SelectorParseException("unknown block", blockId)

        fun invalidEntityId(entityId: String): SelectorParseException = SelectorParseException("invalid entity ID format", entityId)

        fun unknownEntity(entityId: String): SelectorParseException = SelectorParseException("unknown entity type", entityId)
    }
}
