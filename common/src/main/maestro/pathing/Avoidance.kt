package maestro.pathing

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import maestro.Agent
import maestro.player.PlayerContext
import maestro.utils.pack
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.Spider
import net.minecraft.world.entity.monster.ZombifiedPiglin

/**
 * Pathfinding cost multiplier for avoiding dangerous areas (mob spawners, hostile mobs).
 *
 * Applies a coefficient to nodes within a specified radius of a center point, increasing
 * or decreasing the cost of pathfinding through those areas.
 */
class Avoidance {
    @PublishedApi
    internal val centerX: Int

    @PublishedApi
    internal val centerY: Int

    @PublishedApi
    internal val centerZ: Int

    @PublishedApi
    internal val coefficient: Double
    private val radius: Int

    @PublishedApi
    internal val radiusSq: Int

    constructor(center: BlockPos, coefficient: Double, radius: Int) : this(
        center.x,
        center.y,
        center.z,
        coefficient,
        radius,
    )

    constructor(centerX: Int, centerY: Int, centerZ: Int, coefficient: Double, radius: Int) {
        this.centerX = centerX
        this.centerY = centerY
        this.centerZ = centerZ
        this.coefficient = coefficient
        this.radius = radius
        this.radiusSq = radius * radius
    }

    /**
     * Calculates the cost coefficient for a given position.
     *
     * CRITICAL: This method is inline for hot-path performance during pathfinding.
     * Called frequently during path calculation.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Cost multiplier (coefficient if within radius, 1.0 otherwise)
     */
    fun coefficient(
        x: Int,
        y: Int,
        z: Int,
    ): Double {
        val xDiff = x - centerX
        val yDiff = y - centerY
        val zDiff = z - centerZ
        return if (xDiff * xDiff + yDiff * yDiff + zDiff * zDiff <= radiusSq) {
            coefficient
        } else {
            1.0
        }
    }

    /**
     * Applies this avoidance's coefficient to all nodes within radius in a spherical pattern.
     *
     * @param map The cost map to modify (block hash -> cost multiplier)
     */
    fun applySpherical(map: Long2DoubleOpenHashMap) {
        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        val hash = pack(centerX + x, centerY + y, centerZ + z).packed
                        map.put(hash, map.get(hash) * coefficient)
                    }
                }
            }
        }
    }

    companion object {
        /**
         * Creates a list of avoidance zones based on nearby mob spawners and hostile entities.
         *
         * @param ctx Player context for entity and world access
         * @return List of avoidance zones
         */
        @JvmStatic
        fun create(ctx: PlayerContext): List<Avoidance> {
            if (!Agent
                    .getPrimaryAgent()
                    .settings.avoidance.value
            ) {
                return emptyList()
            }

            val res = mutableListOf<Avoidance>()
            val mobSpawnerCoefficient =
                Agent
                    .getPrimaryAgent()
                    .settings.mobSpawnerAvoidanceCoefficient.value
            val mobCoefficient =
                Agent
                    .getPrimaryAgent()
                    .settings.mobAvoidanceCoefficient.value

            // Add avoidance for mob spawners
            if (mobSpawnerCoefficient != 1.0) {
                ctx
                    .worldData()
                    .cachedWorld
                    .getLocationsOf("mob_spawner", 1, ctx.playerFeet().x, ctx.playerFeet().z, 2)
                    .forEach { mobSpawner ->
                        res.add(
                            Avoidance(
                                mobSpawner,
                                mobSpawnerCoefficient,
                                Agent
                                    .getPrimaryAgent()
                                    .settings.mobSpawnerAvoidanceRadius.value,
                            ),
                        )
                    }
            }

            // Add avoidance for hostile mobs
            if (mobCoefficient != 1.0) {
                @Suppress("DEPRECATION")
                ctx
                    .entitiesStream()
                    .filter { entity -> entity is Mob }
                    .filter { entity ->
                        // Spiders only hostile in low light
                        entity !is Spider || ctx.player().lightLevelDependentMagicValue < 0.5f
                    }.filter { entity ->
                        // Zombie piglins only hostile if provoked
                        entity !is ZombifiedPiglin || entity.lastHurtByMob != null
                    }.filter { entity ->
                        // Endermen only hostile when provoked (isCreepy = staring at player)
                        entity !is EnderMan || entity.isCreepy
                    }.forEach { entity ->
                        res.add(
                            Avoidance(
                                entity.blockPosition(),
                                mobCoefficient,
                                Agent
                                    .getPrimaryAgent()
                                    .settings.mobAvoidanceRadius.value,
                            ),
                        )
                    }
            }

            return res
        }
    }
}
