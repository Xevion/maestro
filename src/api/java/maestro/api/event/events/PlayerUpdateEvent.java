package maestro.api.event.events;

import maestro.api.event.events.type.EventState;

public final class PlayerUpdateEvent {

    /** The state of the event */
    private final EventState state;

    public PlayerUpdateEvent(EventState state) {
        this.state = state;
    }

    /**
     * @return The state of the event
     */
    public final EventState getState() {
        return this.state;
    }
}
