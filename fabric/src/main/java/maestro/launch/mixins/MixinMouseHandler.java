package maestro.launch.mixins;

import maestro.Agent;
import maestro.gui.radial.RadialMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    @Shadow private double accumulatedDX;

    @Shadow private double accumulatedDY;

    /**
     * Intercepts the turnPlayer() method that applies mouse input for various downstream
     * implementations.
     *
     * @param ci Callback info for cancelling the method
     */
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        // Handle RadialMenu first (takes priority over everything)
        RadialMenu radialMenu = RadialMenu.Companion.getInstance();
        if (radialMenu != null && mc.screen == radialMenu) {
            accumulatedDX = 0.0;
            accumulatedDY = 0.0;
            ci.cancel();
            return;
        }

        if (mc.player == null || mc.screen != null) {
            return; // No player or screen is open (including MaestroScreen)
        }

        Agent agent = Agent.getPrimaryAgent();
        if (agent == null) {
            return;
        }

        // Freecam takes priority; if not active, swimming free-look can still work
        if (agent.isFreecamActive()
                || (Agent.getPrimaryAgent().getSettings().enableFreeLook.value
                        && agent.isSwimmingActive())) {
            // Update free-look camera angles from mouse deltas
            agent.updateFreeLook(accumulatedDX, accumulatedDY);

            // Reset accumulated deltas (consume them)
            accumulatedDX = 0.0;
            accumulatedDY = 0.0;

            // Cancel the original method (prevent player rotation update)
            ci.cancel();
        }
    }
}
