package maestro.task

import maestro.Agent
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.goals.GoalNear
import maestro.api.task.ITask
import maestro.api.task.PathingCommand
import maestro.api.task.PathingCommandType
import maestro.api.utils.RotationUtils
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import java.util.function.Predicate

/**
 * Attack entities matching a predicate. Paths to targets and attacks them until no matching
 * entities remain in range.
 */
class AttackTask(
    maestro: Agent,
) : TaskHelper(maestro),
    ITask {
    companion object {
        private const val MELEE_RANGE = 4.5
    }

    private var filter: Predicate<Entity>? = null
    private var targets: List<Entity> = emptyList()
    private var lastAttackTick: Int = -1

    override fun onTick(
        calcFailed: Boolean,
        isSafeToCancel: Boolean,
    ): PathingCommand? {
        targets = filter?.let { scanEntities(it) } ?: emptyList()

        if (targets.isEmpty()) {
            // No targets remaining - cancel attack
            onLostControl()
            return null
        }

        val primaryTarget = targets[0]

        // Check if in melee range (measure to nearest point on hitbox, not center)
        val distance = distanceToHitbox(primaryTarget)

        return if (distance <= MELEE_RANGE) {
            // In range - attack if cooldown ready
            if (canAttack()) {
                attack(primaryTarget)
            }
            // Stay in place while attacking
            PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
        } else {
            // Path to target - stay at comfortable attack range
            val goal: Goal = GoalNear(primaryTarget.blockPosition(), 3)
            PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH)
        }
    }

    private fun canAttack(): Boolean {
        // Check if attack cooldown is at full charge for maximum damage
        val attackStrength = ctx.player().getAttackStrengthScale(0.5f)

        // Only attack when fully charged (1.0F) to deal maximum damage and enable critical hits
        // getAttackStrengthScale() automatically accounts for weapon-specific attack speeds
        if (attackStrength < 1.0f) {
            return false
        }

        // Track last attack to prevent redundant packets when cooldown isn't ready
        val currentTick = ctx.minecraft().player!!.tickCount
        return lastAttackTick != currentTick
    }

    private fun attack(target: Entity) {
        // Calculate rotation to target (aim for center of entity)
        val targetPos = target.position().add(0.0, target.eyeHeight * 0.5, 0.0)
        val targetRot =
            RotationUtils.calcRotationFromVec3d(
                ctx.player().getEyePosition(1.0f),
                targetPos,
                ctx.playerRotations(),
            )

        // Update look behavior to rotate towards target (for visual appearance)
        maestro.lookBehavior.updateTarget(targetRot, true)

        // Attack and track the tick to prevent redundant attacks
        ctx.minecraft().gameMode!!.attack(ctx.player(), target)
        ctx.player().swing(InteractionHand.MAIN_HAND)

        // Record attack and reset cooldown timer
        lastAttackTick = ctx.minecraft().player!!.tickCount
        ctx.player().resetAttackStrengthTicker()
    }

    override fun isActive(): Boolean {
        if (filter == null) {
            return false
        }
        targets = filter?.let { scanEntities(it) } ?: emptyList()
        return targets.isNotEmpty()
    }

    override fun onLostControl() {
        filter = null
        targets = emptyList()
        lastAttackTick = -1
    }

    override fun displayName0(): String {
        if (targets.isEmpty()) {
            return "Attacking (no targets)"
        }
        return "Attacking ${targets.size} target(s)"
    }

    /**
     * Begin attacking entities matching the specified predicate. The bot will path to targets and
     * attack them until no matching entities remain in range.
     *
     * @param filter the predicate to match entities
     */
    fun attack(filter: Predicate<Entity>) {
        this.filter = filter
    }

    /**
     * @return The entities that are currently being targeted. null if not currently attacking,
     *     empty if nothing matches the predicate
     */
    fun getTargets(): List<Entity> = targets

    /** Cancels the attack behavior and clears the current target filter. */
    fun cancel() {
        onLostControl()
    }
}
