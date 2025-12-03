package maestro.api.event.events

import maestro.api.event.events.type.Cancellable

/**
 * Fired when chunk occlusion is being calculated.
 * Cancel to disable occlusion culling for chunks.
 */
class ChunkOcclusionEvent : Cancellable()
