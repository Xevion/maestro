package maestro.api.selector.entity

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import java.util.function.Predicate

/**
 * A collection of entity selectors that can be used for filtering.
 *
 * This class provides efficient matching by combining multiple selectors
 * into a single predicate.
 */
class EntitySelectorLookup(
    val selectors: List<EntitySelector>,
) {
    /**
     * Tests if an entity type matches any of the selectors.
     */
    fun matchesType(type: EntityType<*>): Boolean = selectors.any { it.matches(type) }

    /**
     * Tests if an entity instance matches any of the selectors.
     * This uses runtime type checks which are more accurate than [matchesType].
     */
    fun matchesEntity(entity: Entity): Boolean =
        selectors.any { selector ->
            when (selector) {
                is EntitySelector.Category -> selector.category.matchesEntity(entity)
                else -> selector.matches(entity.type)
            }
        }

    /**
     * Creates a predicate for filtering entities.
     * This is compatible with AttackTask.attack().
     */
    fun toPredicate(): Predicate<Entity> =
        Predicate { entity ->
            matchesEntity(entity)
        }

    /**
     * Resolves all selectors to their matching entity types.
     */
    fun resolveAll(): Set<EntityType<*>> = selectors.flatMap { it.resolve() }.toSet()

    /**
     * Returns a human-readable description showing all resolved entity types.
     */
    fun toDisplayString(): String {
        val resolved = resolveAll()
        return resolved
            .mapNotNull { BuiltInRegistries.ENTITY_TYPE.getKey(it)?.toString() }
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
        fun empty(): EntitySelectorLookup = EntitySelectorLookup(emptyList())

        /**
         * Creates a lookup from a single selector.
         */
        fun of(selector: EntitySelector): EntitySelectorLookup = EntitySelectorLookup(listOf(selector))

        /**
         * Creates a lookup from multiple selectors.
         */
        fun of(vararg selectors: EntitySelector): EntitySelectorLookup = EntitySelectorLookup(selectors.toList())

        /**
         * Parses multiple inputs and creates a lookup.
         *
         * @param inputs The raw inputs to parse
         * @return The lookup containing all parsed selectors
         */
        fun parse(vararg inputs: String): EntitySelectorLookup = EntitySelectorLookup(inputs.map { EntitySelector.parse(it) })
    }
}
