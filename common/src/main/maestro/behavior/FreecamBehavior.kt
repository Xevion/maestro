package maestro.behavior

import maestro.Agent
import maestro.api.event.events.ChunkOcclusionEvent
import maestro.api.event.events.RenderEvent
import maestro.api.event.events.TickEvent
import maestro.api.pathing.goals.GoalGetToBlock
import maestro.api.utils.Rotation
import maestro.api.utils.RotationUtils
import maestro.input.InputOverrideHandler
import maestro.utils.clampLength
import maestro.utils.forward
import maestro.utils.strafe
import maestro.utils.vertical
import net.minecraft.Util
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW

/**
 * Handles freecam movement input and position updates. When freecam is active, this behavior
 * captures WASD/Space/Shift input and calculates camera movement independently of the player
 * entity.
 */
class FreecamBehavior(
    maestro: Agent,
) : Behavior(maestro) {
    companion object {
        private const val MOVEMENT_VELOCITY_PER_SECOND = 22.0
        private const val SPRINT_MULTIPLIER = 2.0
        private const val PATHFIND_HOLD_DURATION_MS = 250L

        // Acceleration easing; seconds to reach max speed
        private const val ACCELERATION_TIME = 0.12

        // Deceleration easing; seconds to stop
        private const val DECELERATION_TIME = 0.10
    }

    private var lastUpdateTimeMs = 0L
    private var currentVelocity = Vec3.ZERO // Smoothed velocity for easing

    private var wasLeftClickPressed = false
    private var wasRightClickPressed = false
    private var rightClickStartTimeMs = 0L
    private var rightClickPathQueued = false

    override fun onTick(event: TickEvent) {
        if (!maestro.isFreecamActive || event.type != TickEvent.Type.IN) {
            return
        }

        // Update follow target position snapshots for smooth interpolation
        maestro.updateFollowTarget()
    }

    override fun onRenderPass(event: RenderEvent) {
        if (!maestro.isFreecamActive) {
            return
        }

        // Handle input every frame for instant response regardless of tick-rate
        updateFreecamPosition()
        handleFreecamInput()
    }

    override fun onChunkOcclusion(event: ChunkOcclusionEvent) {
        if (maestro.isFreecamActive && Agent.settings().freecamDisableOcclusion.value) {
            event.cancel()
        }
    }

    private fun updateFreecamPosition() {
        val input = readInputState()
        val deltaTimeSeconds = calculateDeltaTime()
        val targetMovement = calculateMovement(input, deltaTimeSeconds)

        // Apply easing to velocity
        currentVelocity = applyEasing(currentVelocity, targetMovement, deltaTimeSeconds)

        when (maestro.freecamMode) {
            FreecamMode.FOLLOW -> {
                val currentOffset = maestro.freecamFollowOffset ?: Vec3.ZERO
                maestro.setFreecamFollowOffset(currentOffset.add(currentVelocity))
            }
            else -> {
                maestro.freecamPosition?.let { currentPos ->
                    maestro.setFreecamPosition(currentPos.add(currentVelocity))
                }
            }
        }
    }

    private fun applyEasing(
        current: Vec3,
        target: Vec3,
        deltaTime: Double,
    ): Vec3 {
        // If target is zero (no input), decelerate quickly
        if (target.lengthSqr() < 0.001) {
            val decelerationRate = 1.0 / DECELERATION_TIME
            val decelerationFactor = (decelerationRate * deltaTime).coerceAtMost(1.0)
            return current.scale(1.0 - decelerationFactor)
        }

        // Accelerate toward target velocity
        val accelerationRate = 1.0 / ACCELERATION_TIME
        val accelerationFactor = (accelerationRate * deltaTime).coerceAtMost(1.0)

        // Lerp between current and target
        return Vec3(
            current.x + (target.x - current.x) * accelerationFactor,
            current.y + (target.y - current.y) * accelerationFactor,
            current.z + (target.z - current.z) * accelerationFactor,
        )
    }

    private fun calculateDeltaTime(): Double {
        val currentTimeMs = Util.getMillis()
        val deltaTimeSeconds =
            if (lastUpdateTimeMs == 0L) {
                0.05 // First update: assume 50ms (1 tick at 20 TPS)
            } else {
                (currentTimeMs - lastUpdateTimeMs) / 1000.0
            }
        lastUpdateTimeMs = currentTimeMs
        return deltaTimeSeconds
    }

    private data class InputState(
        val forward: Boolean,
        val back: Boolean,
        val left: Boolean,
        val right: Boolean,
        val up: Boolean,
        val down: Boolean,
        val sprint: Boolean,
    )

    private fun readInputState(): InputState {
        if (!InputOverrideHandler.canUseBotKeys()) {
            return InputState(
                forward = false,
                back = false,
                left = false,
                right = false,
                up = false,
                down = false,
                sprint = false,
            )
        }

        val window = ctx.minecraft().window.window
        return InputState(
            forward = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS,
            back = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS,
            left = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS,
            right = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS,
            up = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS,
            down = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS,
            sprint =
                ctx
                    .minecraft()
                    .options.keySprint.isDown,
        )
    }

    private fun calculateEffectiveSpeed(
        sprint: Boolean,
        deltaTimeSeconds: Double,
    ): Double = MOVEMENT_VELOCITY_PER_SECOND * (if (sprint) SPRINT_MULTIPLIER else 1.0) * deltaTimeSeconds

    private fun calculateMovement(
        input: InputState,
        deltaTimeSeconds: Double,
    ): Vec3 {
        val speed = calculateEffectiveSpeed(input.sprint, deltaTimeSeconds)
        val yaw = maestro.freeLookYaw
        val pitch = maestro.freeLookPitch

        var movement = Vec3.ZERO

        if (input.forward) movement = movement.forward(yaw, pitch, speed)
        if (input.back) movement = movement.forward(yaw, pitch, -speed)
        if (input.left) movement = movement.strafe(yaw, -speed)
        if (input.right) movement = movement.strafe(yaw, speed)

        movement =
            when {
                input.up -> movement.vertical(speed)
                input.down -> movement.vertical(-speed)
                else -> movement
            }

        return movement.clampLength(speed)
    }

    private data class MouseState(
        val leftClick: Boolean,
        val rightClick: Boolean,
    )

    private fun readMouseState(): MouseState {
        if (!InputOverrideHandler.canUseBotKeys()) {
            return MouseState(leftClick = false, rightClick = false)
        }

        val window = ctx.minecraft().window.window
        return MouseState(
            leftClick = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS,
            rightClick = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS,
        )
    }

    private fun handleFreecamInput() {
        val mouse = readMouseState()

        if (mouse.leftClick && !wasLeftClickPressed) {
            handleTeleport()
        }

        if (mouse.rightClick) {
            if (rightClickStartTimeMs == 0L) {
                rightClickStartTimeMs = Util.getMillis()
            }

            val holdDurationMs = Util.getMillis() - rightClickStartTimeMs
            if (holdDurationMs >= PATHFIND_HOLD_DURATION_MS && !rightClickPathQueued) {
                handlePathfinding()
                rightClickPathQueued = true
            }
        } else {
            rightClickStartTimeMs = 0L
            rightClickPathQueued = false
        }

        wasLeftClickPressed = mouse.leftClick
        wasRightClickPressed = mouse.rightClick
    }

    private fun getCurrentFreecamPosition(): Vec3? =
        when (maestro.freecamMode) {
            FreecamMode.FOLLOW ->
                maestro.freecamFollowOffset?.let { offset ->
                    ctx.player().position().add(offset)
                }
            else -> maestro.freecamPosition
        }

    private fun raycastFromFreecam(distance: Double): HitResult? {
        val start = getCurrentFreecamPosition() ?: return null
        val rotation = Rotation(maestro.freeLookYaw, maestro.freeLookPitch)
        val direction = RotationUtils.calcLookDirectionFromRotation(rotation)
        val end = start.add(direction.scale(distance))

        return ctx.world().clip(
            ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                ctx.player(),
            ),
        )
    }

    private fun updateFreecamTarget(targetPos: Vec3) {
        when (maestro.freecamMode) {
            FreecamMode.FOLLOW -> {
                val playerPos = ctx.player().position()
                maestro.setFreecamFollowOffset(targetPos.subtract(playerPos))
            }
            else -> maestro.setFreecamPosition(targetPos)
        }
    }

    private fun handleTeleport() {
        val distance = Agent.settings().freecamTeleportDistance.value
        val hitResult = raycastFromFreecam(distance) ?: return

        val targetPos =
            when (hitResult.type) {
                HitResult.Type.BLOCK -> hitResult.location
                else -> {
                    val start = getCurrentFreecamPosition() ?: return
                    val rotation = Rotation(maestro.freeLookYaw, maestro.freeLookPitch)
                    val direction = RotationUtils.calcLookDirectionFromRotation(rotation)
                    start.add(direction.scale(distance))
                }
            }

        updateFreecamTarget(targetPos)
    }

    private fun handlePathfinding() {
        val distance = Agent.settings().freecamPathfindDistance.value
        val hitResult = raycastFromFreecam(distance) ?: return

        if (hitResult.type == HitResult.Type.BLOCK) {
            val targetBlock = (hitResult as BlockHitResult).blockPos
            val goal = GoalGetToBlock(targetBlock)
            maestro.customGoalProcess.setGoalAndPath(goal)
        }
    }
}
