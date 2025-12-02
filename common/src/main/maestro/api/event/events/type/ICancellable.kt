package maestro.api.event.events.type

interface ICancellable {
    /** Cancels this event  */
    fun cancel()

    /**
     * @return Whether this event has been cancelled
     */
    val isCancelled: Boolean
}
