package maestro.api.event.events.type

class Overrideable<T>(
    initialValue: T,
) {
    var value: T = initialValue
        set(newValue) {
            field = newValue
            modified = true
        }

    var modified: Boolean = false
        private set

    override fun toString(): String = "Overrideable(modified=$modified, value=$value)"
}
