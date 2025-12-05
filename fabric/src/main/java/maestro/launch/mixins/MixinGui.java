package maestro.launch.mixins;

import maestro.Agent;
import maestro.api.AgentAPI;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to inject debug HUD rendering into Minecraft's GUI rendering.
 *
 * <p>Injects at the end of the main GUI render method to draw debug overlays on top of the HUD.
 */
@Mixin(Gui.class)
public class MixinGui {

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        float tickDelta = deltaTracker.getGameTimeDeltaTicks();

        // Render debug HUD for all agents
        if (AgentAPI.getSettings().debugEnabled.value) {
            for (Agent agent : AgentAPI.getProvider().getAllMaestros()) {
                agent.getDebugRenderer().getHudRenderer().render(graphics, tickDelta);
            }
        }
    }
}
