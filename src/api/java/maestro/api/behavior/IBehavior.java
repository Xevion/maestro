package maestro.api.behavior;

import maestro.api.event.listener.AbstractGameEventListener;
import maestro.api.event.listener.IGameEventListener;

/**
 * A behavior is simply a type that is able to listen to events.
 *
 * @see IGameEventListener
 */
public interface IBehavior extends AbstractGameEventListener {}
