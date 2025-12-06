package maestro.selector.block

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.StemBlock
import net.minecraft.world.level.block.state.BlockState

/**
 * Predefined block categories for semantic selection.
 *
 * Categories combine Minecraft tags and pattern matching for comprehensive coverage.
 * Resolution results are cached for performance.
 */
sealed class BlockCategory(
    val name: String,
) {
    /**
     * Returns all blocks that belong to this category.
     * Results are cached after first computation.
     */
    abstract fun resolve(): Set<Block>

    /**
     * Tests if a block belongs to this category.
     */
    abstract fun matches(block: Block): Boolean

    /**
     * Tests if a block state belongs to this category.
     * Default implementation delegates to [matches].
     */
    open fun matches(state: BlockState): Boolean = matches(state.block)

    /** All ore blocks (uses tag + pattern matching for modded ores) */
    data object Ores : BlockCategory("ores") {
        private val orePattern = Regex(".*_ore$", RegexOption.IGNORE_CASE)
        private val members: Set<Block> by lazy {
            BuiltInRegistries.BLOCK.filter { matches(it) }.toSet()
        }

        override fun matches(block: Block): Boolean {
            // Check Minecraft tag first
            val holder = BuiltInRegistries.BLOCK.wrapAsHolder(block)
            if (holder.`is`(BlockTags.COAL_ORES) ||
                holder.`is`(BlockTags.IRON_ORES) ||
                holder.`is`(BlockTags.COPPER_ORES) ||
                holder.`is`(BlockTags.GOLD_ORES) ||
                holder.`is`(BlockTags.REDSTONE_ORES) ||
                holder.`is`(BlockTags.LAPIS_ORES) ||
                holder.`is`(BlockTags.DIAMOND_ORES) ||
                holder.`is`(BlockTags.EMERALD_ORES)
            ) {
                return true
            }

            // Fall back to pattern matching for modded ores
            val key = BuiltInRegistries.BLOCK.getKey(block)
            return key?.path?.let { orePattern.matches(it) } ?: false
        }

        override fun resolve(): Set<Block> = members
    }

    /** All log blocks */
    data object Logs : BlockCategory("logs") {
        private val members: Set<Block> by lazy {
            BuiltInRegistries.BLOCK.filter { matches(it) }.toSet()
        }

        override fun matches(block: Block): Boolean {
            val holder = BuiltInRegistries.BLOCK.wrapAsHolder(block)
            return holder.`is`(BlockTags.LOGS)
        }

        override fun resolve(): Set<Block> = members
    }

    /** All plank blocks */
    data object Planks : BlockCategory("planks") {
        private val members: Set<Block> by lazy {
            BuiltInRegistries.BLOCK.filter { matches(it) }.toSet()
        }

        override fun matches(block: Block): Boolean {
            val holder = BuiltInRegistries.BLOCK.wrapAsHolder(block)
            return holder.`is`(BlockTags.PLANKS)
        }

        override fun resolve(): Set<Block> = members
    }

    /** All crop blocks (wheat, carrots, etc.) */
    data object Crops : BlockCategory("crops") {
        private val members: Set<Block> by lazy {
            BuiltInRegistries.BLOCK.filter { matches(it) }.toSet()
        }

        override fun matches(block: Block): Boolean {
            val holder = BuiltInRegistries.BLOCK.wrapAsHolder(block)
            return holder.`is`(BlockTags.CROPS) || block is CropBlock || block is StemBlock
        }

        override fun resolve(): Set<Block> = members
    }

    /** All leaves blocks */
    data object Leaves : BlockCategory("leaves") {
        private val members: Set<Block> by lazy {
            BuiltInRegistries.BLOCK.filter { matches(it) }.toSet()
        }

        override fun matches(block: Block): Boolean {
            val holder = BuiltInRegistries.BLOCK.wrapAsHolder(block)
            return holder.`is`(BlockTags.LEAVES)
        }

        override fun resolve(): Set<Block> = members
    }

    /** All stone variants */
    data object Stone : BlockCategory("stone") {
        private val members: Set<Block> by lazy {
            BuiltInRegistries.BLOCK.filter { matches(it) }.toSet()
        }

        override fun matches(block: Block): Boolean {
            val holder = BuiltInRegistries.BLOCK.wrapAsHolder(block)
            return holder.`is`(BlockTags.STONE_ORE_REPLACEABLES) ||
                holder.`is`(BlockTags.BASE_STONE_OVERWORLD) ||
                holder.`is`(BlockTags.BASE_STONE_NETHER)
        }

        override fun resolve(): Set<Block> = members
    }

    /** All dirt variants */
    data object Dirt : BlockCategory("dirt") {
        private val members: Set<Block> by lazy {
            BuiltInRegistries.BLOCK.filter { matches(it) }.toSet()
        }

        override fun matches(block: Block): Boolean {
            val holder = BuiltInRegistries.BLOCK.wrapAsHolder(block)
            return holder.`is`(BlockTags.DIRT)
        }

        override fun resolve(): Set<Block> = members
    }

    /** All sand variants */
    data object Sand : BlockCategory("sand") {
        private val members: Set<Block> by lazy {
            BuiltInRegistries.BLOCK.filter { matches(it) }.toSet()
        }

        override fun matches(block: Block): Boolean {
            val holder = BuiltInRegistries.BLOCK.wrapAsHolder(block)
            return holder.`is`(BlockTags.SAND)
        }

        override fun resolve(): Set<Block> = members
    }

    /** Flowers and small plants */
    data object Flowers : BlockCategory("flowers") {
        private val members: Set<Block> by lazy {
            BuiltInRegistries.BLOCK.filter { matches(it) }.toSet()
        }

        override fun matches(block: Block): Boolean {
            val holder = BuiltInRegistries.BLOCK.wrapAsHolder(block)
            return holder.`is`(BlockTags.FLOWERS) || holder.`is`(BlockTags.SMALL_FLOWERS)
        }

        override fun resolve(): Set<Block> = members
    }

    /** All saplings */
    data object Saplings : BlockCategory("saplings") {
        private val members: Set<Block> by lazy {
            BuiltInRegistries.BLOCK.filter { matches(it) }.toSet()
        }

        override fun matches(block: Block): Boolean {
            val holder = BuiltInRegistries.BLOCK.wrapAsHolder(block)
            return holder.`is`(BlockTags.SAPLINGS)
        }

        override fun resolve(): Set<Block> = members
    }

    companion object {
        private val registry: Map<String, BlockCategory> =
            listOf(
                Ores,
                Logs,
                Planks,
                Crops,
                Leaves,
                Stone,
                Dirt,
                Sand,
                Flowers,
                Saplings,
            ).associateBy { it.name.lowercase() }

        /**
         * Gets a category by name (case-insensitive).
         *
         * @param name The category name (e.g., "ores", "logs")
         * @return The category, or null if not found
         */
        fun get(name: String): BlockCategory? = registry[name.lowercase()]

        /**
         * Returns all registered category names.
         */
        @JvmStatic
        fun names(): Set<String> = registry.keys

        /**
         * Returns all registered categories.
         */
        fun all(): Collection<BlockCategory> = registry.values
    }
}
