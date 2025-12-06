package maestro.event.events

import maestro.event.events.type.EventState

/** Event fired when a chunk is loaded, unloaded, or populated */
class ChunkEvent(
    /** The state of the event */
    @JvmField val state: EventState,
    /** The type of chunk event that occurred */
    @JvmField val type: Type,
    /** The Chunk X position */
    @JvmField val x: Int,
    /** The Chunk Z position */
    @JvmField val z: Int,
) {
    /** Returns `true` if the event was fired after a chunk population */
    val isPostPopulate: Boolean
        get() = state == EventState.POST && type.isPopulate

    enum class Type {
        /** When the chunk is constructed. */
        LOAD,

        /** When the chunk is deconstructed. */
        UNLOAD,

        /** When the chunk is being populated with blocks, tile entities, etc. (full chunk) */
        POPULATE_FULL,

        /** When the chunk is being populated with blocks, tile entities, etc. (partial chunk) */
        POPULATE_PARTIAL,

        ;

        val isPopulate: Boolean
            get() = this == POPULATE_FULL || this == POPULATE_PARTIAL
    }
}
