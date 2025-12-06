package maestro.command.datatypes

import maestro.command.exception.CommandException
import maestro.command.helpers.FuzzySearchHelper
import maestro.command.helpers.TabCompleteHelper
import maestro.selector.SelectorSyntax
import maestro.selector.entity.EntityCategory
import maestro.selector.entity.EntitySelector
import net.minecraft.core.registries.BuiltInRegistries
import java.util.stream.Stream

/**
 * Datatype for parsing entity selectors from command arguments.
 *
 * Supports:
 * - Literal entity IDs: `zombie`, `minecraft:skeleton`
 * - Wildcards: `*zombie*`, `*_golem`
 * - Categories: `@hostile`, `@animals`
 * - Tags: `#minecraft:raiders` or `minecraft:raiders` (# prefix optional)
 */
enum class ForEntitySelector : IDatatypeFor<EntitySelector> {
    INSTANCE,
    ;

    @Throws(CommandException::class)
    override fun get(ctx: IDatatypeContext): EntitySelector {
        val input = ctx.consumer.string
        return try {
            EntitySelector.parse(input)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid entity selector: ${e.message}")
        }
    }

    @Throws(CommandException::class)
    override fun tabComplete(ctx: IDatatypeContext): Stream<String> {
        val arg = ctx.consumer.peekString()

        return when {
            // Category prefix - search categories only
            arg.startsWith(SelectorSyntax.CATEGORY_PREFIX) -> {
                completeCategoriesFuzzy(arg)
            }
            // Tag prefix - backward compat, search tags only
            arg.startsWith("#") -> {
                completeTagsFuzzy(arg.drop(1))
            }
            // Wildcard - runtime resolution
            arg.contains(SelectorSyntax.WILDCARD_CHAR) -> {
                completeWildcard(arg)
            }
            // Namespace - search entities + tags
            arg.contains(":") -> {
                completeNamespaced(arg)
            }
            // No prefix - cross-type fuzzy search (entities + categories + tags)
            else -> {
                completeAll(arg)
            }
        }
    }

    /**
     * Retrieves all entity tags from the Minecraft registry.
     *
     * @return List of tag IDs (e.g., "minecraft:undead", "minecraft:raiders")
     */
    private fun getAllEntityTags(): List<String> =
        BuiltInRegistries.ENTITY_TYPE
            .getTags()
            .map { it.key().location().toString() }
            .toList()

    /**
     * Complete category names with fuzzy search.
     *
     * @param arg The input with @ prefix (e.g., "@hos")
     * @return Stream of matching categories with @ prefix
     */
    private fun completeCategoriesFuzzy(arg: String): Stream<String> {
        val query = arg.drop(1) // Remove @ prefix
        return TabCompleteHelper()
            .append(EntityCategory.names().map { "@$it" }.stream())
            .filterFuzzy(query, 70, 10)
            .stream()
    }

    /**
     * Complete entity tags with fuzzy search.
     *
     * @param query The tag query without # prefix (e.g., "minecraft:und")
     * @return Stream of matching tags with # prefix
     */
    private fun completeTagsFuzzy(query: String): Stream<String> {
        val tags = getAllEntityTags()
        val matches = FuzzySearchHelper.search(query, tags, 60, 15)
        return matches.map { "#$it" }.stream() // Add # for display
    }

    /**
     * Complete wildcard patterns by resolving and showing matches.
     *
     * @param arg The wildcard pattern (e.g., "*zombie*", "*_golem")
     * @return Stream of matching entity IDs
     */
    private fun completeWildcard(arg: String): Stream<String> =
        try {
            val selector = EntitySelector.parse(arg)
            val matches = selector.resolve()
            if (matches.size <= 20) {
                TabCompleteHelper()
                    .append(
                        matches
                            .mapNotNull {
                                BuiltInRegistries.ENTITY_TYPE.getKey(it)?.toString()
                            }.stream(),
                    ).sortAlphabetically()
                    .stream()
            } else {
                Stream.of(arg)
            }
        } catch (e: Exception) {
            Stream.empty()
        }

    /**
     * Complete entities and tags for namespaced queries.
     *
     * @param query The namespaced query (e.g., "minecraft:cr")
     * @return Stream of matching entities and tags
     */
    private fun completeNamespaced(query: String): Stream<String> {
        val candidates = mutableListOf<String>()

        // Add all entity IDs
        candidates.addAll(BuiltInRegistries.ENTITY_TYPE.keySet().map { it.toString() })

        // Add all tags with # prefix
        candidates.addAll(getAllEntityTags().map { "#$it" })

        return FuzzySearchHelper.searchStream(query, candidates.stream(), 70, 15)
    }

    /**
     * Complete all entity types: entities, categories, and tags.
     * Uses fuzzy search across all candidate types.
     *
     * @param query The query string (e.g., "creeper", "hos", "und")
     * @return Stream of matching suggestions across all types
     */
    private fun completeAll(query: String): Stream<String> {
        val candidates = mutableListOf<String>()

        // Add all entity IDs
        candidates.addAll(BuiltInRegistries.ENTITY_TYPE.keySet().map { it.toString() })

        // Add all categories with @ prefix
        candidates.addAll(EntityCategory.names().map { "@$it" })

        // Add all tags with # prefix
        candidates.addAll(getAllEntityTags().map { "#$it" })

        return FuzzySearchHelper.searchStream(query, candidates.stream(), 60, 20)
    }
}
