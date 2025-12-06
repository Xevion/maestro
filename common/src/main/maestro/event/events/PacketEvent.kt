package maestro.event.events

import maestro.event.events.type.EventState
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet

/** Event fired when a network packet is sent or received */
class PacketEvent(
    @JvmField val networkManager: Connection,
    @JvmField val state: EventState,
    @JvmField val packet: Packet<*>,
) {
    @Suppress("UNCHECKED_CAST")
    fun <T : Packet<*>> cast(): T = packet as T
}
