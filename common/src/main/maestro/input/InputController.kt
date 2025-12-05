
package maestro.input

import maestro.Agent
import maestro.api.AgentAPI
import maestro.api.behavior.ILookBehavior
import maestro.api.event.events.TickEvent
import maestro.api.player.PlayerContext
import maestro.api.utils.Rotation
import maestro.api.utils.RotationUtils
import maestro.behavior.Behavior
import maestro.gui.ControlScreen
import maestro.pathing.movement.ClickIntent
import maestro.pathing.movement.ForwardInput
import maestro.pathing.movement.Intent
import maestro.pathing.movement.LookIntent
import maestro.pathing.movement.MovementIntent
import maestro.pathing.movement.MovementSpeed
import maestro.pathing.movement.StrafeInput
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.KeyboardInput
import net.minecraft.world.phys.Vec2
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.atan2

/**
 * An interface with the game's control system allowing the ability to force down certain controls,
 * having the same effect as if we were actually physically forcing down the assigned key.
 */
class InputController(
    maestro: Agent,
) : Behavior(maestro) {
    /** Maps inputs to whether or not we are forcing their state down. */
    private val inputForceStateMap = mutableMapOf<Input, Boolean>()

    internal val blockBreakManager = BlockInteractionManager(maestro.playerContext, BlockBreakStrategy())
    internal val blockPlaceManager = BlockInteractionManager(maestro.playerContext, BlockPlaceStrategy())

    // Delta-based intent tracking for efficient input updates
    private var lastMovementIntent: MovementIntent? = null
    private var lastLookIntent: LookIntent? = null
    private var lastClickIntent: ClickIntent? = null

    // Debug fields for drift correction visualization
    @Volatile private var lastIntendedYaw: Float = 0f

    @Volatile private var lastCurrentYaw: Float = 0f

    @Volatile private var lastDriftDeviation: Float = 0f

    @Volatile private var lastKeyCombo: String = ""

    /**
     * Returns whether we are forcing down the specified [Input].
     *
     * @param input The input
     * @return Whether it is being forced down
     */
    fun isInputForcedDown(input: Input?): Boolean = input != null && inputForceStateMap.getOrDefault(input, false)

    /**
     * Sets whether the specified [Input] is being forced down.
     *
     * @param input The [Input]
     * @param forced Whether the state is being forced
     */
    fun setInputForceState(
        input: Input,
        forced: Boolean,
    ) {
        inputForceStateMap[input] = forced
    }

    /**
     * Clears movement-related inputs (WASD, JUMP, SNEAK).
     * Use this when transitioning between path nodes or movements.
     */
    fun clearMovementKeys() {
        setInputForceState(Input.MOVE_FORWARD, false)
        setInputForceState(Input.MOVE_BACK, false)
        setInputForceState(Input.MOVE_LEFT, false)
        setInputForceState(Input.MOVE_RIGHT, false)
        setInputForceState(Input.JUMP, false)
        setInputForceState(Input.SNEAK, false)
    }

    /**
     * Clears interaction inputs (CLICK_LEFT, CLICK_RIGHT).
     * Use this when a process loses control or explicitly stops interacting.
     */
    fun clearInteractionKeys() {
        setInputForceState(Input.CLICK_LEFT, false)
        setInputForceState(Input.CLICK_RIGHT, false)
    }

    /**
     * Clears the override state for all keys.
     * Use sparingly - prefer selective clearing (clearMovementKeys, clearInteractionKeys)
     * to maintain clear ownership of input lifecycle.
     */
    fun clearAllKeys() {
        inputForceStateMap.clear()
    }

    override fun onTick(event: TickEvent) {
        if (event.type == TickEvent.Type.OUT) {
            return
        }

        if (isInputForcedDown(Input.CLICK_LEFT)) {
            setInputForceState(Input.CLICK_RIGHT, false)
        }

        blockBreakManager.tick(isInputForcedDown(Input.CLICK_LEFT))
        blockPlaceManager.tick(isInputForcedDown(Input.CLICK_RIGHT))

        if (inControl()) {
            if (ctx.player().input !is PlayerMovementInput) {
                ctx.player().input = PlayerMovementInput(this)
            }
        } else {
            // Allow other movement inputs that aren't this one, e.g. for a freecam
            if (ctx.player().input is PlayerMovementInput) {
                ctx.player().input = KeyboardInput(ctx.minecraft().options)
            }
        }
        // Only set it if it was previously incorrect
        // Gotta do it this way, or else it constantly thinks you're beginning a double tap W sprint lol
    }

    private fun inControl(): Boolean {
        // If we are not primary (a bot) we should set the movement input even when idle (not pathing)
        val movementInputs =
            listOf(
                Input.MOVE_FORWARD,
                Input.MOVE_BACK,
                Input.MOVE_LEFT,
                Input.MOVE_RIGHT,
                Input.SNEAK,
                Input.JUMP,
            )

        return movementInputs.any { isInputForcedDown(it) } ||
            maestro.pathingBehavior.isPathing() ||
            maestro != AgentAPI.getProvider().primaryAgent
    }

    fun getBlockBreakManager(): BlockInteractionManager = blockBreakManager

    companion object {
        /**
         * Checks if bot/debug keys should be active based on current game state.
         *
         * Keys are blocked when any screen/GUI is open (except death screen). This prevents keys
         * from activating while typing in chat, browsing inventory, or interacting with menus.
         *
         * @return true if bot keys should process input, false otherwise
         */
        fun canUseBotKeys(): Boolean {
            val mc = Minecraft.getInstance()
            val currentScreen: Screen? = mc.screen

            return when (currentScreen) {
                // No screen open - keys are active
                null -> true
                // Death screen - allow keys (useful for reviewing what killed you)
                is DeathScreen -> true
                is ControlScreen -> true
                // Any other screen - block keys
                else -> false
            }
        }

        /**
         * Checks if a specific GLFW key is pressed AND bot keys are active.
         *
         * Combines direct GLFW polling with screen-awareness. Only returns true if the key is
         * physically pressed and no blocking screen is open.
         *
         * @param glfwKey The GLFW key constant (e.g., GLFW.GLFW_KEY_W)
         * @return true if key is physically pressed and bot keys are active
         */
        fun isKeyPressed(glfwKey: Int): Boolean {
            if (!canUseBotKeys()) {
                return false
            }

            val window = Minecraft.getInstance().window.window
            return GLFW.glfwGetKey(window, glfwKey) == GLFW.GLFW_PRESS
        }

        /**
         * Checks if CTRL modifier is pressed (either left or right) AND bot keys are active.
         *
         * @return true if any CTRL key is pressed and bot keys are active
         */
        fun isCtrlPressed(): Boolean {
            if (!canUseBotKeys()) {
                return false
            }

            val window = Minecraft.getInstance().window.window
            return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        }
    }

    /**
     * Apply a MovementIntent by translating it to input states.
     *
     * This is the primary entry point for intent-based movement control.
     * Clears all movement inputs first, then applies the new intent.
     *
     * @param intent Movement intent to apply
     * @param ctx Player context for Toward intent angle calculation
     */
    fun applyMovementIntent(
        intent: MovementIntent,
        ctx: PlayerContext,
    ) {
        clearMovementKeys()

        when (intent) {
            is MovementIntent.Toward -> applyTowardIntent(intent, ctx)
            is MovementIntent.Direct -> applyDirectIntent(intent)
            is MovementIntent.Stop -> {} // Already cleared
        }
    }

    /**
     * Apply a LookIntent by updating LookBehavior rotation target.
     *
     * Translates high-level look intents to rotation targets.
     *
     * @param intent Look intent to apply
     * @param lookBehavior LookBehavior instance to update
     */
    fun applyLookIntent(
        intent: LookIntent,
        lookBehavior: ILookBehavior,
    ) {
        when (intent) {
            is LookIntent.Block -> {
                val center = intent.pos.center
                val rotation =
                    RotationUtils.calcRotationFromVec3d(
                        ctx.playerHead(),
                        center,
                        ctx.playerRotations(),
                    )
                lookBehavior.updateTarget(rotation, true)
            }

            is LookIntent.Entity -> {
                val target = intent.entity.position().add(0.0, intent.entity.eyeHeight.toDouble(), 0.0)
                val rotation =
                    RotationUtils.calcRotationFromVec3d(
                        ctx.playerHead(),
                        target,
                        ctx.playerRotations(),
                    )
                lookBehavior.updateTarget(rotation, true)
            }

            is LookIntent.Direction -> {
                val rotation = Rotation(intent.yaw, intent.pitch)
                lookBehavior.updateTarget(rotation, true)
            }

            is LookIntent.Point -> {
                val rotation =
                    RotationUtils.calcRotationFromVec3d(
                        ctx.playerHead(),
                        intent.vec,
                        ctx.playerRotations(),
                    )
                lookBehavior.updateTarget(rotation, true)
            }

            is LookIntent.None -> {
                // No rotation update - maintain current look direction
            }
        }
    }

    /**
     * Apply Toward intent with drift-aware key calculation.
     *
     * Algorithm:
     * 1. Calculate angle from current position to destination
     * 2. If start position provided, calculate drift correction
     * 3. Use player's current rotation to determine keys
     * 4. Map angle to optimal forward/strafe/sprint combination
     */
    private fun applyTowardIntent(
        intent: MovementIntent.Toward,
        ctx: PlayerContext,
    ) {
        val player = ctx.player()
        val currentPos =
            Vec2(player.x.toFloat(), player.z.toFloat())

        // Calculate angle from current position to destination
        val deltaX = intent.target.x - currentPos.x
        val deltaY = intent.target.y - currentPos.y
        val targetYaw = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat() - 90f

        // If start position provided, calculate drift correction
        val correctedYaw =
            if (intent.startPos != null) {
                calculateDriftCorrectedYaw(
                    startPos = intent.startPos,
                    currentPos = currentPos,
                    destination = intent.target,
                    targetYaw = targetYaw,
                )
            } else {
                targetYaw // Fall back to simple angle calculation
            }

        // Use player's current rotation for key determination
        val playerYaw = player.yRot
        val angleDiff = normalizeAngle(correctedYaw - playerYaw)

        // Map angle to keys using simple threshold system
        val keys = mapAngleToKeys(angleDiff, intent.speed)

        // Apply keys
        applyKeyInputs(keys)
        if (intent.jump) setInputForceState(Input.JUMP, true)
    }

    /**
     * Calculate drift-corrected yaw based on deviation from intended path.
     *
     * Compares the intended angle (start → destination) with the current angle
     * (current → destination). If deviation exceeds threshold, corrects toward intended angle.
     */
    private fun calculateDriftCorrectedYaw(
        startPos: Vec2,
        currentPos: Vec2,
        destination: Vec2,
        targetYaw: Float,
    ): Float {
        // Calculate intended angle (from start to destination)
        val intendedDeltaX = destination.x - startPos.x
        val intendedDeltaY = destination.y - startPos.y
        val intendedYaw = Math.toDegrees(atan2(intendedDeltaY.toDouble(), intendedDeltaX.toDouble())).toFloat() - 90f

        // Calculate current angle (from current to destination)
        val currentDeltaX = destination.x - currentPos.x
        val currentDeltaY = destination.y - currentPos.y
        val currentYaw = Math.toDegrees(atan2(currentDeltaY.toDouble(), currentDeltaX.toDouble())).toFloat() - 90f

        // Compute angle deviation
        val deviation = normalizeAngle(currentYaw - intendedYaw)

        // Store for debug HUD
        lastDriftDeviation = deviation
        lastIntendedYaw = intendedYaw
        lastCurrentYaw = currentYaw

        // Only correct if deviation exceeds threshold
        val driftCorrectionThreshold = 15f // degrees
        return if (abs(deviation) > driftCorrectionThreshold) {
            // Correct toward intended angle
            intendedYaw
        } else {
            // Use simple angle to destination (no correction needed)
            targetYaw
        }
    }

    /**
     * Map angle difference to optimal key combination.
     *
     * Uses simple thresholds for forward/backward and strafe keys.
     * Sprint enabled only when moving forward.
     */
    private fun mapAngleToKeys(
        angleDiff: Float,
        speed: MovementSpeed,
    ): KeyCombination {
        // Simple angle-to-keys mapping
        val forward =
            when {
                abs(angleDiff) < 90f -> ForwardInput.FORWARD
                else -> ForwardInput.BACKWARD
            }

        val strafe =
            when {
                angleDiff > 22.5f -> StrafeInput.LEFT
                angleDiff < -22.5f -> StrafeInput.RIGHT
                else -> StrafeInput.NONE
            }

        // Sprint enabled if moving generally forward and speed intent is SPRINT
        val sprint = speed == MovementSpeed.SPRINT && forward == ForwardInput.FORWARD

        return KeyCombination(forward, strafe, sprint)
    }

    /**
     * Apply key combination to input state.
     */
    private fun applyKeyInputs(keys: KeyCombination) {
        when (keys.forward) {
            ForwardInput.FORWARD -> setInputForceState(Input.MOVE_FORWARD, true)
            ForwardInput.BACKWARD -> setInputForceState(Input.MOVE_BACK, true)
            ForwardInput.NONE -> {}
        }

        when (keys.strafe) {
            StrafeInput.LEFT -> setInputForceState(Input.MOVE_LEFT, true)
            StrafeInput.RIGHT -> setInputForceState(Input.MOVE_RIGHT, true)
            StrafeInput.NONE -> {}
        }

        if (keys.sprint) {
            setInputForceState(Input.SPRINT, true)
        }

        // Format for debug display
        lastKeyCombo =
            buildString {
                append(
                    when (keys.forward) {
                        ForwardInput.FORWARD -> "W"
                        ForwardInput.BACKWARD -> "S"
                        ForwardInput.NONE -> ""
                    },
                )
                append(
                    when (keys.strafe) {
                        StrafeInput.LEFT -> "A"
                        StrafeInput.RIGHT -> "D"
                        StrafeInput.NONE -> ""
                    },
                )
                if (keys.sprint) append("+Sprint")
                if (isEmpty()) append("None")
            }
    }

    /**
     * Normalize angle to [-180, 180] range.
     */
    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle % 360f
        if (normalized > 180f) normalized -= 360f
        if (normalized < -180f) normalized += 360f
        return normalized
    }

    /**
     * Key combination for movement.
     */
    private data class KeyCombination(
        val forward: ForwardInput,
        val strafe: StrafeInput,
        val sprint: Boolean,
    )

    /**
     * Apply Direct intent by mapping enum inputs to Minecraft key states.
     *
     * Straightforward translation - no calculation needed.
     */
    private fun applyDirectIntent(intent: MovementIntent.Direct) {
        when (intent.forward) {
            ForwardInput.FORWARD -> setInputForceState(Input.MOVE_FORWARD, true)
            ForwardInput.BACKWARD -> setInputForceState(Input.MOVE_BACK, true)
            ForwardInput.NONE -> {}
        }

        when (intent.strafe) {
            StrafeInput.LEFT -> setInputForceState(Input.MOVE_LEFT, true)
            StrafeInput.RIGHT -> setInputForceState(Input.MOVE_RIGHT, true)
            StrafeInput.NONE -> {}
        }

        applySpeed(intent.speed)
        if (intent.jump) setInputForceState(Input.JUMP, true)
    }

    /**
     * Apply speed modifier to current input state.
     *
     * Sprint and sneak are mutually exclusive modifiers.
     * Walk is the default (no modifier needed).
     */
    private fun applySpeed(speed: MovementSpeed) {
        when (speed) {
            MovementSpeed.SPRINT -> setInputForceState(Input.SPRINT, true)
            MovementSpeed.WALK -> {} // Default, no input needed
            MovementSpeed.SNEAK -> setInputForceState(Input.SNEAK, true)
        }
    }

    /**
     * Apply unified intent with delta-based input updates.
     *
     * Tracks previous intent and only updates changed components, minimizing
     * unnecessary input state changes. This prevents issues like:
     * - Flickering inputs when same intent is applied repeatedly
     * - Unnecessary key clearing and re-application every tick
     * - Sprint detection reset from double-tap W simulation
     *
     * Order of application: Look → Movement → Click
     * This ensures rotation updates before movement calculation.
     *
     * @param intent Unified intent to apply
     * @param lookBehavior LookBehavior instance for rotation updates
     * @param ctx Player context for movement calculations
     */
    fun applyIntent(
        intent: Intent,
        lookBehavior: ILookBehavior,
        ctx: PlayerContext,
    ) {
        // Apply look first (rotation before movement)
        if (intent.look != lastLookIntent) {
            applyLookIntent(intent.look, lookBehavior)
            lastLookIntent = intent.look
        }

        // Apply movement (depends on current rotation)
        if (intent.movement != lastMovementIntent) {
            applyMovementIntent(intent.movement, ctx)
            lastMovementIntent = intent.movement
        }

        // Apply click last
        if (intent.click != lastClickIntent) {
            applyClickIntent(intent.click)
            lastClickIntent = intent.click
        }
    }

    /**
     * Apply click intent by setting click input states.
     *
     * Left and right click are mutually exclusive (handled by existing logic in onTick).
     * This method only sets the new state - clearing is handled by delta comparison.
     */
    private fun applyClickIntent(intent: ClickIntent) {
        when (intent) {
            is ClickIntent.LeftClick -> setInputForceState(Input.CLICK_LEFT, true)
            is ClickIntent.RightClick -> setInputForceState(Input.CLICK_RIGHT, true)
            is ClickIntent.None -> {
                setInputForceState(Input.CLICK_LEFT, false)
                setInputForceState(Input.CLICK_RIGHT, false)
            }
        }
    }

    /**
     * Clear intent tracking state.
     *
     * Called when movement changes or path execution resets to ensure
     * delta comparison doesn't incorrectly detect "no change" when actually
     * switching between movements.
     */
    fun clearIntentTracking() {
        lastMovementIntent = null
        lastLookIntent = null
        lastClickIntent = null
    }

    // Public accessors for debug HUD
    fun getLastIntendedYaw(): Float = lastIntendedYaw

    fun getLastCurrentYaw(): Float = lastCurrentYaw

    fun getLastDriftDeviation(): Float = lastDriftDeviation

    fun getLastKeyCombo(): String = lastKeyCombo

    fun hasActiveMovement(): Boolean = lastMovementIntent != null
}
