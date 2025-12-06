package maestro.event.events

import maestro.event.events.type.Cancellable

/**
 * Fired when chunk occlusion is being calculated.
 * Cancel to disable occlusion culling for chunks.
 */
class ChunkOcclusionEvent : Cancellable()
