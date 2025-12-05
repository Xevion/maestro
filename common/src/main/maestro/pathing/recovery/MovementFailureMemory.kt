package maestro.pathing.recovery

import maestro.Agent
import maestro.api.utils.Loggers
import maestro.api.utils.PackedBlockPos
import maestro.pathing.movement.Movement
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.math.pow

/**
 * Tracks recently failed movements to avoid retrying the same failures repeatedly.
 *
 * When a movement fails (teleport rejected, world changed, timeout, etc.), this memory applies
 * exponential cost penalties to discourage immediate retries. Failures expire after a configurable
 * duration.
 *
 * Tracks failures by source→destination pairs, since a movement failure is specific to the
 * combination of start and end positions, not just one position.
 *
 * Thread-safe for use across pathfinding and execution threads.
 */
class MovementFailureMemory(
    private val agent: Agent,
) {
    private val failures = ConcurrentHashMap<MovementKey, MutableList<MovementFailureRecord>>()

    /**
     * Records a movement failure.
     *
     * @param movement the movement that failed
     * @param reason why it failed
     */
    fun recordFailure(
        movement: Movement,
        reason: FailureReason,
    ) {
        val key = MovementKey(movement.src, movement.dest)
        val movementType = movement::class.java
        val currentTime = System.currentTimeMillis()

        failures.compute(key) { _, records ->
            val recordList = records ?: mutableListOf()

            // Find existing record for this movement type
            val existingRecord =
                recordList.find { record ->
                    record.movementType == movementType &&
                        !record.isExpired(currentTime, Agent.settings().movementFailureMemoryDuration.value)
                }

            val attemptCount = (existingRecord?.attemptCount ?: 0) + 1
            if (existingRecord != null) {
                recordList.remove(existingRecord)
            }

            // Create new record
            val newRecord =
                MovementFailureRecord(
                    key,
                    movementType,
                    currentTime,
                    reason,
                    attemptCount,
                )
            recordList.add(newRecord)

            log
                .atDebug()
                .addKeyValue("source", key.source)
                .addKeyValue("destination", key.destination)
                .addKeyValue("movement_type", movementType.simpleName)
                .addKeyValue("reason", reason)
                .addKeyValue("attempt_count", attemptCount)
                .log("Movement failure recorded")

            // Limit records per movement
            if (recordList.size > MAX_RECORDS_PER_MOVEMENT) {
                recordList.sortBy { it.timestamp }
                recordList.removeAt(0)
            }

            recordList
        }
    }

    /**
     * Calculates cost penalty multiplier for a specific movement.
     *
     * Returns 1.0 if no failures, or exponential penalty based on failure count.
     *
     * @param source source position
     * @param destination destination position
     * @param movementType type of movement
     * @return cost multiplier (1.0 = no penalty, higher = penalized)
     */
    fun getCostPenalty(
        source: PackedBlockPos,
        destination: PackedBlockPos,
        movementType: Class<out Movement>,
    ): Double {
        val key = MovementKey(source, destination)
        val records = failures[key] ?: return 1.0

        val currentTime = System.currentTimeMillis()
        val memoryDuration = Agent.settings().movementFailureMemoryDuration.value
        val penaltyMultiplier = Agent.settings().movementFailurePenaltyMultiplier.value
        val maxPenalty = Agent.settings().movementFailureMaxPenalty.value

        val multiplier =
            records
                .asSequence()
                .filter { record ->
                    record.movementType == movementType &&
                        !record.isExpired(currentTime, memoryDuration)
                }.fold(1.0) { acc, record ->
                    // Exponential penalty: base^attemptCount
                    val penalty = penaltyMultiplier.pow(record.attemptCount)
                    val newMultiplier = acc * penalty

                    log
                        .atDebug()
                        .addKeyValue("source", source)
                        .addKeyValue("destination", destination)
                        .addKeyValue("movement_type", movementType.simpleName)
                        .addKeyValue("attempt_count", record.attemptCount)
                        .addKeyValue("penalty", penalty)
                        .addKeyValue("total_multiplier", newMultiplier)
                        .log("Applying failure penalty")

                    newMultiplier
                }

        return min(multiplier, maxPenalty)
    }

    /**
     * Checks if a specific movement should be filtered out entirely due to excessive failures.
     *
     * @param source source position
     * @param destination destination position
     * @param movementType type of movement
     * @return true if movement should be filtered (too many failures)
     */
    fun shouldFilter(
        source: PackedBlockPos,
        destination: PackedBlockPos,
        movementType: Class<out Movement>,
    ): Boolean {
        val key = MovementKey(source, destination)
        val records = failures[key] ?: return false

        val currentTime = System.currentTimeMillis()
        val memoryDuration = Agent.settings().movementFailureMemoryDuration.value
        val maxAttempts = Agent.settings().movementFailureMaxAttempts.value

        return records.any { record ->
            if (record.movementType == movementType &&
                !record.isExpired(currentTime, memoryDuration) &&
                record.attemptCount >= maxAttempts
            ) {
                log
                    .atDebug()
                    .addKeyValue("source", source)
                    .addKeyValue("destination", destination)
                    .addKeyValue("movement_type", movementType.simpleName)
                    .addKeyValue("attempt_count", record.attemptCount)
                    .addKeyValue("max_attempts", maxAttempts)
                    .log("Filtering movement due to excessive failures")
                true
            } else {
                false
            }
        }
    }

    /**
     * Removes expired failure records.
     *
     * Should be called periodically (every 5 seconds) to prevent memory leaks.
     */
    fun cleanup() {
        val currentTime = System.currentTimeMillis()
        val memoryDuration = Agent.settings().movementFailureMemoryDuration.value
        var removedCount = 0

        val iterator = failures.entries.iterator()
        while (iterator.hasNext()) {
            val (_, records) = iterator.next()

            val initialSize = records.size
            records.removeIf { record -> record.isExpired(currentTime, memoryDuration) }
            removedCount += initialSize - records.size

            if (records.isEmpty()) {
                iterator.remove()
            }
        }

        if (removedCount > 0) {
            log
                .atDebug()
                .addKeyValue("removed_count", removedCount)
                .addKeyValue("remaining_movements", failures.size)
                .log("Cleaned up expired failure records")
        }
    }

    /**
     * Clears all failure records.
     *
     * Used when changing dimensions or when world state changes significantly.
     */
    fun clear() {
        val clearedCount = failures.size
        failures.clear()

        if (clearedCount > 0) {
            log
                .atInfo()
                .addKeyValue("cleared_count", clearedCount)
                .log("Cleared all failure records")
        }
    }

    /** Returns number of movement pairs with failure records */
    fun size(): Int = failures.size

    /**
     * Returns total number of failure records across all positions.
     *
     * For debugging and statistics.
     */
    fun totalRecords(): Int = failures.values.sumOf { it.size }

    companion object {
        private val log: Logger = Loggers.get("path")
        private const val MAX_RECORDS_PER_MOVEMENT = 10
    }
}
