package maestro.process

import maestro.Agent
import maestro.api.combat.TrajectoryResult
import maestro.api.event.events.RenderEvent
import maestro.api.event.listener.AbstractGameEventListener
import maestro.api.pathing.goals.GoalNear
import maestro.api.process.IRangedCombatProcess
import maestro.api.process.PathingCommand
import maestro.api.process.PathingCommandType
import maestro.api.utils.MaestroLogger
import maestro.api.utils.Rotation
import maestro.combat.BowController
import maestro.combat.ProjectilePhysics
import maestro.combat.TargetPredictor
import maestro.combat.TrajectoryRenderer
import maestro.utils.MaestroProcessHelper
import net.minecraft.world.entity.Entity
import org.slf4j.Logger
import java.util.function.Predicate

/**
 * Process for ranged combat using bows. Manages target selection, positioning,
 * aiming with ballistic calculations, bow charging, and shooting.
 */
class RangedCombatProcess(
    maestro: Agent,
) : MaestroProcessHelper(maestro),
    IRangedCombatProcess,
    AbstractGameEventListener {
    private val log: Logger = MaestroLogger.get("combat")

    companion object {
        private const val MIN_RANGE = 8.0
        private const val MAX_RANGE = 40.0
        private const val OPTIMAL_RANGE = 20.0
    }

    /**
     * Combat states for state machine.
     */
    private enum class CombatState {
        IDLE, // No active combat
        SCANNING, // Finding and selecting target
        POSITIONING, // Moving to optimal range
        AIMING, // Calculating trajectory and rotating
        CHARGING, // Charging bow
        SHOOTING, // Releasing arrow
    }

    private var filter: Predicate<Entity>? = null
    private var targets: List<Entity> = emptyList()
    private var currentTarget: Entity? = null
    private var state: CombatState = CombatState.IDLE
    private val bowController = BowController(ctx)
    private var currentTrajectory: TrajectoryResult? = null
    private var hasValidAim: Boolean = false

    override fun onTick(
        calcFailed: Boolean,
        isSafeToCancel: Boolean,
    ): PathingCommand? {
        when (state) {
            CombatState.IDLE -> {
                // Should not reach here if isActive() is working correctly
                return null
            }

            CombatState.SCANNING -> {
                targets = filter?.let { scanEntities(it) } ?: emptyList()
                if (targets.isEmpty()) {
                    transitionState(CombatState.IDLE)
                    onLostControl()
                    return null
                }

                currentTarget = selectBestTarget(targets)
                if (currentTarget == null) {
                    transitionState(CombatState.IDLE)
                    onLostControl()
                    return null
                }

                transitionState(CombatState.POSITIONING)
                return onTick(calcFailed, isSafeToCancel) // Process positioning immediately
            }

            CombatState.POSITIONING -> {
                val target =
                    currentTarget ?: run {
                        transitionState(CombatState.SCANNING)
                        return onTick(calcFailed, isSafeToCancel)
                    }

                // Check if target is still valid
                if (!isValidTarget(target)) {
                    currentTarget = null
                    transitionState(CombatState.SCANNING)
                    return onTick(calcFailed, isSafeToCancel)
                }

                ctx.player().distanceTo(target)

                if (isInShootingRange(target)) {
                    // In good range - start aiming
                    transitionState(CombatState.AIMING)
                    return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                } else {
                    // Need to move to better range
                    val goal = GoalNear(target.blockPosition(), OPTIMAL_RANGE.toInt())
                    return PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH)
                }
            }

            CombatState.AIMING -> {
                val target =
                    currentTarget ?: run {
                        transitionState(CombatState.SCANNING)
                        return onTick(calcFailed, isSafeToCancel)
                    }

                // Check if target still valid and in range
                if (!isValidTarget(target) || !isInShootingRange(target)) {
                    currentTarget = null
                    transitionState(CombatState.SCANNING)
                    return onTick(calcFailed, isSafeToCancel)
                }

                // Select bow if not already holding
                if (!bowController.selectBow()) {
                    // No bow available
                    log.atWarn().log("No bow found in hotbar")
                    onLostControl()
                    return null
                }

                // Check for arrows
                if (!bowController.hasArrows()) {
                    log.atWarn().log("No arrows in inventory")
                    onLostControl()
                    return null
                }

                // Calculate trajectory and update aim
                updateAim(target)

                // If we have valid trajectory, start charging
                if (hasValidAim) {
                    transitionState(CombatState.CHARGING)
                    return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                } else {
                    // Can't hit target from here, reposition
                    transitionState(CombatState.POSITIONING)
                    return onTick(calcFailed, isSafeToCancel)
                }
            }

            CombatState.CHARGING -> {
                val target =
                    currentTarget ?: run {
                        bowController.cancelCharge()
                        transitionState(CombatState.SCANNING)
                        return onTick(calcFailed, isSafeToCancel)
                    }

                // Check if target still valid
                if (!isValidTarget(target) || !isInShootingRange(target)) {
                    bowController.cancelCharge()
                    currentTarget = null
                    transitionState(CombatState.SCANNING)
                    return onTick(calcFailed, isSafeToCancel)
                }

                // Start charging if not already
                if (!bowController.isCharging()) {
                    bowController.startCharging()
                }

                // Update aim while charging (for moving targets)
                updateAim(target)

                // Check if fully charged (or meets minimum threshold)
                val minimumCharge = Agent.settings().minimumBowCharge.value
                if (bowController.hasMinimumCharge(minimumCharge)) {
                    transitionState(CombatState.SHOOTING)
                    return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                }

                return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
            }

            CombatState.SHOOTING -> {
                val target =
                    currentTarget ?: run {
                        bowController.cancelCharge()
                        transitionState(CombatState.SCANNING)
                        return onTick(calcFailed, isSafeToCancel)
                    }

                // Final aim update before release
                updateAim(target)

                // Release arrow
                bowController.release()

                // Return to scanning for next target
                currentTarget = null
                currentTrajectory = null
                hasValidAim = false
                transitionState(CombatState.SCANNING)

                return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
            }
        }
    }

    private fun selectBestTarget(targets: List<Entity>): Entity? {
        // For now, select closest target
        // Future enhancement: consider threat level, line of sight, etc.
        return targets.firstOrNull()
    }

    private fun isInShootingRange(target: Entity): Boolean {
        val distance = ctx.player().distanceTo(target)
        return distance in MIN_RANGE..MAX_RANGE
    }

    private fun updateAim(target: Entity) {
        val shooterPos = ctx.player().getEyePosition(1.0f)

        // Get current bow charge velocity
        val velocity =
            if (bowController.isCharging()) {
                bowController.getChargeVelocity()
            } else {
                // Assume full charge for trajectory calculation
                ProjectilePhysics.MAX_VELOCITY
            }

        // Need minimum velocity for valid trajectory calculations
        if (velocity < 0.1) {
            hasValidAim = false
            currentTrajectory = null
            return
        }

        // Predict target position if enabled
        val targetPos =
            if (Agent.settings().predictTargetMovement.value && TargetPredictor.isPredictable(target)) {
                val iterations = Agent.settings().targetPredictionIterations.value
                TargetPredictor.predictEyePosition(target, shooterPos, velocity, iterations)
            } else {
                // Aim for center of entity
                target.position().add(0.0, target.eyeHeight * 0.5, 0.0)
            }

        // Calculate ballistic trajectory
        val pitch = ProjectilePhysics.calculatePitch(shooterPos, targetPos, velocity)
        val yaw = ProjectilePhysics.calculateYaw(shooterPos, targetPos)

        // Validate results before creating rotation
        if (pitch != null && !yaw.isNaN() && !pitch.isNaN()) {
            // Valid trajectory found
            hasValidAim = true
            val rotation = Rotation(yaw, pitch)
            maestro.lookBehavior.updateTarget(rotation, false)

            // Calculate full trajectory for rendering (always calculate if rendering is enabled)
            currentTrajectory =
                if (Agent.settings().renderTrajectory.value) {
                    ProjectilePhysics.simulateTrajectory(
                        shooterPos,
                        yaw,
                        pitch,
                        velocity,
                        ctx.world(),
                    )
                } else {
                    null
                }
        } else {
            // Target unreachable with current velocity
            hasValidAim = false
            currentTrajectory = null
        }
    }

    private fun transitionState(newState: CombatState) {
        state = newState
    }

    override fun isActive(): Boolean {
        if (filter == null) {
            state = CombatState.IDLE
            return false
        }

        // If not in IDLE state, we're actively processing
        if (state != CombatState.IDLE) {
            return true
        }

        // Check if we have targets
        targets = filter?.let { scanEntities(it) } ?: emptyList()
        if (targets.isEmpty()) {
            return false
        }

        // Start scanning
        transitionState(CombatState.SCANNING)
        return true
    }

    override fun onLostControl() {
        filter = null
        targets = emptyList()
        currentTarget = null
        currentTrajectory = null
        hasValidAim = false
        state = CombatState.IDLE

        // Cancel any ongoing bow charge
        if (bowController.isCharging()) {
            bowController.cancelCharge()
        }
    }

    override fun displayName0(): String =
        when (state) {
            CombatState.IDLE -> "Ranged Combat (idle)"
            CombatState.SCANNING -> "Scanning targets"
            CombatState.POSITIONING -> "Positioning (${currentTarget?.name?.string ?: "unknown"})"
            CombatState.AIMING -> "Aiming (${currentTarget?.name?.string ?: "unknown"})"
            CombatState.CHARGING -> {
                val progress = (bowController.getChargeProgress() * 100).toInt()
                "Charging ($progress%)"
            }

            CombatState.SHOOTING -> "Shooting"
        }

    override fun shoot(filter: Predicate<Entity>) {
        this.filter = filter
        state = CombatState.SCANNING
    }

    override fun getCurrentTarget(): Entity? = currentTarget

    override fun getTargets(): List<Entity> = targets

    override fun getCurrentTrajectory(): TrajectoryResult? = currentTrajectory

    override fun onRenderPass(event: RenderEvent) {
        TrajectoryRenderer.render(event, currentTrajectory)
    }
}
