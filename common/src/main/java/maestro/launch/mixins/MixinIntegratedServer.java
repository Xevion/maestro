package maestro.launch.mixins;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables authentication for LAN servers to enable multi-bot development workflows.
 *
 * <p>SECURITY WARNING: This mixin allows ANYONE on the local network to connect to your LAN server
 * without authentication when a world is opened to LAN. This is intentional for development mode
 * multi-bot testing, where multiple Minecraft instances need to connect to the same world without
 * requiring multiple valid Minecraft accounts.
 *
 * <p>ONLY use this in trusted development environments. DO NOT use on public networks or with
 * untrusted users on your LAN.
 *
 * <p>This is required for the automated dev workflow where:
 *
 * <ul>
 *   <li>Coordinator bot opens world to LAN automatically
 *   <li>Worker bots connect via localhost:25565 without authentication
 *   <li>All bots coordinate via gRPC for multi-agent tasks
 * </ul>
 */
@Mixin(MinecraftServer.class)
public abstract class MixinIntegratedServer {

    @Shadow
    public abstract boolean usesAuthentication();

    @Inject(method = "usesAuthentication", at = @At("RETURN"), cancellable = true)
    private void disableAuthentication(CallbackInfoReturnable<Boolean> cir) {
        // Check if this is an integrated server (LAN) by checking if port is published
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (server.getPort() > 0) {
            // Server is published to LAN, disable authentication for multi-bot dev workflows
            cir.setReturnValue(false);
        }
    }
}
