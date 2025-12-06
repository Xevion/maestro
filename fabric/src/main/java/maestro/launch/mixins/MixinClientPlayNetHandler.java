package maestro.launch.mixins;

import java.util.ArrayList;
import java.util.List;
import maestro.Agent;
import maestro.api.event.events.BlockChangeEvent;
import maestro.api.event.events.ChatEvent;
import maestro.api.event.events.ChunkEvent;
import maestro.api.event.events.type.EventState;
import maestro.cache.CachedChunk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPlayNetHandler extends ClientCommonPacketListenerImpl {

    protected MixinClientPlayNetHandler(
            final Minecraft arg, final Connection arg2, final CommonListenerCookie arg3) {
        super(arg, arg2, arg3);
    }

    @Inject(method = "sendChat(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void sendChatMessage(String string, CallbackInfo ci) {
        ChatEvent event = new ChatEvent(string);
        Agent maestro = Agent.getAgentForPlayer(this.minecraft.player);
        if (maestro == null) {
            return;
        }
        maestro.getGameEventHandler().onSendChatMessage(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void postHandleChunkData(
            ClientboundLevelChunkWithLightPacket packetIn, CallbackInfo ci) {
        for (Agent maestro : Agent.getAllAgents()) {
            LocalPlayer player = maestro.getPlayerContext().player();
            if (player != null && player.connection == (Object) this) {
                maestro.getGameEventHandler()
                        .onChunkEvent(
                                new ChunkEvent(
                                        EventState.POST,
                                        !packetIn.isSkippable()
                                                ? ChunkEvent.Type.POPULATE_FULL
                                                : ChunkEvent.Type.POPULATE_PARTIAL,
                                        packetIn.getX(),
                                        packetIn.getZ()));
            }
        }
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("HEAD"))
    private void preChunkUnload(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        for (Agent maestro : Agent.getAllAgents()) {
            LocalPlayer player = maestro.getPlayerContext().player();
            if (player != null && player.connection == (Object) this) {
                maestro.getGameEventHandler()
                        .onChunkEvent(
                                new ChunkEvent(
                                        EventState.PRE,
                                        ChunkEvent.Type.UNLOAD,
                                        packet.pos().x,
                                        packet.pos().z));
            }
        }
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("RETURN"))
    private void postChunkUnload(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        for (Agent maestro : Agent.getAllAgents()) {
            LocalPlayer player = maestro.getPlayerContext().player();
            if (player != null && player.connection == (Object) this) {
                maestro.getGameEventHandler()
                        .onChunkEvent(
                                new ChunkEvent(
                                        EventState.POST,
                                        ChunkEvent.Type.UNLOAD,
                                        packet.pos().x,
                                        packet.pos().z));
            }
        }
    }

    @Inject(method = "handleBlockUpdate", at = @At("RETURN"))
    private void postHandleBlockChange(ClientboundBlockUpdatePacket packetIn, CallbackInfo ci) {
        if (!Agent.getPrimaryAgent().getSettings().repackOnAnyBlockChange.value) {
            return;
        }
        if (!CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(packetIn.getBlockState().getBlock())) {
            return;
        }
        for (Agent maestro : Agent.getAllAgents()) {
            LocalPlayer player = maestro.getPlayerContext().player();
            if (player != null && player.connection == (Object) this) {
                maestro.getGameEventHandler()
                        .onChunkEvent(
                                new ChunkEvent(
                                        EventState.POST,
                                        ChunkEvent.Type.POPULATE_FULL,
                                        packetIn.getPos().getX() >> 4,
                                        packetIn.getPos().getZ() >> 4));
            }
        }
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("RETURN"))
    private void postHandleMultiBlockChange(
            ClientboundSectionBlocksUpdatePacket packetIn, CallbackInfo ci) {
        Agent maestro = Agent.getAgentForConnection((ClientPacketListener) (Object) this);
        if (maestro == null) {
            return;
        }

        List<kotlin.Pair<BlockPos, BlockState>> changes = new ArrayList<>();
        packetIn.runUpdates(
                (mutPos, state) -> {
                    changes.add(new kotlin.Pair<>(mutPos.immutable(), state));
                });
        if (changes.isEmpty()) {
            return;
        }
        maestro.getGameEventHandler()
                .onBlockChange(
                        new BlockChangeEvent(new ChunkPos(changes.getFirst().getFirst()), changes));
    }

    @Inject(
            method = "handlePlayerCombatKill",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/player/LocalPlayer;shouldShowDeathScreen()Z"))
    private void onPlayerDeath(ClientboundPlayerCombatKillPacket packetIn, CallbackInfo ci) {
        for (Agent maestro : Agent.getAllAgents()) {
            LocalPlayer player = maestro.getPlayerContext().player();
            if (player != null && player.connection == (Object) this) {
                maestro.getGameEventHandler().onPlayerDeath();
            }
        }
    }
}
