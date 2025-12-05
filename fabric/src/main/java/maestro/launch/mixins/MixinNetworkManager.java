package maestro.launch.mixins;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import maestro.Agent;
import maestro.api.event.events.PacketEvent;
import maestro.api.event.events.type.EventState;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinNetworkManager {

    @Shadow private Channel channel;

    @Shadow @Final private PacketFlow receiving;

    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void preDispatchPacket(
            Packet<?> packet,
            PacketSendListener packetSendListener,
            boolean flush,
            CallbackInfo ci) {
        if (this.receiving != PacketFlow.CLIENTBOUND) {
            return;
        }

        for (Agent maestro : Agent.getAllAgents()) {
            if (maestro.getPlayerContext().player() != null
                    && maestro.getPlayerContext().player().connection.getConnection()
                            == (Object) this) {
                maestro.getGameEventHandler()
                        .onSendPacket(
                                new PacketEvent(
                                        (Connection) (Object) this, EventState.PRE, packet));
            }
        }
    }

    @Inject(method = "sendPacket", at = @At("RETURN"))
    private void postDispatchPacket(
            Packet<?> packet,
            PacketSendListener packetSendListener,
            boolean flush,
            CallbackInfo ci) {
        if (this.receiving != PacketFlow.CLIENTBOUND) {
            return;
        }

        for (Agent maestro : Agent.getAllAgents()) {
            if (maestro.getPlayerContext().player() != null
                    && maestro.getPlayerContext().player().connection.getConnection()
                            == (Object) this) {
                maestro.getGameEventHandler()
                        .onSendPacket(
                                new PacketEvent(
                                        (Connection) (Object) this, EventState.POST, packet));
            }
        }
    }

    @Inject(
            method = "channelRead0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "net/minecraft/network/Connection.genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"))
    private void preProcessPacket(
            ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (this.receiving != PacketFlow.CLIENTBOUND) {
            return;
        }
        for (Agent maestro : Agent.getAllAgents()) {
            if (maestro.getPlayerContext().player() != null
                    && maestro.getPlayerContext().player().connection.getConnection()
                            == (Object) this) {
                maestro.getGameEventHandler()
                        .onReceivePacket(
                                new PacketEvent(
                                        (Connection) (Object) this, EventState.PRE, packet));
            }
        }
    }

    @Inject(method = "channelRead0", at = @At("RETURN"))
    private void postProcessPacket(
            ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (!this.channel.isOpen() || this.receiving != PacketFlow.CLIENTBOUND) {
            return;
        }
        for (Agent maestro : Agent.getAllAgents()) {
            if (maestro.getPlayerContext().player() != null
                    && maestro.getPlayerContext().player().connection.getConnection()
                            == (Object) this) {
                maestro.getGameEventHandler()
                        .onReceivePacket(
                                new PacketEvent(
                                        (Connection) (Object) this, EventState.POST, packet));
            }
        }
    }
}
