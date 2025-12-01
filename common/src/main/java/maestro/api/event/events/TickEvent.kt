package maestro.api.event.events

import maestro.api.event.events.type.EventState

/**
 * Called on and after each game tick of the primary Minecraft instance and dispatched to
 * all Maestro instances.
 *
 * When [state] is [EventState.PRE], the event is being called just prior to when
 * the current in-game screen is ticked. When [state] is [EventState.POST], the event
 * is being called at the very end of the Minecraft.runTick method.
 */
data class TickEvent(
    @JvmField val state: EventState,
    @JvmField val type: Type,
    @JvmField val count: Int,
) {
    enum class Type {
        /** When guarantees can be made about the game state and in-game variables. */
        IN,

        /** No guarantees can be made about the game state. This probably means we are at the main menu. */
        OUT,
    }

    companion object {
        private var overallTickCount = 0

        @JvmStatic
        @Synchronized
        fun createNextProvider(): java.util.function.BiFunction<EventState, Type, TickEvent> {
            val count = overallTickCount++
            return java.util.function.BiFunction { state, type -> TickEvent(state, type, count) }
        }
    }
}
