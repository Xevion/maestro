package maestro.launch.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import maestro.Agent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Shadow private Vec3 position;

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
        Agent agent = (Agent) Agent.getPrimaryAgent();
        if (agent == null) {
            return;
        }

        // Freecam takes priority; if not active, swimming free-look can still work
        if (agent.isFreecamActive()
                || (Agent.getPrimaryAgent().getSettings().enableFreeLook.value
                        && agent.isSwimmingActive())) {
            args.set(0, agent.getFreeLookYaw()); // yaw (horizontal)
            args.set(1, agent.getFreeLookPitch()); // pitch (vertical)
        }
        // Otherwise use default behavior (player's actual rotation)
    }

    /**
     * Sets the freecam camera position after setup completes. This allows the rendered camera to be
     * positioned independently of the player entity. Uses interpolation for smooth movement.
     */
    @Inject(method = "setup", at = @At("RETURN"))
    private void onCameraSetup(
            net.minecraft.world.level.BlockGetter blockGetter,
            net.minecraft.world.entity.Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickDelta,
            CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Agent agent = (Agent) Agent.getPrimaryAgent();
        if (agent == null) {
            return;
        }

        if (agent.isFreecamActive()) {
            // Use interpolated position for smooth movement
            this.position =
                    new Vec3(
                            agent.getFreecamX(tickDelta),
                            agent.getFreecamY(tickDelta),
                            agent.getFreecamZ(tickDelta));
        }
    }

    /**
     * Forces the camera to be "detached" when freecam is active. This makes Minecraft render the
     * player model as if in spectator/third-person mode, creating the spectator-like view that is
     * the core feature of freecam.
     */
    @ModifyReturnValue(method = "isDetached", at = @At("RETURN"))
    private boolean onIsDetached(boolean original) {
        Agent agent = (Agent) Agent.getPrimaryAgent();
        if (agent != null && agent.isFreecamActive()) {
            return true;
        }
        return original;
    }
}
