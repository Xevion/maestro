package maestro.behavior

import maestro.Agent
import maestro.api.event.events.ChunkOcclusionEvent
import maestro.api.event.events.RenderEvent
import maestro.api.event.events.TickEvent
import maestro.api.pathing.goals.GoalGetToBlock
import maestro.api.utils.Rotation
import maestro.api.utils.RotationUtils
import maestro.utils.InputOverrideHandler
import net.minecraft.Util
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Handles freecam movement input and position updates. When freecam is active, this behavior
 * captures WASD/Space/Shift input and calculates camera movement independently of the player
 * entity.
 */
class FreecamBehavior(
    maestro: Agent,
) : Behavior(maestro) {
    // Trig cache to avoid recalculating sin/cos every tick
    private var cachedYaw = Float.NaN
    private var cachedPitch = Float.NaN
    private var sinYaw = 0.0
    private var cosYaw = 0.0
    private var sinPitch = 0.0
    private var cosPitch = 0.0

    // Real-time tracking for tickrate-independent movement
    private var lastUpdateTimeMs = 0L

    // Click tracking for teleport and pathfinding
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

        // Handle input every frame for instant response regardless of tickrate
        updateFreecamPosition()
        handleFreecamInput()
    }

    override fun onChunkOcclusion(event: ChunkOcclusionEvent) {
        if (maestro.isFreecamActive && Agent.settings().freecamDisableOcclusion.value) {
            event.cancel()
        }
    }

    private fun updateFreecamPosition() {
        // Don't process freecam movement when screens are open
        if (!InputOverrideHandler.canUseBotKeys()) {
            return
        }

        // Calculate real-time delta for tickrate-independent movement
        val currentTimeMs = Util.getMillis()
        val deltaTimeSeconds =
            if (lastUpdateTimeMs == 0L) {
                0.05 // First update: assume 50ms (1 tick at 20 TPS)
            } else {
                (currentTimeMs - lastUpdateTimeMs) / 1000.0
            }
        lastUpdateTimeMs = currentTimeMs

        val window = ctx.minecraft().window.window

        // Read WASD/Space/Shift keys
        val forward = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS
        val back = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS
        val left = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS
        val right = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS
        val up = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS
        val down = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS

        val movement =
            calculateMovement(forward, back, left, right, up, down, isSprintKeyPressed(), deltaTimeSeconds)

        // In FOLLOW mode, update offset; in STATIC mode, update position
        if (maestro.freecamMode == FreecamMode.FOLLOW) {
            val currentOffset = maestro.freecamFollowOffset ?: Vec3.ZERO
            maestro.setFreecamFollowOffset(currentOffset.add(movement))
        } else {
            val currentPos = maestro.freecamPosition ?: return
            maestro.setFreecamPosition(currentPos.add(movement))
        }
    }

    private fun isSprintKeyPressed(): Boolean =
        ctx
            .minecraft()
            .options.keySprint.isDown

    private fun updateTrigCache() {
        val currentYaw = maestro.freeLookYaw
        val currentPitch = maestro.freeLookPitch

        // Only recalculate if camera rotated
        if (currentYaw != cachedYaw || currentPitch != cachedPitch) {
            val yawRad = currentYaw.toDouble() * DEG_TO_RAD
            val pitchRad = currentPitch.toDouble() * DEG_TO_RAD

            sinYaw = sin(yawRad)
            cosYaw = cos(yawRad)
            sinPitch = sin(pitchRad)
            cosPitch = cos(pitchRad)

            cachedYaw = currentYaw
            cachedPitch = currentPitch
        }
    }

    private fun calculateMovement(
        forward: Boolean,
        back: Boolean,
        left: Boolean,
        right: Boolean,
        up: Boolean,
        down: Boolean,
        sprint: Boolean,
        deltaTimeSeconds: Double,
    ): Vec3 {
        updateTrigCache()

        var moveX = 0.0
        var moveY = 0.0
        var moveZ = 0.0

        // Calculate effective speed: blocks/second (independent of tickrate)
        val speedMultiplier = if (sprint) SPRINT_MULTIPLIER else 1.0
        val velocityPerSecond = MOVEMENT_VELOCITY_PER_SECOND * speedMultiplier
        val effectiveSpeed = velocityPerSecond * deltaTimeSeconds

        // Forward/back relative to camera pitch and yaw (3D movement)
        if (forward) {
            moveX -= sinYaw * cosPitch * effectiveSpeed
            moveY -= sinPitch * effectiveSpeed // Vertical component
            moveZ += cosYaw * cosPitch * effectiveSpeed
        }
        if (back) {
            moveX += sinYaw * cosPitch * effectiveSpeed
            moveY += sinPitch * effectiveSpeed // Opposite vertical
            moveZ -= cosYaw * cosPitch * effectiveSpeed
        }

        // Left/right relative to camera yaw (perpendicular to forward, horizontal only)
        if (left) {
            moveX += cosYaw * effectiveSpeed
            moveZ += sinYaw * effectiveSpeed
        }
        if (right) {
            moveX -= cosYaw * effectiveSpeed
            moveZ -= sinYaw * effectiveSpeed
        }

        // Up/down in world space (overrides pitch-based vertical)
        if (up) {
            moveY = effectiveSpeed // Override
        } else if (down) {
            moveY = -effectiveSpeed // Override
        }

        // Normalize 3D movement vector to prevent faster movement
        val totalLength = sqrt(moveX * moveX + moveY * moveY + moveZ * moveZ)
        if (totalLength > effectiveSpeed + 0.001) { // Epsilon for float precision
            val normalized = effectiveSpeed / totalLength
            moveX *= normalized
            moveY *= normalized
            moveZ *= normalized
        }

        return Vec3(moveX, moveY, moveZ)
    }

    private fun handleFreecamInput() {
        if (!InputOverrideHandler.canUseBotKeys()) return

        val window = ctx.minecraft().window.window
        val leftClick =
            GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        val rightClick =
            GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS

        // Left click: Teleport camera
        if (leftClick && !wasLeftClickPressed) {
            handleTeleport()
        }

        // Right click hold: Pathfinding (real-time based)
        if (rightClick) {
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

        wasLeftClickPressed = leftClick
        wasRightClickPressed = rightClick
    }

    private fun handleTeleport() {
        val player = ctx.player()
        val rotation = Rotation(maestro.freeLookYaw, maestro.freeLookPitch)
        val distance = Agent.settings().freecamTeleportDistance.value

        // Get current camera position based on mode
        val freecamPos =
            if (maestro.freecamMode == FreecamMode.FOLLOW && maestro.freecamFollowOffset != null) {
                Vec3(player.getX(), player.getY(), player.getZ()).add(maestro.freecamFollowOffset)
            } else {
                maestro.freecamPosition ?: return
            }

        // Calculate ray direction
        val direction = RotationUtils.calcLookDirectionFromRotation(rotation)
        val start = Vec3(freecamPos.x, freecamPos.y, freecamPos.z)
        val end =
            start.add(
                direction.x * distance,
                direction.y * distance,
                direction.z * distance,
            )

        // Raycast to find target
        val hitResult =
            ctx
                .world()
                .clip(
                    ClipContext(
                        start,
                        end,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        player,
                    ),
                )

        // Teleport to hit location or max distance
        val targetPos =
            if (hitResult.type == HitResult.Type.BLOCK) {
                hitResult.location
            } else {
                end
            }

        // Update offset in FOLLOW mode, position in STATIC mode
        if (maestro.freecamMode == FreecamMode.FOLLOW) {
            val playerPos = Vec3(player.getX(), player.getY(), player.getZ())
            maestro.setFreecamFollowOffset(targetPos.subtract(playerPos))
        } else {
            maestro.setFreecamPosition(targetPos)
        }
    }

    private fun handlePathfinding() {
        val player = ctx.player()
        val rotation = Rotation(maestro.freeLookYaw, maestro.freeLookPitch)
        val distance = Agent.settings().freecamPathfindDistance.value

        // Get current camera position based on mode
        val freecamPos =
            if (maestro.freecamMode == FreecamMode.FOLLOW && maestro.freecamFollowOffset != null) {
                Vec3(player.getX(), player.getY(), player.getZ()).add(maestro.freecamFollowOffset)
            } else {
                maestro.freecamPosition ?: return
            }

        // Calculate ray direction
        val direction = RotationUtils.calcLookDirectionFromRotation(rotation)
        val start = Vec3(freecamPos.x, freecamPos.y, freecamPos.z)
        val end =
            start.add(
                direction.x * distance,
                direction.y * distance,
                direction.z * distance,
            )

        // Raycast to find target block
        val hitResult =
            ctx
                .world()
                .clip(
                    ClipContext(
                        start,
                        end,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        player,
                    ),
                )

        // Set pathfinding goal if we hit a block
        if (hitResult.type == HitResult.Type.BLOCK) {
            val targetBlock = (hitResult as BlockHitResult).blockPos
            val goal = GoalGetToBlock(targetBlock)
            maestro.customGoalProcess.setGoalAndPath(goal)
        }
    }

    companion object {
        // Movement velocity in blocks per second (0.85 blocks/tick * 20 ticks/second = 17 blocks/second)
        private const val MOVEMENT_VELOCITY_PER_SECOND = 17.0

        private const val SPRINT_MULTIPLIER = 2.0
        private const val DEG_TO_RAD = Math.PI / 180.0

        // Pathfinding hold duration: 350ms (70% of original 500ms = 10 ticks at 20 TPS)
        private const val PATHFIND_HOLD_DURATION_MS = 350L
    }
}
