package maestro.launch.mixins;

import maestro.Agent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents player movement input when freecam is active. This allows the user to control the
 * freecam with WASD/Space/Shift while the bot continues to control player movement via
 * InputOverrideHandler.
 */
@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {

    @Inject(method = "tick", at = @At("RETURN"))
    private void onTick(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Agent agent = (Agent) Agent.getPrimaryAgent();
        if (agent == null) {
            return;
        }

        // Zero out player movement when freecam is active
        if (agent.isFreecamActive()) {
            KeyboardInput input = (KeyboardInput) (Object) this;
            input.leftImpulse = 0.0f;
            input.forwardImpulse = 0.0f;
            input.keyPresses =
                    new net.minecraft.world.entity.player.Input(
                            false, false, false, false, false, false, false);
        }
    }
}
