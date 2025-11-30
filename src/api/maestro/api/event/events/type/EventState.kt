package maestro.api.event.events.type

/**
 * Represents the state of an event in its lifecycle.
 *
 * Events can be dispatched at different points during execution, allowing
 * listeners to react before or after the target action occurs.
 */
enum class EventState {
    /** Before the dispatching of what the event is targeting */
    PRE,

    /** After the dispatching of what the event is targeting */
    POST,
}
