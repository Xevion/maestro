package maestro.event.events

import maestro.event.events.type.Cancellable

/** Event for tab completion in chat */
class TabCompleteEvent(
    @JvmField val prefix: String,
) : Cancellable() {
    @JvmField var completions: Array<String>? = null
}
