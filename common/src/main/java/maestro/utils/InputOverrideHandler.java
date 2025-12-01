package maestro.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import maestro.Agent;
import maestro.api.MaestroAPI;
import maestro.api.event.events.TickEvent;
import maestro.api.utils.IInputOverrideHandler;
import maestro.api.utils.input.Input;
import maestro.behavior.Behavior;
import net.minecraft.client.player.KeyboardInput;

/**
 * An interface with the game's control system allowing the ability to force down certain controls,
 * having the same effect as if we were actually physically forcing down the assigned key.
 */
public final class InputOverrideHandler extends Behavior implements IInputOverrideHandler {

    /** Maps inputs to whether or not we are forcing their state down. */
    private final Map<Input, Boolean> inputForceStateMap = new HashMap<>();

    private final BlockBreakHelper blockBreakHelper;
    private final BlockPlaceHelper blockPlaceHelper;

    public InputOverrideHandler(Agent maestro) {
        super(maestro);
        this.blockBreakHelper = new BlockBreakHelper(maestro.getPlayerContext());
        this.blockPlaceHelper = new BlockPlaceHelper(maestro.getPlayerContext());
    }

    /**
     * Returns whether we are forcing down the specified {@link Input}.
     *
     * @param input The input
     * @return Whether it is being forced down
     */
    @Override
    public boolean isInputForcedDown(Input input) {
        return input != null && this.inputForceStateMap.getOrDefault(input, false);
    }

    /**
     * Sets whether the specified {@link Input} is being forced down.
     *
     * @param input The {@link Input}
     * @param forced Whether the state is being forced
     */
    @Override
    public void setInputForceState(Input input, boolean forced) {
        this.inputForceStateMap.put(input, forced);
    }

    /** Clears the override state for all keys */
    @Override
    public void clearAllKeys() {
        this.inputForceStateMap.clear();
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.type() == TickEvent.Type.OUT) {
            return;
        }
        if (isInputForcedDown(Input.CLICK_LEFT)) {
            setInputForceState(Input.CLICK_RIGHT, false);
        }
        blockBreakHelper.tick(isInputForcedDown(Input.CLICK_LEFT));
        blockPlaceHelper.tick(isInputForcedDown(Input.CLICK_RIGHT));

        if (inControl()) {
            if (ctx.player().input.getClass() != PlayerMovementInput.class) {
                ctx.player().input = new PlayerMovementInput(this);
            }
        } else {
            if (ctx.player().input.getClass()
                    == PlayerMovementInput
                            .class) { // allow other movement inputs that aren't this one, e.g. for
                // a freecam
                ctx.player().input = new KeyboardInput(ctx.minecraft().options);
            }
        }
        // only set it if it was previously incorrect
        // gotta do it this way, or else it constantly thinks you're beginning a double tap W sprint
        // lol
    }

    private boolean inControl() {
        // if we are not primary (a bot) we should set the movement input even when idle (not
        // pathing)
        return Stream.of(
                                Input.MOVE_FORWARD,
                                Input.MOVE_BACK,
                                Input.MOVE_LEFT,
                                Input.MOVE_RIGHT,
                                Input.SNEAK,
                                Input.JUMP)
                        .anyMatch(this::isInputForcedDown)
                || (maestro.getPathingBehavior().isPathing()
                        || maestro != MaestroAPI.getProvider().getPrimaryAgent());
    }

    public BlockBreakHelper getBlockBreakHelper() {
        return blockBreakHelper;
    }
}
