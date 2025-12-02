package maestro.api.event.events.type

open class Cancellable : ICancellable {
    /** Whether this event has been cancelled  */
    private var cancelled = false

    override fun cancel() {
        cancelled = true
    }

    override val isCancelled: Boolean
        get() = cancelled
}
