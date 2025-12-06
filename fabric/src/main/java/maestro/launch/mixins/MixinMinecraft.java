package maestro.launch.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.function.BiFunction;
import maestro.Agent;
import maestro.debug.DebugKeybindings;
import maestro.event.events.PlayerUpdateEvent;
import maestro.event.events.TickEvent;
import maestro.event.events.WorldEvent;
import maestro.event.events.type.EventState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Shadow public LocalPlayer player;
    @Shadow public ClientLevel level;

    @Unique private BiFunction<EventState, TickEvent.Type, TickEvent> tickProvider;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void postInit(CallbackInfo ci) {
        Agent.getPrimaryAgent();
        DebugKeybindings.init();
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "FIELD",
                            opcode = Opcodes.GETFIELD,
                            target =
                                    "net/minecraft/client/Minecraft.screen:Lnet/minecraft/client/gui/screens/Screen;",
                            ordinal = 0,
                            shift = At.Shift.BEFORE),
            slice =
                    @Slice(
                            from =
                                    @At(
                                            value = "FIELD",
                                            opcode = Opcodes.PUTFIELD,
                                            target = "net/minecraft/client/Minecraft.missTime:I")))
    private void runTick(CallbackInfo ci) {
        this.tickProvider = TickEvent.createNextProvider();

        for (Agent maestro : Agent.getAllAgents()) {
            TickEvent.Type type =
                    maestro.getPlayerContext().player() != null
                                    && maestro.getPlayerContext().world() != null
                            ? TickEvent.Type.IN
                            : TickEvent.Type.OUT;
            maestro.getGameEventHandler().onTick(this.tickProvider.apply(EventState.PRE, type));
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void postRunTick(CallbackInfo ci) {
        if (this.tickProvider == null) {
            return;
        }

        for (Agent maestro : Agent.getAllAgents()) {
            TickEvent.Type type =
                    maestro.getPlayerContext().player() != null
                                    && maestro.getPlayerContext().world() != null
                            ? TickEvent.Type.IN
                            : TickEvent.Type.OUT;
            maestro.getGameEventHandler()
                    .onPostTick(this.tickProvider.apply(EventState.POST, type));
        }

        this.tickProvider = null;

        // Process debug keybindings
        DebugKeybindings.tick();
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "net/minecraft/client/multiplayer/ClientLevel.tickEntities()V",
                            shift = At.Shift.AFTER))
    private void postUpdateEntities(CallbackInfo ci) {
        Agent maestro = Agent.getAgentForPlayer(this.player);
        if (maestro != null) {
            // Intentionally call this after all entities have been updated. That way, any
            // modification to rotations
            // can be recognized by other entity code. (Fireworks and Pigs, for example)
            maestro.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(EventState.POST));
        }
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void preLoadWorld(
            ClientLevel world, ReceivingLevelScreen.Reason arg2, CallbackInfo ci) {
        // If we're unloading the world but one doesn't exist, ignore it
        if (this.level == null && world == null) {
            return;
        }

        // mc.world changing is only the primary maestro

        Agent.getPrimaryAgent()
                .getGameEventHandler()
                .onWorldEvent(new WorldEvent(world, EventState.PRE));
    }

    @Inject(method = "setLevel", at = @At("RETURN"))
    private void postLoadWorld(
            ClientLevel world, ReceivingLevelScreen.Reason arg2, CallbackInfo ci) {
        // still fire event for both null, as that means we've just finished exiting a world

        // mc.world changing is only the primary maestro
        Agent.getPrimaryAgent()
                .getGameEventHandler()
                .onWorldEvent(new WorldEvent(world, EventState.POST));
    }

    @Redirect(
            method = "tick",
            at =
                    @At(
                            value = "FIELD",
                            opcode = Opcodes.GETFIELD,
                            target =
                                    "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;"),
            slice =
                    @Slice(
                            from =
                                    @At(
                                            value = "INVOKE",
                                            target =
                                                    "Lnet/minecraft/client/gui/components/DebugScreenOverlay;showDebugScreen()Z"),
                            to = @At(value = "CONSTANT", args = "stringValue=Keybindings")))
    private Screen passEvents(Minecraft instance) {
        // allow user input is only the primary maestro
        if (Agent.getPrimaryAgent().getPathingBehavior().isPathing() && player != null) {
            return null;
        }
        return instance.screen;
    }

    /**
     * Enables glowing outline for the player entity when freecam is active. This makes the player
     * visible through walls when observing from freecam.
     *
     * <p>Currently uses Minecraft's default white outline color. To customize the color in the
     * future, add a mixin to LevelRenderer.renderEntities() that calls {@code
     * outlineBufferSource.setColor(red, green, blue, alpha)} before rendering the player entity
     * (see LevelRenderer.java line ~1800 where OutlineBufferSource.setColor is called).
     */
    @SuppressWarnings("ReferenceEquality") // Intentional: checking for same entity instance
    @ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
    private boolean onShouldEntityAppearGlowing(boolean original, Entity entity) {
        Agent agent = (Agent) Agent.getPrimaryAgent();
        if (agent != null && agent.isFreecamActive()) {
            Minecraft mc = (Minecraft) (Object) this;
            if (mc.player != null && entity == mc.player) {
                return true;
            }
        }
        return original;
    }
}
