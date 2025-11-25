package maestro.behavior

import maestro.Agent
import maestro.api.utils.BetterBlockPos
import maestro.api.utils.Helper
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
            applyTargetVelocity(currentYaw, targetPitch)
        }

        if (Agent.settings().logSwimming.value) {
            val velocity = player.deltaMovement
            logDebug(
                "Swimming: y=${"%.2f".format(currentY)} -> target=${"%.2f".format(targetY)} " +
                    "(Δy=${"%.2f".format(deltaY)}), " +
                    "pitch=${"%.1f".format(targetPitch)} (state=$pitchState), " +
                    "vel=(${"%.3f".format(velocity.x)}, ${"%.3f".format(velocity.y)}, ${"%.3f".format(velocity.z)}), " +
                    "swimming=${player.isSwimming}",
            )
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
        val baseSpeed = 0.08 // Vanilla underwater swimming speed (blocks/tick)
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
        val damping = 0.92 // Retain 92% of velocity each tick
        val acceleration = 0.02 // Proportional correction rate

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
            applyTargetVelocity(rotation.yaw, rotation.pitch)
        }
    }

    /**
     * Deactivate swimming mode and restore normal camera control.
     * Should be called when player exits water or swimming behavior stops controlling movement.
     */
    fun deactivateSwimming() {
        (maestro as Agent).setSwimmingActive(false)
    }

    private fun logDebug(msg: String) {
        Helper.HELPER.logDebug("SwimmingBehavior: $msg")
    }
}
