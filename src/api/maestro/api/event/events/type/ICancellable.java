package maestro.api.event.events.type;

public interface ICancellable {

    /** Cancels this event */
    void cancel();

    /**
     * @return Whether or not this event has been cancelled
     */
    boolean isCancelled();
}
