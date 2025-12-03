package maestro.launch.mixins;

import maestro.Agent;
import maestro.api.MaestroAPI;
import maestro.utils.InputOverrideHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents block breaking and entity attacking when freecam is active and bot keys are enabled.
 * Freecam input handling uses left-click for teleportation, so we need to prevent vanilla
 * attack/break logic from executing on the same click. When screens are open (inventory, etc.),
 * vanilla click handling is allowed to enable normal GUI interaction.
 */
@Mixin(Minecraft.class)
public class MixinMinecraftAttack {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void maestro$preventAttackDuringFreecam(CallbackInfoReturnable<Boolean> cir) {
        Agent agent = (Agent) MaestroAPI.getProvider().getPrimaryAgent();
        if (agent != null
                && agent.isFreecamActive()
                && InputOverrideHandler.Companion.canUseBotKeys()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void maestro$preventContinueAttackDuringFreecam(CallbackInfo ci) {
        Agent agent = (Agent) MaestroAPI.getProvider().getPrimaryAgent();
        if (agent != null
                && agent.isFreecamActive()
                && InputOverrideHandler.Companion.canUseBotKeys()) {
            ci.cancel();
        }
    }
}
