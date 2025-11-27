package maestro.launch.mixins;

import maestro.Agent;
import maestro.api.MaestroAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts mouse input during swimming to enable free-look camera control. When swimming is
 * active, mouse movements update the free-look camera instead of the player's rotation, allowing
 * the bot to control movement direction while the user can look around independently.
 */
@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    @Shadow private double accumulatedDX;

    @Shadow private double accumulatedDY;

    /**
     * Intercepts the turnPlayer() method that applies mouse input to player rotation. When swimming
     * is active, we consume the mouse deltas for free-look and prevent them from affecting the
     * player's actual rotation.
     *
     * @param ci Callback info for cancelling the method
     */
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Agent agent = (Agent) MaestroAPI.getProvider().getPrimaryAgent();
        if (agent == null) {
            return;
        }

        // Freecam takes priority; if not active, swimming free-look can still work
        if (agent.isFreecamActive()
                || (Agent.settings().enableFreeLook.value && agent.isSwimmingActive())) {
            // Update free-look camera angles from mouse deltas
            agent.updateFreeLook(accumulatedDX, accumulatedDY);

            // Reset accumulated deltas (consume them)
            accumulatedDX = 0.0;
            accumulatedDY = 0.0;

            // Cancel the original method (prevent player rotation update)
            ci.cancel();
        }
        // Otherwise let vanilla behavior proceed (user controls player rotation normally)
    }
}
