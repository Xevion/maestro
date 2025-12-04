package maestro.api.selector.entity

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.ambient.AmbientCreature
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.WaterAnimal
import net.minecraft.world.entity.animal.horse.AbstractHorse
import net.minecraft.world.entity.boss.EnderDragonPart
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.npc.AbstractVillager

/**
 * Predefined entity categories for semantic selection.
 *
 * Categories support both:
 * - Type-based matching via [matchesType] for registry lookups
 * - Instance-based matching via [matchesEntity] for runtime entity checks
 *
 * Resolution results are cached for performance.
 */
sealed class EntityCategory(
    val name: String,
) {
    /**
     * Returns all entity types that belong to this category.
     * Results are cached after first computation.
     */
    abstract fun resolve(): Set<EntityType<*>>

    /**
     * Tests if an entity type belongs to this category.
     * For some categories, this may be approximate since we can't spawn entities to check.
     */
    abstract fun matchesType(type: EntityType<*>): Boolean

    /**
     * Tests if a live entity instance belongs to this category.
     * This is more accurate than [matchesType] since we can check inheritance.
     */
    abstract fun matchesEntity(entity: Entity): Boolean

    /** Entities that attack players unprovoked */
    data object Hostile : EntityCategory("hostile") {
        private val members: Set<EntityType<*>> by lazy {
            BuiltInRegistries.ENTITY_TYPE.filter { matchesType(it) }.toSet()
        }

        override fun matchesType(type: EntityType<*>): Boolean = type.category == MobCategory.MONSTER

        override fun matchesEntity(entity: Entity): Boolean = entity is Enemy

        override fun resolve(): Set<EntityType<*>> = members
    }

    /** Entities that never attack players */
    data object Passive : EntityCategory("passive") {
        private val passiveCategories =
            setOf(
                MobCategory.CREATURE,
                MobCategory.AMBIENT,
                MobCategory.WATER_CREATURE,
                MobCategory.WATER_AMBIENT,
                MobCategory.UNDERGROUND_WATER_CREATURE,
                MobCategory.AXOLOTLS,
            )

        private val members: Set<EntityType<*>> by lazy {
            BuiltInRegistries.ENTITY_TYPE.filter { matchesType(it) }.toSet()
        }

        override fun matchesType(type: EntityType<*>): Boolean = type.category in passiveCategories

        override fun matchesEntity(entity: Entity): Boolean = entity is Animal || entity is AmbientCreature || entity is WaterAnimal

        override fun resolve(): Set<EntityType<*>> = members
    }

    /** Entities that attack only when provoked */
    data object Neutral : EntityCategory("neutral") {
        // These are neutral mobs that will attack when provoked
        private val neutralTypes =
            setOf(
                "minecraft:wolf",
                "minecraft:bee",
                "minecraft:polar_bear",
                "minecraft:llama",
                "minecraft:trader_llama",
                "minecraft:panda",
                "minecraft:iron_golem",
                "minecraft:dolphin",
                "minecraft:enderman",
                "minecraft:zombified_piglin",
                "minecraft:piglin",
                "minecraft:goat",
            )

        private val members: Set<EntityType<*>> by lazy {
            BuiltInRegistries.ENTITY_TYPE.filter { matchesType(it) }.toSet()
        }

        override fun matchesType(type: EntityType<*>): Boolean {
            val key = BuiltInRegistries.ENTITY_TYPE.getKey(type)
            return key?.toString() in neutralTypes
        }

        override fun matchesEntity(entity: Entity): Boolean {
            val key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)
            return key?.toString() in neutralTypes
        }

        override fun resolve(): Set<EntityType<*>> = members
    }

    /** All animal entities (cows, pigs, etc.) */
    data object Animals : EntityCategory("animals") {
        private val members: Set<EntityType<*>> by lazy {
            BuiltInRegistries.ENTITY_TYPE.filter { matchesType(it) }.toSet()
        }

        override fun matchesType(type: EntityType<*>): Boolean = type.category == MobCategory.CREATURE

        override fun matchesEntity(entity: Entity): Boolean = entity is Animal

        override fun resolve(): Set<EntityType<*>> = members
    }

    /** All monster entities */
    data object Monsters : EntityCategory("monsters") {
        private val members: Set<EntityType<*>> by lazy {
            BuiltInRegistries.ENTITY_TYPE.filter { matchesType(it) }.toSet()
        }

        override fun matchesType(type: EntityType<*>): Boolean = type.category == MobCategory.MONSTER

        override fun matchesEntity(entity: Entity): Boolean = entity is Monster

        override fun resolve(): Set<EntityType<*>> = members
    }

    /** Villagers and wandering traders */
    data object Villagers : EntityCategory("villagers") {
        private val members: Set<EntityType<*>> by lazy {
            BuiltInRegistries.ENTITY_TYPE.filter { matchesType(it) }.toSet()
        }

        override fun matchesType(type: EntityType<*>): Boolean {
            val key = BuiltInRegistries.ENTITY_TYPE.getKey(type)?.toString()
            return key == "minecraft:villager" || key == "minecraft:wandering_trader"
        }

        override fun matchesEntity(entity: Entity): Boolean = entity is AbstractVillager

        override fun resolve(): Set<EntityType<*>> = members
    }

    /** Common farm animals (cow, pig, sheep, chicken, etc.) */
    data object FarmAnimals : EntityCategory("farm_animals") {
        private val farmTypes =
            setOf(
                "minecraft:cow",
                "minecraft:pig",
                "minecraft:sheep",
                "minecraft:chicken",
                "minecraft:rabbit",
                "minecraft:mooshroom",
                "minecraft:goat",
            )

        private val members: Set<EntityType<*>> by lazy {
            BuiltInRegistries.ENTITY_TYPE.filter { matchesType(it) }.toSet()
        }

        override fun matchesType(type: EntityType<*>): Boolean {
            val key = BuiltInRegistries.ENTITY_TYPE.getKey(type)
            return key?.toString() in farmTypes
        }

        override fun matchesEntity(entity: Entity): Boolean = matchesType(entity.type)

        override fun resolve(): Set<EntityType<*>> = members
    }

    /** Tameable and rideable pets */
    data object Pets : EntityCategory("pets") {
        private val petTypes =
            setOf(
                "minecraft:wolf",
                "minecraft:cat",
                "minecraft:parrot",
                "minecraft:horse",
                "minecraft:donkey",
                "minecraft:mule",
                "minecraft:llama",
            )

        private val members: Set<EntityType<*>> by lazy {
            BuiltInRegistries.ENTITY_TYPE.filter { matchesType(it) }.toSet()
        }

        override fun matchesType(type: EntityType<*>): Boolean {
            val key = BuiltInRegistries.ENTITY_TYPE.getKey(type)?.toString()
            return key in petTypes
        }

        override fun matchesEntity(entity: Entity): Boolean = entity is TamableAnimal || entity is AbstractHorse

        override fun resolve(): Set<EntityType<*>> = members
    }

    /** Boss mobs (ender dragon, wither, etc.) */
    data object Bosses : EntityCategory("bosses") {
        private val bossTypes =
            setOf(
                "minecraft:ender_dragon",
                "minecraft:wither",
                "minecraft:elder_guardian",
                "minecraft:warden",
            )

        private val members: Set<EntityType<*>> by lazy {
            BuiltInRegistries.ENTITY_TYPE.filter { matchesType(it) }.toSet()
        }

        override fun matchesType(type: EntityType<*>): Boolean {
            val key = BuiltInRegistries.ENTITY_TYPE.getKey(type)
            return key?.toString() in bossTypes
        }

        override fun matchesEntity(entity: Entity): Boolean =
            entity is EnderDragon ||
                entity is EnderDragonPart ||
                entity is WitherBoss ||
                entity is Warden ||
                matchesType(entity.type)

        override fun resolve(): Set<EntityType<*>> = members
    }

    /** Undead mobs (zombies, skeletons, etc.) */
    data object Undead : EntityCategory("undead") {
        private val undeadTypes =
            setOf(
                "minecraft:zombie",
                "minecraft:zombie_villager",
                "minecraft:husk",
                "minecraft:drowned",
                "minecraft:zombified_piglin",
                "minecraft:zoglin",
                "minecraft:skeleton",
                "minecraft:stray",
                "minecraft:wither_skeleton",
                "minecraft:bogged",
                "minecraft:phantom",
                "minecraft:wither",
            )

        private val members: Set<EntityType<*>> by lazy {
            BuiltInRegistries.ENTITY_TYPE.filter { matchesType(it) }.toSet()
        }

        override fun matchesType(type: EntityType<*>): Boolean {
            val key = BuiltInRegistries.ENTITY_TYPE.getKey(type)?.toString() ?: return false
            return key in undeadTypes ||
                key.contains("zombie") ||
                key.contains("skeleton")
        }

        override fun matchesEntity(entity: Entity): Boolean = matchesType(entity.type)

        override fun resolve(): Set<EntityType<*>> = members
    }

    /** Arthropod mobs (spiders, silverfish, etc.) */
    data object Arthropods : EntityCategory("arthropods") {
        private val arthropodTypes =
            setOf(
                "minecraft:spider",
                "minecraft:cave_spider",
                "minecraft:silverfish",
                "minecraft:endermite",
                "minecraft:bee",
            )

        private val members: Set<EntityType<*>> by lazy {
            BuiltInRegistries.ENTITY_TYPE.filter { matchesType(it) }.toSet()
        }

        override fun matchesType(type: EntityType<*>): Boolean {
            val key = BuiltInRegistries.ENTITY_TYPE.getKey(type)?.toString() ?: return false
            return key in arthropodTypes || key.contains("spider")
        }

        override fun matchesEntity(entity: Entity): Boolean = matchesType(entity.type)

        override fun resolve(): Set<EntityType<*>> = members
    }

    companion object {
        private val registry: Map<String, EntityCategory> =
            listOf(
                Hostile,
                Passive,
                Neutral,
                Animals,
                Monsters,
                Villagers,
                FarmAnimals,
                Pets,
                Bosses,
                Undead,
                Arthropods,
            ).associateBy { it.name.lowercase() }

        /**
         * Gets a category by name (case-insensitive).
         *
         * @param name The category name (e.g., "hostile", "animals")
         * @return The category, or null if not found
         */
        fun get(name: String): EntityCategory? = registry[name.lowercase()]

        /**
         * Returns all registered category names.
         */
        @JvmStatic
        fun names(): Set<String> = registry.keys

        /**
         * Returns all registered categories.
         */
        fun all(): Collection<EntityCategory> = registry.values
    }
}
