package maestro.api.event.events

import maestro.api.event.events.type.Cancellable

/** Event fired when a chat message is being sent */
class ChatEvent(
    /** The message being sent */
    @JvmField val message: String,
) : Cancellable()
