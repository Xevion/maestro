package maestro.api.behavior

import maestro.api.event.listener.AbstractGameEventListener

/**
 * A behavior is simply a type that is able to listen to events.
 *
 * @see AbstractGameEventListener
 */
interface IBehavior : AbstractGameEventListener
