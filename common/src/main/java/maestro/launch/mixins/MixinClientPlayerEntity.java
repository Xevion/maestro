package maestro.launch.mixins;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import maestro.api.IAgent;
import maestro.api.MaestroAPI;
import maestro.api.event.events.PlayerUpdateEvent;
import maestro.api.event.events.SprintStateEvent;
import maestro.api.event.events.type.EventState;
import maestro.behavior.LookBehavior;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class MixinClientPlayerEntity {
    @Unique private static final MethodHandle MAY_FLY = maestro$resolveMayFly();

    @Unique
    private static MethodHandle maestro$resolveMayFly() {
        try {
            var lookup = MethodHandles.publicLookup();
            return lookup.findVirtual(
                    LocalPlayer.class, "mayFly", MethodType.methodType(boolean.class));
        } catch (NoSuchMethodException e) {
            return null;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "net/minecraft/client/player/AbstractClientPlayer.tick()V",
                            shift = At.Shift.AFTER))
    private void onPreUpdate(CallbackInfo ci) {
        IAgent maestro = MaestroAPI.getProvider().getMaestroForPlayer((LocalPlayer) (Object) this);
        if (maestro != null) {
            maestro.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(EventState.PRE));
        }
    }

    @Redirect(
            method = "aiStep",
            at =
                    @At(
                            value = "FIELD",
                            target = "net/minecraft/world/entity/player/Abilities.mayfly:Z",
                            opcode = Opcodes.GETFIELD))
    @Group(name = "mayFly", min = 1, max = 1)
    private boolean isAllowFlying(Abilities capabilities) {
        IAgent maestro = MaestroAPI.getProvider().getMaestroForPlayer((LocalPlayer) (Object) this);
        if (maestro == null) {
            return capabilities.mayfly;
        }
        return !maestro.getPathingBehavior().isPathing() && capabilities.mayfly;
    }

    @Redirect(
            method = "aiStep",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/player/LocalPlayer;mayFly()Z"))
    @Group(name = "mayFly", min = 1, max = 1)
    private boolean onMayFlyNeoforge(LocalPlayer instance) throws Throwable {
        IAgent maestro = MaestroAPI.getProvider().getMaestroForPlayer((LocalPlayer) (Object) this);
        if (maestro == null) {
            return (boolean) MAY_FLY.invokeExact(instance);
        }
        return !maestro.getPathingBehavior().isPathing() && (boolean) MAY_FLY.invokeExact(instance);
    }

    @Redirect(
            method = "aiStep",
            at = @At(value = "INVOKE", target = "net/minecraft/client/KeyMapping.isDown()Z"))
    private boolean isKeyDown(KeyMapping keyBinding) {
        IAgent maestro = MaestroAPI.getProvider().getMaestroForPlayer((LocalPlayer) (Object) this);
        if (maestro == null) {
            return keyBinding.isDown();
        }
        SprintStateEvent event = new SprintStateEvent();
        maestro.getGameEventHandler().onPlayerSprintState(event);
        if (event.state != null) {
            return event.state;
        }
        if (maestro != MaestroAPI.getProvider().getPrimaryAgent()) {
            // hitting control shouldn't make all bots sprint
            return false;
        }
        return keyBinding.isDown();
    }

    @Inject(method = "rideTick", at = @At(value = "HEAD"))
    private void updateRidden(CallbackInfo cb) {
        IAgent maestro = MaestroAPI.getProvider().getMaestroForPlayer((LocalPlayer) (Object) this);
        if (maestro != null) {
            ((LookBehavior) maestro.getLookBehavior()).pig();
        }
    }

    @Redirect(
            method = "aiStep",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/player/LocalPlayer;tryToStartFallFlying()Z"))
    private boolean tryToStartFallFlying(final LocalPlayer instance) {
        IAgent maestro = MaestroAPI.getProvider().getMaestroForPlayer(instance);
        if (maestro != null && maestro.getPathingBehavior().isPathing()) {
            return false;
        }
        return instance.tryToStartFallFlying();
    }
}
