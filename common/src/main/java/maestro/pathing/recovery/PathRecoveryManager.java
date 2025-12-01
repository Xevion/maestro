package maestro.pathing.recovery;

import java.util.Optional;
import maestro.Agent;
import maestro.api.pathing.calc.IPath;
import maestro.api.pathing.goals.GoalBlock;
import maestro.api.pathing.movement.IMovement;
import maestro.api.pathing.movement.MovementStatus;
import maestro.api.utils.BetterBlockPos;
import maestro.api.utils.MaestroLogger;
import maestro.behavior.PathingBehavior;
import maestro.pathing.movement.CalculationContext;
import maestro.pathing.movement.Movement;
import org.slf4j.Logger;

/**
 * Orchestrates recovery strategies when movements fail or path deviates.
 *
 * <p>Implements a decision tree: Try retry → Try reconnection → Trigger recalculation. This
 * centralizes all recovery logic so PathExecutor only needs to execute the decision.
 */
public class PathRecoveryManager {

    private static final Logger log = MaestroLogger.get("path");

    private final PathingBehavior behavior;
    private final MovementRetryEngine retryEngine;
    private final PathReconnection pathReconnection;
    private final RetryBudget retryBudget;

    public PathRecoveryManager(PathingBehavior behavior) {
        this.behavior = behavior;
        this.retryEngine = new MovementRetryEngine(behavior);
        this.pathReconnection = new PathReconnection();
        this.retryBudget = new RetryBudget();
    }

    /**
     * Handles movement failure by attempting recovery strategies.
     *
     * <p>Decision flow:
     *
     * <ol>
     *   <li>Record failure in MovementFailureMemory
     *   <li>Check if retry budget allows retry
     *   <li>Try to find alternative movement
     *   <li>If found → Return RETRY_MOVEMENT
     *   <li>Otherwise → Return CANCEL_PATH
     * </ol>
     *
     * @param failedMovement The movement that failed
     * @param currentPosition Bot's current position
     * @param currentPath The path being executed
     * @param pathPosition Current position in path
     * @param status Movement status (UNREACHABLE or FAILED)
     * @param reason Failure reason for logging
     * @return Recovery action to take
     */
    public RecoveryAction handleMovementFailure(
            Movement failedMovement,
            BetterBlockPos currentPosition,
            IPath currentPath,
            int pathPosition,
            MovementStatus status,
            FailureReason reason) {

        log.atDebug()
                .addKeyValue("movement_type", failedMovement.getClass().getSimpleName())
                .addKeyValue("source", failedMovement.getSrc())
                .addKeyValue("dest", failedMovement.getDest())
                .addKeyValue("status", status)
                .addKeyValue("reason", reason)
                .log("Handling movement failure");

        // Record failure (already done by caller, but ensure consistency)
        behavior.failureMemory.recordFailure(failedMovement, reason);

        // Check retry budget
        if (!retryBudget.canRetry(failedMovement.getSrc())) {
            log.atDebug()
                    .addKeyValue("retry_count", retryBudget.getRetryCount(failedMovement.getSrc()))
                    .addKeyValue("decision", "cancel_path")
                    .log("Retry budget exhausted");
            return RecoveryAction.cancelPath();
        }

        // Try to find alternative movement
        Optional<IMovement> alternative =
                retryEngine.findAlternative(failedMovement, currentPosition);

        if (alternative.isPresent()) {
            retryBudget.recordRetry(failedMovement.getSrc());

            log.atInfo()
                    .addKeyValue("failed_movement", failedMovement.getClass().getSimpleName())
                    .addKeyValue(
                            "alternative_movement", alternative.get().getClass().getSimpleName())
                    .addKeyValue("retry_count", retryBudget.getRetryCount(failedMovement.getSrc()))
                    .addKeyValue("decision", "retry_movement")
                    .log("Found alternative movement");

            return RecoveryAction.retryMovement((Movement) alternative.get());
        }

        log.atDebug().addKeyValue("decision", "cancel_path").log("No alternative movement found");
        return RecoveryAction.cancelPath();
    }

