package maestro.api.event.events

import maestro.api.event.events.type.EventState

/** Event fired when the player is updated */
class PlayerUpdateEvent(
    /** The state of the event */
    @JvmField val state: EventState,
)
