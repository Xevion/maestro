package maestro.behavior

import maestro.Agent
import maestro.input.Input
import maestro.pathing.movement.MovementState
import maestro.utils.PackedBlockPos
import maestro.utils.RotationUtils
import net.minecraft.world.phys.Vec3

/**
 * Handles Minecraft 1.13+ swimming behavior when the bot is in water.
 *
 * Minecraft 1.13 introduced a new swimming mechanic where players can swim horizontally underwater
 * by sprinting + moving forward while in water. This triggers the "swimming" pose/state which allows
 * staying underwater and moving efficiently.
 *
 * Implementation uses target velocity with exponential damping to prevent velocity compounding.
 * Rotation (yaw/pitch) is controlled by Movement classes for continuous error correction.
 */
class SwimmingBehavior(
    agent: Agent,
) : Behavior(agent) {
    /** Returns true if player is fully submerged underwater. */
    fun isSubmerged(): Boolean = ctx.player().isUnderWater

    /** Returns true if player is touching water (maybe at surface). */
    fun isInWater(): Boolean = ctx.player().isInWater

    /**
     * Determines if enhanced swimming should be activated.
     * Requires player to be in water and the enhanced swimming setting enabled.
     */
    fun shouldActivateSwimming(): Boolean =
        isInWater() &&
            Agent
                .getPrimaryAgent()
                .settings.enhancedSwimming.value

    /**
     * Apply swimming inputs to trigger Minecraft 1.13+ swimming state.
     * Uses rotation already calculated by Movement class for continuous error correction.
     *
     * Key to MC 1.13+ swimming:
     * - Sprint + Forward = initiate swimming
     * - Movement classes control pitch/yaw based on position error
     *
     * Uses direct sprint control and target velocity approach with exponential damping.
     *
     * @param state The movement state (unused, kept for API compatibility)
     * @param targetY The Y coordinate (unused, kept for API compatibility)
     */
    fun applySwimmingInputs(
        state: MovementState,
        targetY: Double,
    ) {
        val player = ctx.player()
        val agent = agent as Agent

        // Trigger vanilla swimming state and pose
        player.isSwimming = true
        player.pose = net.minecraft.world.entity.Pose.SWIMMING

        // Activate swimming mode (enables free-look camera)
        agent.setSwimmingActive(true)

        // Core swimming inputs: Direct sprint control + Forward input
        // Direct sprint prevents jittering from input clearing
        player.isSprinting = true
        agent.inputOverrideHandler.setInputForceState(Input.MOVE_FORWARD, true)

        // Apply velocity using rotation already set by Movement class
        // Movement calculated optimal yaw/pitch based on position error
        val currentYaw = player.yRot
        val currentPitch = player.xRot

        applyTargetVelocity(currentYaw, currentPitch)
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
        val speedPercent =
            Agent
                .getPrimaryAgent()
                .settings.swimSpeedPercent.value / 100.0
        val baseSpeed = 0.197 // Vanilla sprint-swimming speed (blocks/tick, ~3.93 blocks/sec)
        val targetSpeed = (baseSpeed * speedPercent).coerceAtLeast(0.01)

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

        player.deltaMovement = newVelocity
    }

    /**
     * Swim toward a specific target position (used by pathfinding in Phase 2).
     * Calculates required rotation and applies target velocity in that direction.
     *
     * @param target The block position to swim toward
     */
    fun swimToward(target: PackedBlockPos) {
        val player = ctx.player()
        val targetVec = Vec3.atCenterOf(target.toBlockPos())
        val playerPos = player.position()

        // Calculate required rotation to face target
        val rotation = RotationUtils.calcRotationFromVec3d(playerPos, targetVec, ctx.playerRotations())

        // Request rotation via RotationManager (priority 50 = NORMAL)
        val rotationMgr = agent.rotationManager
        rotationMgr.queue(
            rotation.yaw,
            rotation.pitch,
            RotationManager.Priority.NORMAL,
        ) {
            // Callback: apply velocity after rotation complete
            // Guard callback execution
            if (agent.isSwimmingActive && isInWater()) {
                applyTargetVelocity(rotation.yaw, rotation.pitch)
            }
        }
    }

    /**
     * Deactivate swimming mode and restore normal camera control.
     * Should be called when player exits water or swimming behavior stops controlling movement.
     */
    fun deactivateSwimming() {
        val player = ctx.player()

        // Clear vanilla swimming state (restores normal physics)
        player.isSwimming = false
        player.isSprinting = false

        agent.setSwimmingActive(false)

        // Clear pending rotations to prevent camera jerk after deactivation
        // Without this, queued rotations from swimming continue executing after user regains control
        this@SwimmingBehavior.agent.rotationManager.clear()
    }
}
