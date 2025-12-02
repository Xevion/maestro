package maestro.pathing.recovery

import maestro.api.utils.PackedBlockPos
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks retry attempts per position to prevent infinite loops.
 *
 * When a movement fails, and we try alternatives, we don't want to keep retrying the same
 * position forever. This budget limits attempts to 3 per position.
 */
class RetryBudget {
    private val retriesByPosition = ConcurrentHashMap<PackedBlockPos, Int>()

    /**
     * Checks if we can retry at this position.
     *
     * @param position position to check
     * @return true if we haven't exceeded retry budget
     */
    fun canRetry(position: PackedBlockPos): Boolean = retriesByPosition.getOrDefault(position, 0) < MAX_RETRIES

    /**
     * Records a retry attempt at this position.
     *
     * @param position position where retry occurred
     */
    fun recordRetry(position: PackedBlockPos) {
        retriesByPosition.merge(position, 1, Int::plus)
    }

    /** Resets the retry budget (called when path changes or succeeds). */
    fun reset() = retriesByPosition.clear()

    /**
     * Returns number of retries attempted at a position.
     *
     * @param position position to check
     * @return retry count
     */
    fun getRetryCount(position: PackedBlockPos): Int = retriesByPosition.getOrDefault(position, 0)

    companion object {
        private const val MAX_RETRIES = 3
    }
}
