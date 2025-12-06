package maestro.pathing.recovery

import maestro.Agent
import maestro.api.pathing.calc.IPath
import maestro.api.pathing.goals.GoalBlock
import maestro.api.pathing.movement.MovementStatus
import maestro.api.utils.Loggers
import maestro.api.utils.PackedBlockPos
import maestro.behavior.PathingBehavior
import maestro.pathing.movement.CalculationContext
import maestro.pathing.movement.Movement
import org.slf4j.Logger

/**
 * Orchestrates recovery strategies when movements fail or path deviates.
 *
 * Implements a decision tree: Try retry → Try reconnection → Trigger recalculation. This
 * centralizes all recovery logic so PathExecutor only needs to execute the decision.
 */
class PathRecoveryManager(
    private val behavior: PathingBehavior,
) {
    private val retryEngine = MovementRetryEngine(behavior)
    private val pathReconnection = PathReconnection()
    private val retryBudget = RetryBudget()

    /**
     * Handles movement failure by attempting recovery strategies.
     *
     * Decision flow:
     * 1. Record failure in MovementFailureMemory
     * 2. Check if retry budget allows retry
     * 3. Try to find alternative movement
     * 4. If found → Return RETRY_MOVEMENT
     * 5. Otherwise → Return CANCEL_PATH
     *
     * @param failedMovement The movement that failed
     * @param currentPosition Bot's current position
     * @param currentPath The path being executed
     * @param pathPosition Current position in path
     * @param status Movement status (UNREACHABLE or FAILED)
     * @param reason Failure reason for logging
     * @return Recovery action to take
     */
    fun handleMovementFailure(
        failedMovement: Movement,
        currentPosition: PackedBlockPos,
        currentPath: IPath,
        pathPosition: Int,
        status: MovementStatus,
        reason: FailureReason,
    ): RecoveryAction {
        log
            .atDebug()
            .addKeyValue("movement_type", failedMovement::class.java.simpleName)
            .addKeyValue("source", failedMovement.src)
            .addKeyValue("dest", failedMovement.dest)
            .addKeyValue("status", status)
            .addKeyValue("reason", reason)
            .log("Handling movement failure")

        // Record failure (already done by caller, but ensure consistency)
        behavior.failureMemory.recordFailure(failedMovement, reason)

        // Check retry budget
        if (!retryBudget.canRetry(failedMovement.src)) {
            log
                .atDebug()
                .addKeyValue("retry_count", retryBudget.getRetryCount(failedMovement.src))
                .addKeyValue("decision", "cancel_path")
                .log("Retry budget exhausted")
            return RecoveryAction.Cancel
        }

        // Try to find alternative movement
        val alternative = retryEngine.findAlternative(failedMovement, currentPosition)

        return if (alternative != null) {
            retryBudget.recordRetry(failedMovement.src)

            log
                .atInfo()
                .addKeyValue("failed_movement", failedMovement::class.java.simpleName)
                .addKeyValue("alternative_movement", alternative::class.java.simpleName)
                .addKeyValue("retry_count", retryBudget.getRetryCount(failedMovement.src))
                .addKeyValue("decision", "retry_movement")
                .log("Found alternative movement")

            RecoveryAction.Retry(alternative as Movement)
        } else {
            log
                .atDebug()
                .addKeyValue("decision", "cancel_path")
                .log("No alternative movement found")
            RecoveryAction.Cancel
        }
    }

    /**
     * Handles corridor deviation by attempting path reconnection.
     *
     * Decision flow:
     * 1. Check if path reconnection is enabled
     * 2. Try to find reconnection point
     * 3. Calculate reconnection path
     * 4. Compare cost: reconnection vs full recalc
     * 5. If reconnection cheaper → Return RECONNECT_PATH
     * 6. Otherwise → Return CANCEL_PATH
     *
     * @param currentPosition Bot's current position
     * @param currentPath The path being executed
     * @param corridorPosition Current corridor segment index
     * @param context Calculation context
     * @return Recovery action to take
     */
    fun handleCorridorDeviation(
        currentPosition: PackedBlockPos,
        currentPath: IPath,
        corridorPosition: Int,
        context: CalculationContext,
    ): RecoveryAction {
        log
            .atDebug()
            .addKeyValue("current_position", currentPosition)
            .addKeyValue("corridor_position", corridorPosition)
            .log("Handling corridor deviation")

        // Check if reconnection is enabled
        if (!Agent
                .getPrimaryAgent()
                .settings.pathReconnectionEnabled.value
        ) {
            log
                .atDebug()
                .addKeyValue("decision", "cancel_path")
                .log("Path reconnection disabled")
            return RecoveryAction.Cancel
        }

        // Find reconnection point
        val candidate =
            pathReconnection.findReconnectionPoint(
                currentPath,
                corridorPosition,
                currentPosition,
                context,
            )

        if (candidate == null) {
            log
                .atDebug()
                .addKeyValue("decision", "cancel_path")
                .log("No valid reconnection point found")
            return RecoveryAction.Cancel
        }

        // Calculate reconnection path to verify reachability
        val reconnectionPath =
            pathReconnection.calculateReconnectionPath(
                currentPosition,
                candidate.position,
                context,
            )

        if (reconnectionPath == null) {
            log
                .atDebug()
                .addKeyValue("reconnection_point", candidate.position)
                .addKeyValue("decision", "cancel_path")
                .log("Failed to calculate reconnection path")
            return RecoveryAction.Cancel
        }

        // Compare cost: reconnection vs full recalc.
        // Full recalc needs to get back to path first, then continue
        val corridorPos = currentPath.positions()[corridorPosition]
        val returnToCorridorCost =
            GoalBlock(corridorPos.toBlockPos()).heuristic(
                currentPosition.x,
                currentPosition.y,
                currentPosition.z,
            )
        val fullRecalcCost = returnToCorridorCost + currentPath.ticksRemainingFrom(corridorPosition)

        val reconnectionCost =
            reconnectionPath.ticksRemainingFrom(0) +
                currentPath.ticksRemainingFrom(candidate.pathIndex)
        val threshold =
            Agent
                .getPrimaryAgent()
                .settings.pathReconnectionCostThreshold.value

        if (!pathReconnection.shouldUseReconnection(reconnectionCost, fullRecalcCost, threshold)) {
            log
                .atDebug()
                .addKeyValue("reconnection_cost", reconnectionCost)
                .addKeyValue("full_recalc_cost", fullRecalcCost)
                .addKeyValue("threshold", threshold)
                .addKeyValue("decision", "cancel_path")
                .log("Reconnection too expensive")
            return RecoveryAction.Cancel
        }

        log
            .atInfo()
            .addKeyValue("reconnection_index", candidate.pathIndex)
            .addKeyValue("reconnection_point", candidate.position)
            .addKeyValue("reconnection_cost", reconnectionCost)
            .addKeyValue("full_recalc_cost", fullRecalcCost)
            .addKeyValue("savings", fullRecalcCost - reconnectionCost)
            .addKeyValue("decision", "reconnect_path")
            .log("Path reconnection preferred")

        return RecoveryAction.Reconnect(candidate.pathIndex)
    }

    /**
     * Resets retry budget (called when advancing to next path position).
     *
     * This prevents retry budget exhaustion from affecting future positions in the path.
     */
    fun resetRetryBudget() = retryBudget.reset()

    companion object {
        private val log: Logger = Loggers.Path.get()
    }
}
