package maestro.utils;

import maestro.api.utils.input.Input;
import net.minecraft.client.player.ClientInput;

public class PlayerMovementInput extends ClientInput {

    private final InputOverrideHandler handler;

    PlayerMovementInput(InputOverrideHandler handler) {
        this.handler = handler;
    }

    @Override
    public void tick() {
        this.leftImpulse = 0.0F;
        this.forwardImpulse = 0.0F;
        boolean jumping = handler.isInputForcedDown(Input.JUMP); // oppa gangnam style

        boolean up = handler.isInputForcedDown(Input.MOVE_FORWARD);
        if (up) {
            this.forwardImpulse++;
        }

        boolean down = handler.isInputForcedDown(Input.MOVE_BACK);
        if (down) {
            this.forwardImpulse--;
        }

        boolean left = handler.isInputForcedDown(Input.MOVE_LEFT);
        if (left) {
            this.leftImpulse++;
        }

        boolean right = handler.isInputForcedDown(Input.MOVE_RIGHT);
        if (right) {
            this.leftImpulse--;
        }

        boolean sneaking = handler.isInputForcedDown(Input.SNEAK);
        if (sneaking) {
            this.leftImpulse *= 0.3F;
            this.forwardImpulse *= 0.3F;
        }

        boolean sprinting = handler.isInputForcedDown(Input.SPRINT);

        this.keyPresses =
                new net.minecraft.world.entity.player.Input(
                        up, down, left, right, jumping, sneaking, sprinting);
    }
}
