package maestro.api.selector.block

import maestro.api.selector.CachingSelector
import maestro.api.selector.SelectorParseException
import maestro.api.selector.SelectorSyntax
import maestro.api.selector.SelectorType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/**
 * A selector for matching block types.
 *
 * Supports four syntaxes:
 * - **Literal**: `diamond_ore`, `minecraft:oak_log`
 * - **Wildcard**: `*_ore`, `*spruce*`
 * - **Category**: `@ores`, `@logs`
 * - **Tag**: `#minecraft:logs`
 *
 * Resolution is cached - calling [resolve] multiple times returns the same set.
 */
sealed class BlockSelector(
    rawInput: String,
    type: SelectorType,
) : CachingSelector<Block>(rawInput, type) {
    /**
     * Tests if a block state matches this selector.
     * Default implementation delegates to [matches] for the block.
     */
    open fun matchesState(state: BlockState): Boolean = matches(state.block)

    /**
     * Exact match against a single block type.
     */
    class Literal(
        rawInput: String,
        val block: Block,
    ) : BlockSelector(rawInput, SelectorType.LITERAL) {
        override fun computeResolve(): Set<Block> = setOf(block)

        override fun matches(target: Block): Boolean = target == block

        override fun toDisplayString(): String {
            val key = BuiltInRegistries.BLOCK.getKey(block)
            return key?.toString() ?: rawInput
        }
    }

    /**
     * Glob pattern matching against block IDs.
     */
    class Wildcard(
        rawInput: String,
        val pattern: String,
    ) : BlockSelector(rawInput, SelectorType.WILDCARD) {
        private val regex: Regex = SelectorSyntax.globToRegex(pattern)

        override fun computeResolve(): Set<Block> = BuiltInRegistries.BLOCK.filter { matches(it) }.toSet()

        override fun matches(target: Block): Boolean {
            val key = BuiltInRegistries.BLOCK.getKey(target) ?: return false
            // Match against both full ID and path (e.g., "minecraft:diamond_ore" and "diamond_ore")
            return regex.matches(key.toString()) || regex.matches(key.path)
        }

        override fun toDisplayString(): String = pattern
    }

    /**
     * Predefined semantic category.
     */
    class Category(
        rawInput: String,
        val category: BlockCategory,
    ) : BlockSelector(rawInput, SelectorType.CATEGORY) {
        override fun computeResolve(): Set<Block> = category.resolve()

        override fun matches(target: Block): Boolean = category.matches(target)

        override fun matchesState(state: BlockState): Boolean = category.matches(state)

        override fun toDisplayString(): String = "@${category.name}"
    }

    /**
     * Minecraft tag reference.
     */
    class Tag(
        rawInput: String,
        val tagKey: TagKey<Block>,
    ) : BlockSelector(rawInput, SelectorType.TAG) {
        override fun computeResolve(): Set<Block> = BuiltInRegistries.BLOCK.filter { matches(it) }.toSet()

        override fun matches(target: Block): Boolean = BuiltInRegistries.BLOCK.wrapAsHolder(target).`is`(tagKey)

        override fun toDisplayString(): String = "#${tagKey.location()}"
    }

    companion object {
        /**
         * Parses a raw input string into a BlockSelector.
         *
         * @param input The raw input (e.g., "diamond_ore", "@ores", "#minecraft:logs")
         * @return The parsed selector
         * @throws SelectorParseException if the input is invalid
         */
        @JvmStatic
        fun parse(input: String): BlockSelector {
            if (input.isBlank()) {
                throw SelectorParseException.blankInput()
            }

            return when (SelectorSyntax.detectType(input)) {
                SelectorType.CATEGORY -> {
                    val name = SelectorSyntax.extractCategoryName(input)
                    val category =
                        BlockCategory.get(name)
                            ?: throw SelectorParseException.unknownCategory(name, BlockCategory.names())
                    Category(input, category)
                }
                SelectorType.TAG -> {
                    val tagId = SelectorSyntax.extractTagId(input)
                    val location =
                        ResourceLocation.tryParse(tagId)
                            ?: throw SelectorParseException.invalidTagFormat(tagId)
                    val tagKey = TagKey.create(Registries.BLOCK, location)

                    // Validate tag exists and has members
                    val hasMembers =
                        BuiltInRegistries.BLOCK
                            .getTagOrEmpty(tagKey)
                            .iterator()
                            .hasNext()
                    if (!hasMembers) {
                        throw SelectorParseException.unknownTag(tagId)
                    }

                    Tag(input, tagKey)
                }
                SelectorType.WILDCARD -> {
                    Wildcard(input, input)
                }
                SelectorType.LITERAL -> {
                    val id = if (input.contains(':')) input else "minecraft:$input"
                    val location =
                        ResourceLocation.tryParse(id)
                            ?: throw SelectorParseException.invalidBlockId(input)
                    val block =
                        BuiltInRegistries.BLOCK
                            .getOptional(location)
                            .orElseThrow { SelectorParseException.unknownBlock(input) }
                    Literal(input, block)
                }
            }
        }
    }
}
