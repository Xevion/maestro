package maestro.behavior

import maestro.Agent
import maestro.api.event.events.PlayerUpdateEvent
import maestro.api.event.events.TickEvent
import maestro.api.utils.Loggers
import maestro.api.utils.Rotation
import org.slf4j.Logger
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min

/**
 * Manages bot rotations with priority-based queueing, separating bot control from user camera view.
 * This allows the bot to control look direction for movement while the user maintains independent
 * camera control.
 *
 * Priority levels (lower = higher precedence):
 * - CRITICAL (100): Manual user control overrides
 * - HIGH (75): Combat, block interaction
 * - NORMAL (50): Swimming, pathing (default)
 * - LOW (25): Idle look-around
 */
class RotationManager(
    maestro: Agent,
) : Behavior(maestro) {
    private val rotations: PriorityQueue<PendingRotation> = PriorityQueue(compareBy { it.priority })
    private var currentRotation: PendingRotation? = null

    /**
     * Queue a rotation request with specified priority.
     *
     * @param yaw Desired yaw angle (horizontal rotation)
     * @param pitch Desired pitch angle (vertical rotation, negative = up, positive = down)
     * @param priority Priority level (0-100, lower = higher precedence)
     * @param callback Optional callback to execute after rotation is applied (can be null)
     */
    @JvmOverloads
    fun queue(
        yaw: Float,
        pitch: Float,
        priority: Int,
        callback: Runnable? = null,
    ) {
        // Clamp priority to valid range
        val clampedPriority = max(0, min(100, priority))
        rotations.add(PendingRotation(yaw, pitch, clampedPriority, callback))
    }

    /**
     * Queue a rotation request using a Rotation object.
     *
     * @param rotation The rotation to apply
     * @param priority Priority level (0-100, lower = higher precedence)
     * @param callback Optional callback (can be null)
     */
    fun queue(
        rotation: Rotation,
        priority: Int,
        callback: Runnable? = null,
    ) {
        queue(rotation.yaw, rotation.pitch, priority, callback)
    }

    override fun onPlayerUpdate(event: PlayerUpdateEvent?) {
        // Process rotation queue before player update (movement packet send)
        if (currentRotation == null && rotations.isNotEmpty()) {
            currentRotation = rotations.poll()
        }

        currentRotation?.let { rotation ->
            // Apply rotation to player
            ctx.player().yRot = rotation.yaw
            ctx.player().xRot = rotation.pitch

            // Execute callback if provided
            rotation.callback?.let { callback ->
                try {
                    callback.run()
                } catch (e: Exception) {
                    log.atError().setCause(e).log("Rotation callback failed")
                }
            }

            // Clear current rotation (single-use)
            currentRotation = null
        }
    }

    override fun onTick(event: TickEvent?) {
        // Clear old rotations if queue gets too large (prevent memory leak)
        if (rotations.size > 100) {
            log
                .atWarn()
                .addKeyValue("queue_size", rotations.size)
                .log("Queue overflow, clearing rotations")
            rotations.clear()
        }
    }

    /** Returns true if there are pending rotations in the queue.  */
    fun hasPendingRotations(): Boolean = rotations.isNotEmpty() || currentRotation != null

    /** Clears all pending rotations (emergency stop).  */
    fun clear() {
        rotations.clear()
        currentRotation = null
    }

    /** Represents a pending rotation request with priority and callback.  */
    private data class PendingRotation(
        val yaw: Float,
        val pitch: Float,
        val priority: Int,
        val callback: Runnable?,
    )

    /** Priority level constants for common use cases.  */
    object Priority {
        const val CRITICAL: Int = 100
        const val HIGH: Int = 75
        const val NORMAL: Int = 50
        const val LOW: Int = 25
    }

    companion object {
        private val log: Logger = Loggers.Rotation.get()
    }
}
