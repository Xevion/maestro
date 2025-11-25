package maestro.launch.mixins;

import maestro.Agent;
import maestro.api.MaestroAPI;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Mixin to intercept camera rotation and allow independent free-look camera control. When free-look
 * is enabled, the camera uses stored free-look angles instead of the player's actual rotation,
 * allowing the user to look around while the bot controls movement direction.
 *
 * <p>Fixed: Changed from Camera.update() to Camera.setup() - the method was renamed in MC 1.21+.
 */
@Mixin(Camera.class)
public class MixinCamera {

    /**
     * Intercepts the setRotation call in Camera.setup() to apply free-look camera angles when
     * swimming is active. This allows the rendered camera to be independent of the player's actual
     * rotation (which is controlled by the bot for movement).
     *
     * <p>CRITICAL: Only activates when swimming behavior is actively controlling the bot,
     * preventing the camera from freezing when not swimming.
     *
     * @param args The arguments to setRotation(yaw, pitch)
     */
    @ModifyArgs(
            method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void onCameraSetRotation(Args args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // Get the current agent instance
        Agent agent = (Agent) MaestroAPI.getProvider().getPrimaryAgent();
        if (agent == null) {
            return;
        }

        // CRITICAL FIX: Only activate when swimming is actively controlling the bot
        // This prevents the camera from freezing when not swimming
        if (Agent.settings().enableFreeLook.value && agent.isSwimmingActive()) {
            args.set(0, agent.getFreeLookYaw()); // yaw (horizontal)
            args.set(1, agent.getFreeLookPitch()); // pitch (vertical)
        }
        // Otherwise use default behavior (player's actual rotation)
    }
}
