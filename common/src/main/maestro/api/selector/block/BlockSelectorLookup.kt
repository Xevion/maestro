package maestro.api.selector.block

import maestro.api.utils.BlockOptionalMeta
import maestro.api.utils.BlockOptionalMetaLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/**
 * A collection of block selectors that can be used for filtering.
 *
 * Provides backward compatibility with [BlockOptionalMetaLookup] for
 * integration with existing processes like MineProcess.
 */
class BlockSelectorLookup(
    val selectors: List<BlockSelector>,
) {
    /**
     * Tests if a block matches any of the selectors.
     */
    fun has(block: Block): Boolean = selectors.any { it.matches(block) }

    /**
     * Tests if a block state matches any of the selectors.
     */
    fun has(state: BlockState): Boolean = selectors.any { it.matchesState(state) }

    /**
     * Resolves all selectors to their matching blocks.
     */
    fun resolveAll(): Set<Block> = selectors.flatMap { it.resolve() }.toSet()

    /**
     * Converts this lookup to a [BlockOptionalMetaLookup] for backward compatibility.
     *
     * Note: This resolves wildcards, categories, and tags to their matching blocks,
     * which may result in a large set for broad selectors like `@ores`.
     *
     * @return A BlockOptionalMetaLookup containing all resolved blocks
     */
    fun toBlockOptionalMetaLookup(): BlockOptionalMetaLookup {
        val blocks = resolveAll()
        val boms = blocks.map { BlockOptionalMeta(it) }.toTypedArray()
        return BlockOptionalMetaLookup(*boms)
    }

    /**
     * Returns a human-readable description showing all resolved blocks.
     */
    fun toDisplayString(): String {
        val resolved = resolveAll()
        return resolved
            .mapNotNull { BuiltInRegistries.BLOCK.getKey(it)?.toString() }
            .sorted()
            .joinToString(", ")
    }

    /**
     * Returns true if this lookup contains no selectors.
     */
    fun isEmpty(): Boolean = selectors.isEmpty()

    /**
     * Returns the number of selectors in this lookup.
     */
    fun size(): Int = selectors.size

    companion object {
        /**
         * Creates an empty lookup.
         */
        fun empty(): BlockSelectorLookup = BlockSelectorLookup(emptyList())

        /**
         * Creates a lookup from a single selector.
         */
        fun of(selector: BlockSelector): BlockSelectorLookup = BlockSelectorLookup(listOf(selector))

        /**
         * Creates a lookup from multiple selectors.
         */
        fun of(vararg selectors: BlockSelector): BlockSelectorLookup = BlockSelectorLookup(selectors.toList())

        /**
         * Parses multiple inputs and creates a lookup.
         *
         * @param inputs The raw inputs to parse
         * @return The lookup containing all parsed selectors
         */
        fun parse(vararg inputs: String): BlockSelectorLookup = BlockSelectorLookup(inputs.map { BlockSelector.parse(it) })
    }
}
