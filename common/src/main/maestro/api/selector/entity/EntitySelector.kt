package maestro.api.selector.entity

import maestro.api.selector.CachingSelector
import maestro.api.selector.SelectorParseException
import maestro.api.selector.SelectorSyntax
import maestro.api.selector.SelectorType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import java.util.function.Predicate

/**
 * A selector for matching entity types.
 *
 * Supports four syntaxes:
 * - **Literal**: `zombie`, `minecraft:skeleton`
 * - **Wildcard**: `*zombie*`, `*_golem`
 * - **Category**: `@hostile`, `@animals`
 * - **Tag**: `#minecraft:raiders`
 *
 * Resolution is cached - calling [resolve] multiple times returns the same set.
 */
sealed class EntitySelector(
    rawInput: String,
    type: SelectorType,
) : CachingSelector<EntityType<*>>(rawInput, type) {
    /**
     * Creates a predicate that matches entities against this selector.
     * This is more accurate than [resolve] since it uses runtime type checks.
     */
    abstract fun toPredicate(): Predicate<Entity>

    /**
     * Exact match against a single entity type.
     */
    class Literal(
        rawInput: String,
        val entityType: EntityType<*>,
    ) : EntitySelector(rawInput, SelectorType.LITERAL) {
        override fun computeResolve(): Set<EntityType<*>> = setOf(entityType)

        override fun matches(target: EntityType<*>): Boolean = target == entityType

        override fun toPredicate(): Predicate<Entity> =
            Predicate { entity ->
                entity.type == entityType
            }

        override fun toDisplayString(): String {
            val key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType)
            return key?.toString() ?: rawInput
        }
    }

    /**
     * Glob pattern matching against entity type IDs.
     */
    class Wildcard(
        rawInput: String,
        val pattern: String,
    ) : EntitySelector(rawInput, SelectorType.WILDCARD) {
        private val regex: Regex = SelectorSyntax.globToRegex(pattern)

        override fun computeResolve(): Set<EntityType<*>> = BuiltInRegistries.ENTITY_TYPE.filter { matches(it) }.toSet()

        override fun matches(target: EntityType<*>): Boolean {
            val key = BuiltInRegistries.ENTITY_TYPE.getKey(target) ?: return false
            // Match against both full ID and path (e.g., "minecraft:zombie" and "zombie")
            return regex.matches(key.toString()) || regex.matches(key.path)
        }

        override fun toPredicate(): Predicate<Entity> =
            Predicate { entity ->
                matches(entity.type)
            }

        override fun toDisplayString(): String = pattern
    }

    /**
     * Predefined semantic category.
     */
    class Category(
        rawInput: String,
        val category: EntityCategory,
    ) : EntitySelector(rawInput, SelectorType.CATEGORY) {
        override fun computeResolve(): Set<EntityType<*>> = category.resolve()

        override fun matches(target: EntityType<*>): Boolean = category.matchesType(target)

        override fun toPredicate(): Predicate<Entity> =
            Predicate { entity ->
                category.matchesEntity(entity)
            }

        override fun toDisplayString(): String = "@${category.name}"
    }

    /**
     * Minecraft tag reference.
     */
    class Tag(
        rawInput: String,
        val tagKey: TagKey<EntityType<*>>,
    ) : EntitySelector(rawInput, SelectorType.TAG) {
        override fun computeResolve(): Set<EntityType<*>> = BuiltInRegistries.ENTITY_TYPE.filter { matches(it) }.toSet()

        override fun matches(target: EntityType<*>): Boolean = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target).`is`(tagKey)

        override fun toPredicate(): Predicate<Entity> =
            Predicate { entity ->
                entity.type.`is`(tagKey)
            }

        override fun toDisplayString(): String = "#${tagKey.location()}"
    }

    companion object {
        /**
         * Parses a raw input string into an EntitySelector.
         *
         * @param input The raw input (e.g., "zombie", "@hostile", "#minecraft:raiders")
         * @return The parsed selector
         * @throws SelectorParseException if the input is invalid
         */
        fun parse(input: String): EntitySelector {
            if (input.isBlank()) {
                throw SelectorParseException.blankInput()
            }

            return when (SelectorSyntax.detectType(input)) {
                SelectorType.CATEGORY -> {
                    val name = SelectorSyntax.extractCategoryName(input)
                    val category =
                        EntityCategory.get(name)
                            ?: throw SelectorParseException.unknownCategory(name, EntityCategory.names())
                    Category(input, category)
                }

                SelectorType.TAG -> {
                    val tagId = SelectorSyntax.extractTagId(input)
                    val location =
                        ResourceLocation.tryParse(tagId)
                            ?: throw SelectorParseException.invalidTagFormat(tagId)
                    val tagKey = TagKey.create(Registries.ENTITY_TYPE, location)

                    // Validate tag exists and has members
                    val hasMembers =
                        BuiltInRegistries.ENTITY_TYPE
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
                            ?: throw SelectorParseException.invalidEntityId(input)
                    val entityType =
                        BuiltInRegistries.ENTITY_TYPE
                            .getOptional(location)
                            .orElseThrow { SelectorParseException.unknownEntity(input) }
                    Literal(input, entityType)
                }
            }
        }
    }
}
