package maestro.process

import maestro.Agent
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.goals.GoalBlock
import maestro.api.pathing.goals.GoalComposite
import maestro.api.pathing.goals.GoalNear
import maestro.api.pathing.goals.GoalXZ
import maestro.api.process.IFollowProcess
import maestro.api.process.PathingCommand
import maestro.api.process.PathingCommandType
import maestro.api.utils.PackedBlockPos
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import java.util.function.Predicate

/** Follow an entity */
class FollowProcess(
    maestro: Agent,
) : MaestroProcessHelper(maestro),
    IFollowProcess {
    private var filter: Predicate<Entity>? = null
    private var cache: List<Entity>? = null
    private var into: Boolean = false // Walk straight into the target, regardless of settings

    override fun onTick(
        calcFailed: Boolean,
        isSafeToCancel: Boolean,
    ): PathingCommand {
        scanWorld()
        val currentCache = cache ?: emptyList()
        val goal = GoalComposite(*currentCache.map { towards(it) }.toTypedArray())
        return PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH)
    }

    private fun towards(following: Entity): Goal {
        val pos: BlockPos =
            if (Agent.settings().followOffsetDistance.value == 0.0 || into) {
                following.blockPosition()
            } else {
                val g =
                    GoalXZ.fromDirection(
                        following.position(),
                        Agent.settings().followOffsetDirection.value,
                        Agent.settings().followOffsetDistance.value,
                    )
                PackedBlockPos(g.x, following.position().y.toInt(), g.z).toBlockPos()
            }

        return if (into) {
            GoalBlock(pos)
        } else {
            GoalNear(pos, Agent.settings().followRadius.value)
        }
    }

    private fun followable(entity: Entity?): Boolean {
        if (entity == null) {
            return false
        }

        if (!entity.isAlive) {
            return false
        }

        if (entity == ctx.player()) {
            return false
        }

        val maxDist = Agent.settings().followTargetMaxDistance.value
        val player = ctx.player() ?: return false

        if (maxDist != 0 && entity.distanceToSqr(player) > maxDist * maxDist) {
            return false
        }

        return ctx.entitiesStream().anyMatch { it == entity }
    }

    private fun scanWorld() {
        val currentFilter = filter
        cache =
            if (currentFilter != null) {
                ctx
                    .entitiesStream()
                    .filter { followable(it) }
                    .filter { currentFilter.test(it) }
                    .distinct()
                    .toList()
            } else {
                emptyList()
            }
    }

    override fun isActive(): Boolean {
        if (filter == null) {
            return false
        }

        scanWorld()
        return !cache.isNullOrEmpty()
    }

    override fun onLostControl() {
        filter = null
        cache = null
    }

    override fun displayName0(): String = "Following $cache"

    override fun follow(filter: Predicate<Entity>) {
        this.filter = filter
        this.into = false
    }

    override fun pickup(filter: Predicate<ItemStack>) {
        this.filter =
            Predicate { e ->
                e is ItemEntity && filter.test(e.item)
            }
        this.into = true
    }

    override fun following(): List<Entity>? = cache

    override fun currentFilter(): Predicate<Entity>? = filter
}
