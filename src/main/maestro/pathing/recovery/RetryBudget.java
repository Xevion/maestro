package maestro.pathing.recovery;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import maestro.api.utils.BetterBlockPos;

/**
 * Tracks retry attempts per position to prevent infinite loops.
 *
 * <p>When a movement fails and we try alternatives, we don't want to keep retrying the same
 * position forever. This budget limits attempts to 3 per position.
 */
public class RetryBudget {

    private static final int MAX_RETRIES = 3;
    private final Map<BetterBlockPos, Integer> retriesByPosition = new ConcurrentHashMap<>();

    /**
     * Checks if we can retry at this position.
     *
     * @param position position to check
     * @return true if we haven't exceeded retry budget
     */
    public boolean canRetry(BetterBlockPos position) {
        return retriesByPosition.getOrDefault(position, 0) < MAX_RETRIES;
    }

    /**
     * Records a retry attempt at this position.
     *
     * @param position position where retry occurred
     */
    public void recordRetry(BetterBlockPos position) {
        retriesByPosition.merge(position, 1, Integer::sum);
    }

    /** Resets the retry budget (called when path changes or succeeds). */
    public void reset() {
        retriesByPosition.clear();
    }

    /**
     * Returns number of retries attempted at a position.
     *
     * @param position position to check
     * @return retry count
     */
    public int getRetryCount(BetterBlockPos position) {
        return retriesByPosition.getOrDefault(position, 0);
    }
}
