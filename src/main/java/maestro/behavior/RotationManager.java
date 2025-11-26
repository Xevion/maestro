package maestro.behavior;

import java.util.Comparator;
import java.util.PriorityQueue;
import maestro.Agent;
import maestro.api.event.events.PlayerUpdateEvent;
import maestro.api.event.events.TickEvent;
import maestro.api.utils.MaestroLogger;
import maestro.api.utils.Rotation;
import org.slf4j.Logger;

/**
 * Manages bot rotations with priority-based queueing, separating bot control from user camera view.
 * This allows the bot to control look direction for movement while the user maintains independent
 * camera control.
 *
 * <p>Priority levels (lower = higher precedence):
 *
 * <ul>
 *   <li>CRITICAL (100): Manual user control overrides
 *   <li>HIGH (75): Combat, block interaction
 *   <li>NORMAL (50): Swimming, pathing (default)
 *   <li>LOW (25): Idle look-around
 * </ul>
 */
public final class RotationManager extends Behavior {

    private static final Logger log = MaestroLogger.get("rotation");

    private final PriorityQueue<PendingRotation> rotations;
    private PendingRotation currentRotation = null;

    public RotationManager(Agent maestro) {
        super(maestro);
        // Lower priority value = higher precedence
        this.rotations = new PriorityQueue<>(Comparator.comparingInt(r -> r.priority));
    }

    /**
     * Queue a rotation request with specified priority.
     *
     * @param yaw Desired yaw angle (horizontal rotation)
     * @param pitch Desired pitch angle (vertical rotation, negative = up, positive = down)
     * @param priority Priority level (0-100, lower = higher precedence)
     * @param callback Optional callback to execute after rotation is applied (can be null)
     */
    public void queue(float yaw, float pitch, int priority, Runnable callback) {
        // Clamp priority to valid range
        int clampedPriority = Math.max(0, Math.min(100, priority));
        rotations.add(new PendingRotation(yaw, pitch, clampedPriority, callback));
    }

    /**
     * Queue a rotation request with specified priority (no callback).
     *
     * @param yaw Desired yaw angle
     * @param pitch Desired pitch angle
     * @param priority Priority level (0-100, lower = higher precedence)
     */
    public void queue(float yaw, float pitch, int priority) {
        queue(yaw, pitch, priority, null);
    }

    /**
     * Queue a rotation request using a Rotation object.
     *
     * @param rotation The rotation to apply
     * @param priority Priority level (0-100, lower = higher precedence)
     * @param callback Optional callback (can be null)
     */
    public void queue(Rotation rotation, int priority, Runnable callback) {
        queue(rotation.getYaw(), rotation.getPitch(), priority, callback);
    }

    @Override
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        // Process rotation queue before player update (movement packet send)
        if (currentRotation == null && !rotations.isEmpty()) {
            currentRotation = rotations.poll();
        }

        if (currentRotation != null) {
            // Apply rotation to player
            ctx.player().setYRot(currentRotation.yaw);
            ctx.player().setXRot(currentRotation.pitch);

            // Execute callback if provided
            if (currentRotation.callback != null) {
                try {
                    currentRotation.callback.run();
                } catch (Exception e) {
                    log.atError().setCause(e).log("Rotation callback failed");
                }
            }

            // Clear current rotation (single-use)
            currentRotation = null;
        }
    }

    @Override
    public void onTick(TickEvent event) {
        // Clear old rotations if queue gets too large (prevent memory leak)
        if (rotations.size() > 100) {
            log.atWarn()
                    .addKeyValue("queue_size", rotations.size())
                    .log("Queue overflow, clearing rotations");
            rotations.clear();
        }
    }

    /** Returns true if there are pending rotations in the queue. */
    public boolean hasPendingRotations() {
        return !rotations.isEmpty() || currentRotation != null;
    }

    /** Clears all pending rotations (emergency stop). */
    public void clear() {
        rotations.clear();
        currentRotation = null;
    }

    /** Represents a pending rotation request with priority and callback. */
    static class PendingRotation {
        final float yaw;
        final float pitch;
        final int priority;
        final Runnable callback;

        PendingRotation(float yaw, float pitch, int priority, Runnable callback) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.priority = priority;
            this.callback = callback;
        }
    }

    /** Priority level constants for common use cases. */
    public static final class Priority {
        public static final int CRITICAL = 100;
        public static final int HIGH = 75;
        public static final int NORMAL = 50;
        public static final int LOW = 25;

        private Priority() {
            // Utility class
        }
    }
}
