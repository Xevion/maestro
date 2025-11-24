package maestro.api.event.events;

import maestro.api.event.events.type.Cancellable;

public final class ChatEvent extends Cancellable {

    /** The message being sent */
    private final String message;

    public ChatEvent(String message) {
        this.message = message;
    }

    /**
     * @return The message being sent
     */
    public final String getMessage() {
        return this.message;
    }
}
