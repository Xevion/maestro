package maestro.api.command.datatypes

import maestro.api.command.exception.CommandException
import maestro.api.command.helpers.TabCompleteHelper
import maestro.api.selector.SelectorSyntax
import maestro.api.selector.block.BlockCategory
import maestro.api.selector.block.BlockSelector
import net.minecraft.core.registries.BuiltInRegistries
import java.util.stream.Stream

/**
 * Datatype for parsing block selectors from command arguments.
 *
 * Supports:
 * - Literal block IDs: `diamond_ore`, `minecraft:oak_log`
 * - Wildcards: `*_ore`, `*spruce*`
 * - Categories: `@ores`, `@logs`
 * - Tags: `#minecraft:logs`
 */
enum class ForBlockSelector : IDatatypeFor<BlockSelector> {
    INSTANCE,
    ;

    @Throws(CommandException::class)
    override fun get(ctx: IDatatypeContext): BlockSelector {
        val input = ctx.consumer.string
        return try {
            BlockSelector.parse(input)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid block selector: ${e.message}")
        }
    }

    @Throws(CommandException::class)
    override fun tabComplete(ctx: IDatatypeContext): Stream<String> {
        val arg = ctx.consumer.peekString()

        return when {
            arg.startsWith(SelectorSyntax.CATEGORY_PREFIX) -> {
                // Complete category names
                TabCompleteHelper()
                    .append(BlockCategory.names().map { "@$it" }.stream())
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
                    val selector = BlockSelector.parse(arg)
                    val matches = selector.resolve()
                    if (matches.size <= 20) {
                        // Show matching blocks if not too many
                        TabCompleteHelper()
                            .append(
                                matches
                                    .mapNotNull {
                                        BuiltInRegistries.BLOCK.getKey(it)?.toString()
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
                // Complete with block IDs, categories, and tag prefix
                val lowerArg = arg.lowercase()
                TabCompleteHelper()
                    .append(
                        BuiltInRegistries.BLOCK
                            .keySet()
                            .stream()
                            .map { it.toString() },
                    ).append(BlockCategory.names().map { "@$it" }.stream())
                    .append("#") // Hint for tag prefix
                    .filter { completion ->
                        val lower = completion.lowercase()
                        // Match if:
                        // 1. Full string starts with input (minecraft:dirt starts with mi)
                        // 2. Path part starts with input (dirt starts with d)
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
        // Common block tags for completion
        val commonTags =
            listOf(
                "#minecraft:logs",
                "#minecraft:planks",
                "#minecraft:sand",
                "#minecraft:dirt",
                "#minecraft:stone_ore_replaceables",
                "#minecraft:leaves",
                "#minecraft:saplings",
                "#minecraft:flowers",
                "#minecraft:crops",
                "#minecraft:coal_ores",
                "#minecraft:iron_ores",
                "#minecraft:copper_ores",
                "#minecraft:gold_ores",
                "#minecraft:redstone_ores",
                "#minecraft:lapis_ores",
                "#minecraft:diamond_ores",
                "#minecraft:emerald_ores",
                "#minecraft:wool",
                "#minecraft:terracotta",
                "#minecraft:glass",
            )

        return TabCompleteHelper()
            .append(commonTags.stream())
            .filterPrefix(arg)
            .sortAlphabetically()
            .stream()
    }
}
