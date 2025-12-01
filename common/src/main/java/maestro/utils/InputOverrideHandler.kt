package maestro.utils

import maestro.Agent
import maestro.api.MaestroAPI
import maestro.api.event.events.TickEvent
import maestro.api.utils.IInputOverrideHandler
import maestro.api.utils.input.Input
import maestro.behavior.Behavior
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

    internal val blockBreakHelper = BlockBreakHelper(maestro.playerContext)
    internal val blockPlaceHelper = BlockPlaceHelper(maestro.playerContext)

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

    /** Clears the override state for all keys */
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

        blockBreakHelper.tick(isInputForcedDown(Input.CLICK_LEFT))
        blockPlaceHelper.tick(isInputForcedDown(Input.CLICK_RIGHT))

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

    fun getBlockBreakHelper(): BlockBreakHelper = blockBreakHelper

    companion object {
        /**
         * Checks if bot/debug keys should be active based on current game state.
         *
         * Keys are blocked when any screen/GUI is open (except death screen). This prevents keys
         * from activating while typing in chat, browsing inventory, or interacting with menus.
         *
         * @return true if bot keys should process input, false otherwise
         */
        @JvmStatic
        fun canUseBotKeys(): Boolean {
            val mc = Minecraft.getInstance()
            val currentScreen: Screen? = mc.screen

            return when (currentScreen) {
                // No screen open - keys are active
                null -> true
                // Death screen - allow keys (useful for reviewing what killed you)
                is DeathScreen -> true
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
        @JvmStatic
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
        @JvmStatic
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
