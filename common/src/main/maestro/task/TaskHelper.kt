package maestro.task

import maestro.Agent
import maestro.api.player.PlayerContext
import maestro.api.task.ITask
import maestro.api.utils.Helper
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import java.util.Comparator
import java.util.function.Predicate
import java.util.stream.Collectors
import kotlin.math.sqrt

abstract class TaskHelper(
    @JvmField protected val maestro: Agent,
) : ITask,
    Helper {
    @JvmField protected val ctx: PlayerContext = maestro.playerContext

    override fun isTemporary(): Boolean = false

    /**
     * Scans entities matching the filter, sorted by distance from player.
     * @param filter Predicate to filter entities
     * @return List of entities sorted by distance (closest first)
     */
    protected fun scanEntities(filter: Predicate<Entity>): List<Entity> =
        ctx
            .entitiesStream()
            .filter { isValidTarget(it) }
            .filter(filter)
            .sorted(Comparator.comparingDouble { ctx.player().distanceToSqr(it) })
            .collect(Collectors.toList())

    /**
     * Checks if entity is a valid combat/interaction target.
     * @param entity The entity to check
     * @return true if entity is alive, not self, and is LivingEntity
     */
    protected fun isValidTarget(entity: Entity?): Boolean =
        entity != null &&
            entity != ctx.player() &&
            entity.isAlive &&
            entity is LivingEntity

    /**
     * Calculates distance to nearest point on entity's hitbox (more accurate than center).
     * @param entity The target entity
     * @return Distance to nearest point on hitbox
     */
    protected fun distanceToHitbox(entity: Entity): Double {
        val hitbox = entity.boundingBox
        val player = ctx.player()
        val closestX = Mth.clamp(player.x, hitbox.minX, hitbox.maxX)
        val closestY = Mth.clamp(player.y, hitbox.minY, hitbox.maxY)
        val closestZ = Mth.clamp(player.z, hitbox.minZ, hitbox.maxZ)
        return sqrt(player.distanceToSqr(closestX, closestY, closestZ))
    }
}
