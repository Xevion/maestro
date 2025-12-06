package maestro.launch.mixins;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import maestro.Agent;
import maestro.event.events.PlayerUpdateEvent;
import maestro.event.events.SprintStateEvent;
import maestro.event.events.type.EventState;
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
        Agent agent = Agent.getAgentForPlayer((LocalPlayer) (Object) this);
        if (agent != null) {
            agent.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(EventState.PRE));
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
        Agent agent = Agent.getAgentForPlayer((LocalPlayer) (Object) this);
        if (agent == null) {
            return capabilities.mayfly;
        }
        return !agent.getPathingBehavior().isPathing() && capabilities.mayfly;
    }

    @Redirect(
            method = "aiStep",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/player/LocalPlayer;mayFly()Z"))
    @Group(name = "mayFly", min = 1, max = 1)
    private boolean onMayFlyNeoforge(LocalPlayer instance) throws Throwable {
        Agent agent = Agent.getAgentForPlayer((LocalPlayer) (Object) this);
        if (agent == null) {
            return (boolean) MAY_FLY.invokeExact(instance);
        }
        return !agent.getPathingBehavior().isPathing() && (boolean) MAY_FLY.invokeExact(instance);
    }

    @Redirect(
            method = "aiStep",
            at = @At(value = "INVOKE", target = "net/minecraft/client/KeyMapping.isDown()Z"))
    private boolean isKeyDown(KeyMapping keyBinding) {
        Agent agent = Agent.getAgentForPlayer((LocalPlayer) (Object) this);
        if (agent == null) {
            return keyBinding.isDown();
        }
        SprintStateEvent event = new SprintStateEvent();
        agent.getGameEventHandler().onPlayerSprintState(event);
        if (event.state != null) {
            return event.state;
        }
        if (agent != Agent.getPrimaryAgent()) {
            // hitting control shouldn't make all bots sprint
            return false;
        }
        return keyBinding.isDown();
    }

    @Inject(method = "rideTick", at = @At(value = "HEAD"))
    private void updateRidden(CallbackInfo cb) {
        Agent agent = Agent.getAgentForPlayer((LocalPlayer) (Object) this);
        if (agent != null) {
            agent.getLookBehavior().pig();
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
        Agent agent = Agent.getAgentForPlayer(instance);
        if (agent != null && agent.getPathingBehavior().isPathing()) {
            return false;
        }
        return instance.tryToStartFallFlying();
    }
}
