package maestro.pathing.recovery;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import maestro.Agent;
import maestro.api.utils.MaestroLogger;
import maestro.api.utils.PackedBlockPos;
import maestro.pathing.movement.Movement;
import org.slf4j.Logger;

/**
 * Tracks recently failed movements to avoid retrying the same failures repeatedly.
 *
 * <p>When a movement fails (teleport rejected, world changed, timeout, etc.), this memory applies
 * exponential cost penalties to discourage immediate retries. Failures expire after a configurable
 * duration.
 *
 * <p>Tracks failures by source→destination pairs, since a movement failure is specific to the
 * combination of start and end positions, not just one position.
 *
 * <p>Thread-safe for use across pathfinding and execution threads.
 */
public class MovementFailureMemory {

    private static final Logger log = MaestroLogger.get("path");
    private static final int MAX_RECORDS_PER_MOVEMENT = 10;

    private final Agent agent;
    private final ConcurrentHashMap<MovementKey, List<MovementFailureRecord>> failures;

    public MovementFailureMemory(Agent agent) {
        this.agent = agent;
        this.failures = new ConcurrentHashMap<>();
    }

    /**
     * Records a movement failure.
     *
     * @param movement the movement that failed
     * @param reason why it failed
     */
    public void recordFailure(Movement movement, FailureReason reason) {
        MovementKey key = new MovementKey(movement.getSrc(), movement.getDest());
        Class<? extends Movement> movementType = movement.getClass();
        long currentTime = System.currentTimeMillis();

        failures.compute(
                key,
                (movementKey, records) -> {
                    if (records == null) {
                        records = new ArrayList<>();
                    }

                    // Find existing record for this movement type
                    int attemptCount = 1;
                    for (MovementFailureRecord record : records) {
                        if (record.movementType.equals(movementType)
                                && !record.isExpired(
                                        currentTime,
                                        Agent.settings().movementFailureMemoryDuration.value)) {
                            attemptCount = record.attemptCount + 1;
                            records.remove(record);
                            break;
                        }
                    }

                    // Create new record
                    MovementFailureRecord newRecord =
                            new MovementFailureRecord(
                                    key, movementType, currentTime, reason, attemptCount);
                    records.add(newRecord);

                    log.atDebug()
                            .addKeyValue("source", key.source)
                            .addKeyValue("destination", key.destination)
                            .addKeyValue("movement_type", movementType.getSimpleName())
                            .addKeyValue("reason", reason)
                            .addKeyValue("attempt_count", attemptCount)
                            .log("Movement failure recorded");

                    // Limit records per movement
                    if (records.size() > MAX_RECORDS_PER_MOVEMENT) {
                        records.sort(Comparator.comparingLong(r -> r.timestamp));
                        records.remove(0);
                    }

                    return records;
                });
    }

    /**
     * Calculates cost penalty multiplier for a specific movement.
     *
     * <p>Returns 1.0 if no failures, or exponential penalty based on failure count.
     *
     * @param source source position
     * @param destination destination position
     * @param movementType type of movement
     * @return cost multiplier (1.0 = no penalty, higher = penalized)
     */
    public double getCostPenalty(
            PackedBlockPos source,
            PackedBlockPos destination,
            Class<? extends Movement> movementType) {
        MovementKey key = new MovementKey(source, destination);
        List<MovementFailureRecord> records = failures.get(key);
        if (records == null || records.isEmpty()) {
            return 1.0;
        }

        long currentTime = System.currentTimeMillis();
        long memoryDuration = Agent.settings().movementFailureMemoryDuration.value;
        double penaltyMultiplier = Agent.settings().movementFailurePenaltyMultiplier.value;
        double maxPenalty = Agent.settings().movementFailureMaxPenalty.value;

        double multiplier = 1.0;
        for (MovementFailureRecord record : records) {
            if (record.movementType.equals(movementType)
                    && !record.isExpired(currentTime, memoryDuration)) {
                // Exponential penalty: base^attemptCount
                double penalty = Math.pow(penaltyMultiplier, record.attemptCount);
                multiplier *= penalty;

                log.atDebug()
                        .addKeyValue("source", source)
                        .addKeyValue("destination", destination)
                        .addKeyValue("movement_type", movementType.getSimpleName())
                        .addKeyValue("attempt_count", record.attemptCount)
                        .addKeyValue("penalty", penalty)
                        .addKeyValue("total_multiplier", multiplier)
                        .log("Applying failure penalty");
            }
        }

        return Math.min(multiplier, maxPenalty);
    }

    /**
     * Checks if a specific movement should be filtered out entirely due to excessive failures.
     *
     * @param source source position
     * @param destination destination position
     * @param movementType type of movement
     * @return true if movement should be filtered (too many failures)
     */
    public boolean shouldFilter(
            PackedBlockPos source,
            PackedBlockPos destination,
            Class<? extends Movement> movementType) {
        MovementKey key = new MovementKey(source, destination);
        List<MovementFailureRecord> records = failures.get(key);
        if (records == null || records.isEmpty()) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long memoryDuration = Agent.settings().movementFailureMemoryDuration.value;
        int maxAttempts = Agent.settings().movementFailureMaxAttempts.value;

        for (MovementFailureRecord record : records) {
            if (record.movementType.equals(movementType)
                    && !record.isExpired(currentTime, memoryDuration)
                    && record.attemptCount >= maxAttempts) {
                log.atDebug()
                        .addKeyValue("source", source)
                        .addKeyValue("destination", destination)
                        .addKeyValue("movement_type", movementType.getSimpleName())
                        .addKeyValue("attempt_count", record.attemptCount)
                        .addKeyValue("max_attempts", maxAttempts)
                        .log("Filtering movement due to excessive failures");

                return true;
            }
        }

        return false;
    }

    /**
     * Removes expired failure records.
     *
     * <p>Should be called periodically (every 5 seconds) to prevent memory leaks.
     */
    public void cleanup() {
        long currentTime = System.currentTimeMillis();
        long memoryDuration = Agent.settings().movementFailureMemoryDuration.value;
        int removedCount = 0;

        Iterator<Map.Entry<MovementKey, List<MovementFailureRecord>>> iterator =
                failures.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<MovementKey, List<MovementFailureRecord>> entry = iterator.next();
            List<MovementFailureRecord> records = entry.getValue();

            records.removeIf(record -> record.isExpired(currentTime, memoryDuration));
            removedCount += records.size();

            if (records.isEmpty()) {
                iterator.remove();
            }
        }

        if (removedCount > 0) {
            log.atDebug()
                    .addKeyValue("removed_count", removedCount)
                    .addKeyValue("remaining_movements", failures.size())
                    .log("Cleaned up expired failure records");
        }
    }

    /**
     * Clears all failure records.
     *
     * <p>Used when changing dimensions or when world state changes significantly.
     */
    public void clear() {
        int clearedCount = failures.size();
        failures.clear();

        if (clearedCount > 0) {
            log.atInfo()
                    .addKeyValue("cleared_count", clearedCount)
                    .log("Cleared all failure records");
        }
    }

    /** Returns number of movement pairs with failure records */
    public int size() {
        return failures.size();
    }

    /**
     * Returns total number of failure records across all positions.
     *
     * <p>For debugging and statistics.
     */
    public int totalRecords() {
        return failures.values().stream().mapToInt(List::size).sum();
    }
}
