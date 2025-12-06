package maestro.behavior

import maestro.Agent
import maestro.event.events.ChunkOcclusionEvent
import maestro.event.events.RenderEvent
import maestro.event.events.TickEvent
import maestro.gui.radial.RadialMenu
import maestro.input.InputController
import maestro.pathing.goals.GoalGetToBlock
import maestro.utils.Loggers
import maestro.utils.Rotation
import maestro.utils.RotationUtils
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
 * Manages freecam state and controls. Freecam allows the camera to detach from the player entity
 * and move independently, providing a spectator-like view while the bot continues operating.
 */
class FreecamBehavior(
    agent: Agent,
) : Behavior(agent) {
    companion object {
        private const val MOVEMENT_VELOCITY_PER_SECOND = 9.0
        private const val SPRINT_MULTIPLIER = 2.0
        private const val PATHFIND_HOLD_DURATION_MS = 250L

        // Acceleration easing; seconds to reach max speed
        private const val ACCELERATION_TIME = 0.12

        // Deceleration easing; seconds to stop
        private const val DECELERATION_TIME = 0.10
    }

    // Free-look camera angles (independent of player rotation)
    private var freeLookYaw = 0.0f
    private var freeLookPitch = 0.0f

    // Freecam state
    private var freecamPosition: Vec3? = null
    private var prevFreecamPosition: Vec3? = null
    private var freecamActive = false
    private var _savedFov = -1
    private var savedSmoothCamera = false
    private var freecamMode = FreecamMode.STATIC
    private var lastPlayerPosition: Vec3? = null
    private var freecamFollowOffset: Vec3? = null
    private var prevFreecamFollowOffset: Vec3? = null

    // Follow mode position tracking
    private var followTargetPrev: Vec3? = null
    private var followTargetCurrent: Vec3? = null

    // Movement state
    private var lastUpdateTimeMs = 0L
    private var currentVelocity = Vec3.ZERO

    // Input state
    private var wasLeftClickPressed = false
    private var wasRightClickPressed = false
    private var rightClickStartTimeMs = 0L
    private var rightClickPathQueued = false

    // ===== Public API =====

    val isActive: Boolean get() = freecamActive

    val mode: FreecamMode get() = freecamMode

    val position: Vec3? get() = freecamPosition

    val yaw: Float get() = freeLookYaw

    val pitch: Float get() = freeLookPitch

    val savedFov: Int get() = _savedFov

    fun activate() {
        if (!freecamActive && ctx.player() != null && ctx.minecraft().gameRenderer != null) {
            val camera = ctx.minecraft().gameRenderer.mainCamera
            freecamPosition = camera.position
            prevFreecamPosition = freecamPosition
            freeLookYaw = camera.yRot
            freeLookPitch = camera.xRot

            _savedFov =
                ctx
                    .minecraft()
                    .options
                    .fov()
                    .get()
            savedSmoothCamera = ctx.minecraft().options.smoothCamera

            freecamMode =
                Agent
                    .getPrimaryAgent()
                    .settings.freecamDefaultMode.value
            lastPlayerPosition = ctx.player().position()

            freecamActive = true

            if (Agent
                    .getPrimaryAgent()
                    .settings.freecamReloadChunks.value
            ) {
                ctx.minecraft().execute(ctx.minecraft().levelRenderer::allChanged)
            }
        }
    }

    fun deactivate() {
        freecamActive = false
        freecamPosition = null
        prevFreecamPosition = null
        lastPlayerPosition = null
        followTargetPrev = null
        followTargetCurrent = null
        freecamFollowOffset = null
        prevFreecamFollowOffset = null

        if (_savedFov >= 0) {
            ctx
                .minecraft()
                .options
                .fov()
                .set(_savedFov)
            _savedFov = -1
        }
        ctx.minecraft().options.smoothCamera = savedSmoothCamera

        if (Agent
                .getPrimaryAgent()
                .settings.freecamReloadChunks.value &&
            ctx.minecraft().levelRenderer != null
        ) {
            ctx.minecraft().execute { ctx.minecraft().levelRenderer.allChanged() }
        }
    }

    fun toggleMode() {
        val newMode = if (freecamMode == FreecamMode.STATIC) FreecamMode.FOLLOW else FreecamMode.STATIC
        freecamMode = newMode

        if (newMode == FreecamMode.FOLLOW && ctx.player() != null && freecamPosition != null) {
            val playerPos = ctx.player().position()
            val offset = freecamPosition!!.subtract(playerPos)

            freecamFollowOffset = offset
            prevFreecamFollowOffset = offset

            followTargetPrev = playerPos
            followTargetCurrent = playerPos
        } else if (newMode == FreecamMode.STATIC && ctx.player() != null && freecamFollowOffset != null) {
            val playerPos = ctx.player().position()
            val staticPos = playerPos.add(freecamFollowOffset!!)

            freecamPosition = staticPos
            prevFreecamPosition = staticPos
        }
        Loggers.Dev
            .get()
            .atDebug()
            .addKeyValue("mode", newMode)
            .log("Freecam mode switched")
    }

    fun updateMouseLook(
        deltaX: Double,
        deltaY: Double,
    ) {
        val sensitivity = 0.6f + 0.2f

        freeLookYaw += (deltaX * 0.15f * sensitivity).toFloat()
        freeLookPitch += (deltaY * 0.15f * sensitivity).toFloat()

        freeLookPitch = freeLookPitch.coerceIn(-90.0f, 90.0f)
    }

    fun getInterpolatedX(tickDelta: Float): Double {
        if (freecamMode == FreecamMode.FOLLOW && freecamFollowOffset != null && prevFreecamFollowOffset != null) {
            val playerX =
                if (followTargetPrev != null && followTargetCurrent != null) {
                    followTargetPrev!!.x + (followTargetCurrent!!.x - followTargetPrev!!.x) * tickDelta
                } else {
                    ctx.player().x
                }

            val offsetX =
                prevFreecamFollowOffset!!.x + (freecamFollowOffset!!.x - prevFreecamFollowOffset!!.x) * tickDelta

            return playerX + offsetX
        }

        if (freecamPosition == null || prevFreecamPosition == null) {
            return freecamPosition?.x ?: 0.0
        }
        return prevFreecamPosition!!.x + (freecamPosition!!.x - prevFreecamPosition!!.x) * tickDelta
    }

    fun getInterpolatedY(tickDelta: Float): Double {
        if (freecamMode == FreecamMode.FOLLOW && freecamFollowOffset != null && prevFreecamFollowOffset != null) {
            val playerY =
                if (followTargetPrev != null && followTargetCurrent != null) {
                    followTargetPrev!!.y + (followTargetCurrent!!.y - followTargetPrev!!.y) * tickDelta
                } else {
                    ctx.player().y
                }

            val offsetY =
                prevFreecamFollowOffset!!.y + (freecamFollowOffset!!.y - prevFreecamFollowOffset!!.y) * tickDelta

            return playerY + offsetY
        }

        if (freecamPosition == null || prevFreecamPosition == null) {
            return freecamPosition?.y ?: 0.0
        }
        return prevFreecamPosition!!.y + (freecamPosition!!.y - prevFreecamPosition!!.y) * tickDelta
    }

    fun getInterpolatedZ(tickDelta: Float): Double {
        if (freecamMode == FreecamMode.FOLLOW && freecamFollowOffset != null && prevFreecamFollowOffset != null) {
            val playerZ =
                if (followTargetPrev != null && followTargetCurrent != null) {
                    followTargetPrev!!.z + (followTargetCurrent!!.z - followTargetPrev!!.z) * tickDelta
                } else {
                    ctx.player().z
                }

            val offsetZ =
                prevFreecamFollowOffset!!.z + (freecamFollowOffset!!.z - prevFreecamFollowOffset!!.z) * tickDelta

            return playerZ + offsetZ
        }

        if (freecamPosition == null || prevFreecamPosition == null) {
            return freecamPosition?.z ?: 0.0
        }
        return prevFreecamPosition!!.z + (freecamPosition!!.z - prevFreecamPosition!!.z) * tickDelta
    }

    // ===== Event Handlers =====

    override fun onTick(event: TickEvent) {
        if (!freecamActive || event.type != TickEvent.Type.IN) {
            return
        }

        updateFollowTarget()
    }

    override fun onRenderPass(event: RenderEvent) {
        if (!freecamActive) {
            return
        }

        updateFreecamPosition()
        handleFreecamInput()
    }

    override fun onChunkOcclusion(event: ChunkOcclusionEvent) {
        if (freecamActive &&
            Agent
                .getPrimaryAgent()
                .settings.freecamDisableOcclusion.value
        ) {
            event.cancel()
        }
    }

    // ===== Internal Methods =====

    private fun updateFollowTarget() {
        if (ctx.player() == null) return

        val currentPos = ctx.player().position()

        followTargetPrev = followTargetCurrent
        followTargetCurrent = currentPos

        if (followTargetPrev == null) {
            followTargetPrev = currentPos
        }
    }

    private fun updateFreecamPosition() {
        val input = readInputState()
        val deltaTimeSeconds = calculateDeltaTime()
        val targetMovement = calculateMovement(input, deltaTimeSeconds)

        currentVelocity = applyEasing(currentVelocity, targetMovement, deltaTimeSeconds)

        when (freecamMode) {
            FreecamMode.FOLLOW -> {
                val currentOffset = freecamFollowOffset ?: Vec3.ZERO
                prevFreecamFollowOffset = freecamFollowOffset
                freecamFollowOffset = currentOffset.add(currentVelocity)

                if (prevFreecamFollowOffset == null) {
                    prevFreecamFollowOffset = freecamFollowOffset
                }
            }
            else -> {
                freecamPosition?.let { currentPos ->
                    prevFreecamPosition = freecamPosition
                    freecamPosition = currentPos.add(currentVelocity)
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
        if (!InputController.canUseBotKeys()) {
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

        var movement = Vec3.ZERO

        if (input.forward) movement = movement.forward(freeLookYaw, freeLookPitch, speed)
        if (input.back) movement = movement.forward(freeLookYaw, freeLookPitch, -speed)
        if (input.left) movement = movement.strafe(freeLookYaw, -speed)
        if (input.right) movement = movement.strafe(freeLookYaw, speed)

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
        if (!InputController.canUseBotKeys()) {
            return MouseState(leftClick = false, rightClick = false)
        }

        val window = ctx.minecraft().window.window
        return MouseState(
            leftClick = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS,
            rightClick = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS,
        )
    }

    private fun handleFreecamInput() {
        // Skip input handling if radial menu is open (it handles right-click)
        if (RadialMenu.getInstance() != null) {
            wasLeftClickPressed = false
            wasRightClickPressed = false
            rightClickStartTimeMs = 0L
            rightClickPathQueued = false
            return
        }

        val mouse = readMouseState()

        if (mouse.leftClick && !wasLeftClickPressed) {
            handleTeleport()
        }

        // Right-click handling disabled - radial menu now handles this
        // The menu opens on right-click press via DebugKeybindings

        wasLeftClickPressed = mouse.leftClick
        wasRightClickPressed = mouse.rightClick
    }

    private fun getCurrentFreecamPosition(): Vec3? =
        when (freecamMode) {
            FreecamMode.FOLLOW ->
                freecamFollowOffset?.let { offset ->
                    ctx.player().position().add(offset)
                }
            else -> freecamPosition
        }

    private fun raycastFromFreecam(distance: Double): HitResult? {
        val start = getCurrentFreecamPosition() ?: return null
        val rotation = Rotation(freeLookYaw, freeLookPitch)
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
        when (freecamMode) {
            FreecamMode.FOLLOW -> {
                val playerPos = ctx.player().position()
                prevFreecamFollowOffset = freecamFollowOffset
                freecamFollowOffset = targetPos.subtract(playerPos)
            }
            else -> {
                prevFreecamPosition = freecamPosition
                freecamPosition = targetPos
            }
        }
    }

    private fun handleTeleport() {
        val distance =
            Agent
                .getPrimaryAgent()
                .settings.freecamTeleportDistance.value
        val hitResult = raycastFromFreecam(distance) ?: return

        val targetPos =
            when (hitResult.type) {
                HitResult.Type.BLOCK -> hitResult.location
                else -> {
                    val start = getCurrentFreecamPosition() ?: return
                    val rotation = Rotation(freeLookYaw, freeLookPitch)
                    val direction = RotationUtils.calcLookDirectionFromRotation(rotation)
                    start.add(direction.scale(distance))
                }
            }

        updateFreecamTarget(targetPos)
    }

    private fun handlePathfinding() {
        val distance =
            Agent
                .getPrimaryAgent()
                .settings.freecamPathfindDistance.value
        val hitResult = raycastFromFreecam(distance) ?: return

        if (hitResult.type == HitResult.Type.BLOCK) {
            val targetBlock = (hitResult as BlockHitResult).blockPos
            val goal = GoalGetToBlock(targetBlock)
            agent.customGoalTask.setGoalAndPath(goal)
        }
    }
}

/**
 * Freecam camera modes.
 * - STATIC: Camera position is independent of player (default)
 * - FOLLOW: Camera follows player position deltas, rotation independent
 */
enum class FreecamMode {
    STATIC,
    FOLLOW,
}
