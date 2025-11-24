package maestro.api.event.events.type;

public class Cancellable implements ICancellable {

    /** Whether or not this event has been cancelled */
    private boolean cancelled;

    @Override
    public final void cancel() {
        this.cancelled = true;
    }

    @Override
    public final boolean isCancelled() {
        return this.cancelled;
    }
}
