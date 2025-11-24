package maestro.api.event.listener;

/**
 * A type of {@link IGameEventListener} that can have additional listeners registered so that they
 * receive the events that are dispatched to this listener.
 */
public interface IEventBus extends IGameEventListener {

    /**
     * Registers the specified {@link IGameEventListener} to this event bus
     *
     * @param listener The listener
     */
    void registerEventListener(IGameEventListener listener);
}
