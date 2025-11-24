package maestro.api.command.datatypes

import maestro.api.command.exception.CommandException
import maestro.api.command.helpers.TabCompleteHelper
import maestro.api.selector.SelectorSyntax
import maestro.api.selector.entity.EntityCategory
import maestro.api.selector.entity.EntitySelector
import net.minecraft.core.registries.BuiltInRegistries
import java.util.stream.Stream

/**
 * Datatype for parsing entity selectors from command arguments.
 *
 * Supports:
 * - Literal entity IDs: `zombie`, `minecraft:skeleton`
 * - Wildcards: `*zombie*`, `*_golem`
 * - Categories: `@hostile`, `@animals`
 * - Tags: `#minecraft:raiders`
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
            arg.startsWith(SelectorSyntax.CATEGORY_PREFIX) -> {
                // Complete category names
                TabCompleteHelper()
                    .append(EntityCategory.names().map { "@$it" }.stream())
                    .filterPrefix(arg)
                    .sortAlphabetically()
                    .stream()
            }
            arg.startsWith(SelectorSyntax.TAG_PREFIX) -> {
                // Complete tag names
                completeTagNames(arg)
            }
            arg.contains(SelectorSyntax.WILDCARD_CHAR) -> {
                // For wildcards, show what would match
                try {
                    val selector = EntitySelector.parse(arg)
                    val matches = selector.resolve()
                    if (matches.size <= 20) {
                        // Show matching entities if not too many
                        TabCompleteHelper()
                            .append(
                                matches
                                    .mapNotNull {
                                        BuiltInRegistries.ENTITY_TYPE.getKey(it)?.toString()
                                    }.stream(),
                            ).sortAlphabetically()
                            .stream()
                    } else {
                        // Just return the current input as valid
                        Stream.of(arg)
                    }
                } catch (e: Exception) {
                    Stream.empty()
                }
            }
            else -> {
                // Complete with entity IDs, categories, and tag prefix
                val lowerArg = arg.lowercase()
                TabCompleteHelper()
                    .append(
                        BuiltInRegistries.ENTITY_TYPE
                            .keySet()
                            .stream()
                            .map { it.toString() },
                    ).append(EntityCategory.names().map { "@$it" }.stream())
                    .append("#") // Hint for tag prefix
                    .filter { completion ->
                        val lower = completion.lowercase()
                        // Match if:
                        // 1. Full string starts with input (minecraft:zombie starts with mi)
                        // 2. Path part starts with input (zombie starts with z)
                        lower.startsWith(lowerArg) ||
                            (
                                completion.contains(":") &&
                                    completion.substringAfter(":").lowercase().startsWith(lowerArg)
                            )
                    }.sortAlphabetically()
                    .stream()
            }
        }
    }

    private fun completeTagNames(arg: String): Stream<String> {
        // Common entity tags for completion
        val commonTags =
            listOf(
                "#minecraft:raiders",
                "#minecraft:skeletons",
                "#minecraft:zombies",
                "#minecraft:beehive_inhabitors",
                "#minecraft:axolotl_always_hostiles",
                "#minecraft:axolotl_hunt_targets",
                "#minecraft:freeze_immune_entity_types",
                "#minecraft:frog_food",
                "#minecraft:illager",
                "#minecraft:undead",
            )

        return TabCompleteHelper()
            .append(commonTags.stream())
            .filterPrefix(arg)
            .sortAlphabetically()
            .stream()
    }
}
