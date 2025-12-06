package maestro.event.events

import maestro.event.events.type.EventState
import net.minecraft.client.multiplayer.ClientLevel

/** Event fired when a world is loaded or unloaded */
class WorldEvent(
    /** The new world that is being loaded. `null` if being unloaded. */
    @JvmField val world: ClientLevel?,
    /** The state of the event */
    @JvmField val state: EventState,
)