    /**
     * Handles corridor deviation by attempting path reconnection.
     *
     * <p>Decision flow:
     *
     * <ol>
     *   <li>Check if path reconnection is enabled
     *   <li>Try to find reconnection point
     *   <li>Calculate reconnection path
     *   <li>Compare cost: reconnection vs full recalc
     *   <li>If reconnection cheaper → Return RECONNECT_PATH
     *   <li>Otherwise → Return CANCEL_PATH
     * </ol>
     *
     * @param currentPosition Bot's current position
     * @param currentPath The path being executed
     * @param corridorPosition Current corridor segment index
     * @param context Calculation context
     * @return Recovery action to take
     */
    public RecoveryAction handleCorridorDeviation(
            BetterBlockPos currentPosition,
            IPath currentPath,
            int corridorPosition,
            CalculationContext context) {

        log.atDebug()
                .addKeyValue("current_position", currentPosition)
                .addKeyValue("corridor_position", corridorPosition)
                .log("Handling corridor deviation");

        // Check if reconnection is enabled
        if (!Agent.settings().pathReconnectionEnabled.value) {
            log.atDebug().addKeyValue("decision", "cancel_path").log("Path reconnection disabled");
            return RecoveryAction.cancelPath();
        }

        // Find reconnection point
        Optional<PathReconnection.ReconnectionCandidate> candidateOpt =
                pathReconnection.findReconnectionPoint(
                        currentPath, corridorPosition, currentPosition, context);

        if (candidateOpt.isEmpty()) {
            log.atDebug()
                    .addKeyValue("decision", "cancel_path")
                    .log("No valid reconnection point found");
            return RecoveryAction.cancelPath();
        }

        PathReconnection.ReconnectionCandidate candidate = candidateOpt.get();

        // Calculate reconnection path to verify reachability
        Optional<IPath> reconnectionPathOpt =
                pathReconnection.calculateReconnectionPath(
                        currentPosition, candidate.position, context);

        if (reconnectionPathOpt.isEmpty()) {
            log.atDebug()
                    .addKeyValue("reconnection_point", candidate.position)
                    .addKeyValue("decision", "cancel_path")
                    .log("Failed to calculate reconnection path");
            return RecoveryAction.cancelPath();
        }

        IPath reconnectionPath = reconnectionPathOpt.get();

        // Compare cost: reconnection vs full recalc.
        // Full recalc needs to get back to path first, then continue
        BetterBlockPos corridorPos = currentPath.positions().get(corridorPosition);
        double returnToCorridorCost = new GoalBlock(corridorPos).heuristic(currentPosition);
        double fullRecalcCost =
                returnToCorridorCost + currentPath.ticksRemainingFrom(corridorPosition);

        double reconnectionCost =
                reconnectionPath.ticksRemainingFrom(0)
                        + currentPath.ticksRemainingFrom(candidate.pathIndex);
        double threshold = Agent.settings().pathReconnectionCostThreshold.value;

        if (!pathReconnection.shouldUseReconnection(reconnectionCost, fullRecalcCost, threshold)) {
            log.atDebug()
                    .addKeyValue("reconnection_cost", reconnectionCost)
                    .addKeyValue("full_recalc_cost", fullRecalcCost)
                    .addKeyValue("threshold", threshold)
                    .addKeyValue("decision", "cancel_path")
                    .log("Reconnection too expensive");
            return RecoveryAction.cancelPath();
        }

        log.atInfo()
                .addKeyValue("reconnection_index", candidate.pathIndex)
                .addKeyValue("reconnection_point", candidate.position)
                .addKeyValue("reconnection_cost", reconnectionCost)
                .addKeyValue("full_recalc_cost", fullRecalcCost)
                .addKeyValue("savings", fullRecalcCost - reconnectionCost)
                .addKeyValue("decision", "reconnect_path")
                .log("Path reconnection preferred");

        return RecoveryAction.reconnectPath(candidate.pathIndex);
    }

    /**
     * Resets retry budget (called when advancing to next path position).
     *
     * <p>This prevents retry budget exhaustion from affecting future positions in the path.
     */
    public void resetRetryBudget() {
        retryBudget.reset();
    }

    /** Recovery action types. */
    public enum RecoveryType {
        /** Try alternative movement */
        RETRY_MOVEMENT,
        /** Reconnect to existing path */
        RECONNECT_PATH,
        /** Cancel and recalculate */
        CANCEL_PATH,
        /** Continue normal execution */
        CONTINUE
    }

    /** Recovery action result with associated data. */
    public static class RecoveryAction {
        private final RecoveryType type;
        private final Movement movement; // For RETRY_MOVEMENT
        private final int reconnectionIndex; // For RECONNECT_PATH

        private RecoveryAction(RecoveryType type, Movement movement, int reconnectionIndex) {
            this.type = type;
            this.movement = movement;
            this.reconnectionIndex = reconnectionIndex;
        }

        public static RecoveryAction retryMovement(Movement movement) {
            return new RecoveryAction(RecoveryType.RETRY_MOVEMENT, movement, -1);
        }

        public static RecoveryAction reconnectPath(int index) {
            return new RecoveryAction(RecoveryType.RECONNECT_PATH, null, index);
        }

        public static RecoveryAction cancelPath() {
            return new RecoveryAction(RecoveryType.CANCEL_PATH, null, -1);
        }

        public static RecoveryAction continueExecution() {
            return new RecoveryAction(RecoveryType.CONTINUE, null, -1);
        }

        public RecoveryType type() {
            return type;
        }

        public Movement movement() {
            if (type != RecoveryType.RETRY_MOVEMENT) {
                throw new IllegalStateException("movement() only valid for RETRY_MOVEMENT actions");
            }
            return movement;
        }

        public int reconnectionIndex() {
            if (type != RecoveryType.RECONNECT_PATH) {
                throw new IllegalStateException(
                        "reconnectionIndex() only valid for RECONNECT_PATH actions");
            }
            return reconnectionIndex;
        }
    }
}
