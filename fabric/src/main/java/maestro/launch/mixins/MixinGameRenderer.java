package maestro.launch.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import maestro.Agent;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to disable visual effects during freecam mode. When freecam is active, this prevents
 * disorienting visual effects like head bob and hurt shake from affecting the spectator view.
 */
@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    /**
     * Disables view bobbing effect when freecam is active. This prevents the camera from bobbing
     * when the bot walks/runs.
     */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void onBobView(PoseStack poseStack, float tickDelta, CallbackInfo ci) {
        Agent agent = Agent.getPrimaryAgent();
        if (agent.isFreecamActive()) {
            ci.cancel();
        }
    }

    /**
     * Disables hurt shake effect when freecam is active. This prevents the camera from shaking when
     * the bot takes damage.
     */
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void onBobHurt(PoseStack poseStack, float tickDelta, CallbackInfo ci) {
        Agent agent = Agent.getPrimaryAgent();
        if (agent != null && agent.isFreecamActive()) {
            ci.cancel();
        }
    }

    /**
     * Disables FOV effects when freecam is active. This prevents FOV changes from sprinting,
     * flying, or other movement states from affecting the spectator view.
     */
    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private float onGetFov(float original) {
        Agent agent = Agent.getPrimaryAgent();
        if (agent.isFreecamActive()) {
            return (float) agent.getSavedFov();
        }
        return original;
    }
}
