package maestro.utils

import maestro.Agent
import maestro.api.MaestroAPI
import maestro.api.event.events.TickEvent
import maestro.api.utils.IInputOverrideHandler
import maestro.api.utils.input.Input
import maestro.behavior.Behavior
import maestro.gui.MaestroScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.KeyboardInput
import org.lwjgl.glfw.GLFW

/**
 * An interface with the game's control system allowing the ability to force down certain controls,
 * having the same effect as if we were actually physically forcing down the assigned key.
 */
class InputOverrideHandler(
    maestro: Agent,
) : Behavior(maestro),
    IInputOverrideHandler {
    /** Maps inputs to whether or not we are forcing their state down. */
    private val inputForceStateMap = mutableMapOf<Input, Boolean>()

    internal val blockBreakManager = BlockInteractionManager(maestro.playerContext, BlockBreakStrategy())
    internal val blockPlaceManager = BlockInteractionManager(maestro.playerContext, BlockPlaceStrategy())

    /**
     * Returns whether we are forcing down the specified [Input].
     *
     * @param input The input
     * @return Whether it is being forced down
     */
    override fun isInputForcedDown(input: Input?): Boolean = input != null && inputForceStateMap.getOrDefault(input, false)

    /**
     * Sets whether the specified [Input] is being forced down.
     *
     * @param input The [Input]
     * @param forced Whether the state is being forced
     */
    override fun setInputForceState(
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
    override fun clearAllKeys() {
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
            maestro != MaestroAPI.getProvider().primaryAgent
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
                is MaestroScreen -> true
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
}
