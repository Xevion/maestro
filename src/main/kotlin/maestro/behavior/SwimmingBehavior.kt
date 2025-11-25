package maestro.behavior

import maestro.Agent
import maestro.api.utils.BetterBlockPos
import maestro.api.utils.RotationUtils
import maestro.api.utils.input.Input
import maestro.pathing.movement.MovementState
import net.minecraft.world.phys.Vec3

/**
 * Handles Minecraft 1.13+ swimming behavior when the bot is in water.
 *
 * Minecraft 1.13 introduced a new swimming mechanic where players can swim horizontally underwater
 * by sprinting + moving forward while in water. This triggers the "swimming" pose/state which allows
 * staying underwater and moving efficiently.
 *
 * Implementation uses target velocity with exponential damping to prevent velocity compounding,
 * and a state machine with hysteresis to prevent pitch oscillation.
 */
class SwimmingBehavior(
    maestro: Agent,
) : Behavior(maestro) {
    /** Pitch control state machine (prevents oscillation via hysteresis) */
    private enum class SwimPitchState {
        ASCENDING, // Swimming upward
        NEUTRAL, // Swimming horizontally
        DESCENDING, // Swimming downward
    }

    private var pitchState = SwimPitchState.NEUTRAL

    // Hysteresis thresholds (prevent rapid state changes)
    private val ascendThreshold = 0.8 // Must be >0.8 blocks above to start ascending
    private val descendThreshold = -0.8 // Must be >0.8 blocks below to start descending
    private val neutralBuffer = 0.3 // Hysteresis buffer zone

    /** Returns true if player is fully submerged underwater. */
    fun isSubmerged(): Boolean = ctx.player().isUnderWater

    /** Returns true if player is touching water (may be at surface). */
    fun isInWater(): Boolean = ctx.player().isInWater

    /**
     * Determines if enhanced swimming should be activated.
     * Requires player to be in water and the enhanced swimming setting enabled.
     */
    fun shouldActivateSwimming(): Boolean = isInWater() && Agent.settings().enhancedSwimming.value

    /**
     * Apply swimming inputs to trigger Minecraft 1.13+ swimming state.
     * This sets sprint + forward inputs + pitch control, which causes the player to enter
     * swimming pose and stay horizontal underwater.
     *
     * Key to MC 1.13+ swimming:
     * - Sprint + Forward = initiate swimming
     * - Pitch down (look down) = stay underwater and maintain horizontal swimming pose
     * - Pitch up = surface
     *
     * Uses target velocity approach with exponential damping to prevent velocity compounding.
     * Uses state machine with hysteresis to prevent pitch oscillation.
     *
     * @param state The movement state to apply swimming inputs to
     * @param targetY The Y coordinate we're trying to reach (for pitch adjustment)
     */
    fun applySwimmingInputs(
        state: MovementState,
        targetY: Double,
    ) {
        val player = ctx.player()
        val currentY = player.y
        val deltaY = targetY - currentY

        // CRITICAL FIX: Trigger vanilla swimming state and pose
        // This enables true Minecraft 1.13+ swimming physics (not just velocity changes)
        player.setSwimming(true)
        player.setPose(net.minecraft.world.entity.Pose.SWIMMING)

        // Activate swimming mode (enables free-look camera)
        (maestro as Agent).setSwimmingActive(true)

        // Core swimming inputs: Sprint + Forward = swimming pose in MC 1.13+
        state.setInput(Input.SPRINT, true)
        state.setInput(Input.MOVE_FORWARD, true)

        // Calculate pitch using state machine with hysteresis (prevents oscillation)
        val targetPitch = calculatePitchWithHysteresis(deltaY)
        val currentYaw = player.yRot

        // Queue rotation via RotationManager (bot controls direction)
        val rotationMgr = (maestro as Agent).getRotationManager()
        rotationMgr.queue(
            currentYaw,
            targetPitch,
            RotationManager.Priority.NORMAL,
        ) {
            // Callback: apply velocity after rotation
            // Guard against executing callback after swimming stops
            if ((maestro as Agent).isSwimmingActive() && isInWater()) {
                applyTargetVelocity(currentYaw, targetPitch)
            }
        }
    }

    /**
     * Calculate pitch using state machine with hysteresis to prevent oscillation.
     * State only changes when thresholds are exceeded, preventing rapid toggling.
     *
     * @param deltaY Vertical distance to target (positive = need to go up)
     * @return Target pitch angle (negative = up, positive = down)
     */
    private fun calculatePitchWithHysteresis(deltaY: Double): Float {
        // Update state machine based on deltaY with hysteresis
        when (pitchState) {
            SwimPitchState.NEUTRAL -> {
                // Only change state if we significantly exceed threshold
                if (deltaY > ascendThreshold) {
                    pitchState = SwimPitchState.ASCENDING
                } else if (deltaY < descendThreshold) {
                    pitchState = SwimPitchState.DESCENDING
                }
            }

            SwimPitchState.ASCENDING -> {
                // Need to drop below -neutralBuffer to return to neutral
                // (hysteresis prevents immediate switch back)
                if (deltaY < -neutralBuffer) {
                    pitchState = SwimPitchState.NEUTRAL
                }
            }

            SwimPitchState.DESCENDING -> {
                // Need to rise above +neutralBuffer to return to neutral
                if (deltaY > neutralBuffer) {
                    pitchState = SwimPitchState.NEUTRAL
                }
            }
        }

        // Return pitch based on current state
        return when (pitchState) {
            SwimPitchState.ASCENDING -> -30.0f // Look up to surface
            SwimPitchState.DESCENDING -> 30.0f // Look down to descend
            SwimPitchState.NEUTRAL -> 5.0f // Slight downward to stay submerged
        }
    }

    /**
     * Apply target velocity with exponential damping to prevent velocity compounding.
     *
     * Uses formula: newVel = currentVel * damping + (targetVel - currentVel) * acceleration
     * This converges to bounded target velocity instead of exponentially growing.
     *
     * @param yaw Current yaw angle (horizontal direction)
     * @param pitch Current pitch angle (vertical direction)
     */
    private fun applyTargetVelocity(
        yaw: Float,
        pitch: Float,
    ) {
        val player = ctx.player()

        // Get speed configuration (percentage of vanilla speed)
        val speedPercent = Agent.settings().swimSpeedPercent.value / 100.0
        val baseSpeed = 0.197 // Vanilla sprint-swimming speed (blocks/tick, ~3.93 blocks/sec)
        val targetSpeed = (baseSpeed * speedPercent).coerceIn(0.01, 0.80)

        // Calculate target direction vector from yaw/pitch
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        val targetDirX = -kotlin.math.sin(yawRad) * kotlin.math.cos(pitchRad)
        val targetDirY = -kotlin.math.sin(pitchRad)
        val targetDirZ = kotlin.math.cos(yawRad) * kotlin.math.cos(pitchRad)

        // Normalize and scale to target speed
        val magnitude = kotlin.math.sqrt(targetDirX * targetDirX + targetDirY * targetDirY + targetDirZ * targetDirZ)
        val targetVelocity =
            Vec3(
                targetDirX / magnitude * targetSpeed,
                targetDirY / magnitude * targetSpeed,
                targetDirZ / magnitude * targetSpeed,
            )

        // Apply exponential damping + proportional correction
        val currentVelocity = player.deltaMovement
        val damping = 0.70 // Retain 70% of velocity (faster decay, reaches target in ~5 ticks)
        val acceleration = 0.30 // Proportional correction rate (faster convergence to target velocity)

        val newVelocity =
            currentVelocity
                .scale(damping)
                .add(targetVelocity.subtract(currentVelocity).scale(acceleration))

        player.setDeltaMovement(newVelocity)
    }

    /**
     * Swim toward a specific target position (used by pathfinding in Phase 2).
     * Calculates required rotation and applies target velocity in that direction.
     *
     * @param target The block position to swim toward
     */
    fun swimToward(target: BetterBlockPos) {
        val player = ctx.player()
        val targetVec = Vec3.atCenterOf(target)
        val playerPos = player.position()

        // Calculate required rotation to face target
        val rotation = RotationUtils.calcRotationFromVec3d(playerPos, targetVec, ctx.playerRotations())

        // Request rotation via RotationManager (priority 50 = NORMAL)
        val rotationMgr = (maestro as Agent).getRotationManager()
        rotationMgr.queue(
            rotation.yaw,
            rotation.pitch,
            RotationManager.Priority.NORMAL,
        ) {
            // Callback: apply velocity after rotation complete
            // Guard callback execution
            if ((maestro as Agent).isSwimmingActive() && isInWater()) {
                applyTargetVelocity(rotation.yaw, rotation.pitch)
            }
        }
    }

    /**
     * Deactivate swimming mode and restore normal camera control.
     * Should be called when player exits water or swimming behavior stops controlling movement.
     *
     * CRITICAL: Clears rotation queue to prevent stale rotations from executing after deactivation.
     * CRITICAL: Resets pitch state machine to ensure clean initialization on next activation.
     */
    fun deactivateSwimming() {
        val player = ctx.player()

        // CRITICAL FIX: Clear vanilla swimming state (restores normal physics)
        player.setSwimming(false)

        (maestro as Agent).setSwimmingActive(false)

        // CRITICAL FIX: Clear pending rotations to prevent camera jerk after deactivation
        // Without this, queued rotations from swimming (typically 5-20) continue executing
        // after user regains control, causing 0.25-1 second of unwanted camera movement
        (maestro as Agent).getRotationManager().clear()

        // CRITICAL FIX: Reset pitch state machine to neutral for next swim session
        // Without this, state persists (e.g., ASCENDING) and causes incorrect initial pitch
        // calculation on the next swimming session
        pitchState = SwimPitchState.NEUTRAL
    }
}
