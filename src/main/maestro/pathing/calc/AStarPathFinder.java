package maestro.pathing.calc;

import java.util.List;
import java.util.Optional;
import maestro.Agent;
import maestro.api.pathing.calc.IPath;
import maestro.api.pathing.goals.Goal;
import maestro.api.pathing.movement.ActionCosts;
import maestro.api.pathing.movement.IMovement;
import maestro.api.utils.BetterBlockPos;
import maestro.api.utils.MaestroLogger;
import maestro.api.utils.SettingsUtil;
import maestro.pathing.calc.openset.BinaryHeapOpenSet;
import maestro.pathing.movement.CalculationContext;
import maestro.pathing.movement.CompositeMovementProvider;
import maestro.pathing.movement.ContinuousSwimmingProvider;
import maestro.pathing.movement.EnumMovementProvider;
import maestro.pathing.movement.IMovementProvider;
import maestro.pathing.movement.Movement;
import maestro.pathing.movement.TeleportMovementProvider;
import maestro.utils.pathing.BetterWorldBorder;
import maestro.utils.pathing.Favoring;
import maestro.utils.pathing.MutableMoveResult;
import org.slf4j.Logger;

/**
 * The actual A* pathfinding
 *
 * @author leijurv
 */
public final class AStarPathFinder extends AbstractNodeCostSearch {

    private static final Logger log = MaestroLogger.get("path");

    private final Favoring favoring;
    private final CalculationContext calcContext;
    private final IMovementProvider movementProvider;

    public AStarPathFinder(
            BetterBlockPos realStart,
            int startX,
            int startY,
            int startZ,
            Goal goal,
            Favoring favoring,
            CalculationContext context,
            IMovementProvider provider) {
        super(realStart, startX, startY, startZ, goal, context);
        this.favoring = favoring;
        this.calcContext = context;
        this.movementProvider = provider != null ? provider : createDefaultProvider();
    }

    /** Creates the default movement provider with all movement types. */
    private static IMovementProvider createDefaultProvider() {
        return new CompositeMovementProvider(
                new EnumMovementProvider(), // Standard terrestrial movements
                new ContinuousSwimmingProvider(), // Swimming movements
                new TeleportMovementProvider() // Teleport movements (self-filters via settings)
                );
    }

    /** Backward compatibility constructor without movement provider. */
    public AStarPathFinder(
            BetterBlockPos realStart,
            int startX,
            int startY,
            int startZ,
            Goal goal,
            Favoring favoring,
            CalculationContext context) {
        this(realStart, startX, startY, startZ, goal, favoring, context, null);
    }

    @Override
    protected Optional<IPath> calculate0(long primaryTimeout, long failureTimeout) {
        int minY = calcContext.world.dimensionType().minY();
        int height = calcContext.world.dimensionType().height();
        startNode =
                getNodeAtPosition(
                        startX, startY, startZ, BetterBlockPos.longHash(startX, startY, startZ));
        startNode.cost = 0;
        startNode.combinedCost = startNode.estimatedCostToGoal;
        BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
        openSet.insert(startNode);
        double[] bestHeuristicSoFar =
                new double[COEFFICIENTS.length]; // keep track of the best node by the metric of
        // (estimatedCostToGoal + cost / COEFFICIENTS[i])
        for (int i = 0; i < bestHeuristicSoFar.length; i++) {
            bestHeuristicSoFar[i] = startNode.estimatedCostToGoal;
            bestSoFar[i] = startNode;
        }
        MutableMoveResult res = new MutableMoveResult();
        BetterWorldBorder worldBorder = new BetterWorldBorder(calcContext.world.getWorldBorder());
        long startTime = System.currentTimeMillis();
        boolean slowPath = Agent.settings().slowPath.value;
        if (slowPath) {
            log.atDebug()
                    .addKeyValue("slow_timeout_ms", Agent.settings().slowPathTimeoutMS.value)
                    .addKeyValue("normal_timeout_ms", primaryTimeout)
                    .log("Slow path enabled");
        }
        long primaryTimeoutTime =
                startTime + (slowPath ? Agent.settings().slowPathTimeoutMS.value : primaryTimeout);
        long failureTimeoutTime =
                startTime + (slowPath ? Agent.settings().slowPathTimeoutMS.value : failureTimeout);
        boolean failing = true;
        int numNodes = 0;
        int numMovementsConsidered = 0;
        int numEmptyChunk = 0;
        boolean isFavoring = !favoring.isEmpty();
        int timeCheckInterval = 1 << 6;
        int pathingMaxChunkBorderFetch =
                Agent.settings()
                        .pathingMaxChunkBorderFetch
                        .value; // grab all settings beforehand so that changing settings during
        // pathing doesn't cause a crash or unpredictable behavior
        double minimumImprovement =
                Agent.settings().minimumImprovementRepropagation.value ? MIN_IMPROVEMENT : 0;
        while (!openSet.isEmpty()
                && numEmptyChunk < pathingMaxChunkBorderFetch
                && !cancelRequested) {
            if ((numNodes & (timeCheckInterval - 1))
                    == 0) { // only call this once every 64 nodes (about half a millisecond)
                long now = System.currentTimeMillis(); // since nanoTime is slow on windows (takes
                // many microseconds)
                if (now - failureTimeoutTime >= 0 || (!failing && now - primaryTimeoutTime >= 0)) {
                    break;
                }
            }
            if (slowPath) {
                try {
                    Thread.sleep(Agent.settings().slowPathTimeDelayMS.value);
                } catch (InterruptedException ignored) {
                }
            }
            PathNode currentNode = openSet.removeLowest();
            mostRecentConsidered = currentNode;
            numNodes++;
            if (goal.isInGoal(currentNode.x, currentNode.y, currentNode.z)) {
                log.atDebug()
                        .addKeyValue("duration_ms", System.currentTimeMillis() - startTime)
                        .addKeyValue("movements_considered", numMovementsConsidered)
                        .log("Path found");
                return Optional.of(
                        new Path(realStart, startNode, currentNode, numNodes, goal, calcContext));
            }

            // Generate movements for current position
            BetterBlockPos currentPos =
                    new BetterBlockPos(currentNode.x, currentNode.y, currentNode.z);
            List<IMovement> movements =
                    movementProvider.generateMovements(calcContext, currentPos).toList();

            for (IMovement movement : movements) {
                numMovementsConsidered++;

                BetterBlockPos dest = movement.getDest();
                int newX = dest.x;
                int newY = dest.y;
                int newZ = dest.z;

                // Chunk loading check
                if ((newX >> 4 != currentNode.x >> 4 || newZ >> 4 != currentNode.z >> 4)
                        && !calcContext.isLoaded(newX, newZ)) {
                    numEmptyChunk++;
                    continue;
                }

                // World border check
                if (!worldBorder.entirelyContains(newX, newZ)) {
                    continue;
                }

                // Height bounds check
                if (newY > height || newY < minY) {
                    continue;
                }

                // Get cost (already calculated during movement creation)
                double actionCost = movement.getCost();
                if (actionCost >= ActionCosts.COST_INF) {
                    continue;
                }

                // Apply failure memory penalties (only for Movement subclasses)
                if (movement instanceof Movement) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Movement> movementClass =
                            (Class<? extends Movement>) movement.getClass();
                    BetterBlockPos src =
                            new BetterBlockPos(currentNode.x, currentNode.y, currentNode.z);

                    // Check if movement should be filtered due to excessive failures
                    if (calcContext.failureMemory.shouldFilter(src, dest, movementClass)) {
                        log.atDebug()
                                .addKeyValue("source", src)
                                .addKeyValue("destination", dest)
                                .addKeyValue("movement_type", movement.getClass().getSimpleName())
                                .log("Filtered movement due to excessive failures");
                        continue;
                    }

                    // Apply cost penalty based on failure history
                    double penalty =
                            calcContext.failureMemory.getCostPenalty(src, dest, movementClass);
                    if (penalty > 1.0) {
                        log.atDebug()
                                .addKeyValue("source", src)
                                .addKeyValue("destination", dest)
                                .addKeyValue("movement_type", movement.getClass().getSimpleName())
                                .addKeyValue("penalty", penalty)
                                .addKeyValue("original_cost", actionCost)
                                .addKeyValue("penalized_cost", actionCost * penalty)
                                .log("Applying failure penalty");
                        actionCost *= penalty;
                    }

                    // Re-check if penalized cost is now infinite
                    if (actionCost >= ActionCosts.COST_INF) {
                        continue;
                    }
                }

                if (actionCost <= 0 || Double.isNaN(actionCost)) {
                    throw new IllegalStateException(
                            String.format(
                                    "%s from %s %s %s calculated implausible cost %s",
                                    movement.getClass().getSimpleName(),
                                    SettingsUtil.maybeCensor(currentNode.x),
                                    SettingsUtil.maybeCensor(currentNode.y),
                                    SettingsUtil.maybeCensor(currentNode.z),
                                    actionCost));
                }

                long hashCode = BetterBlockPos.longHash(newX, newY, newZ);
                if (isFavoring) {
                    // see issue #18
                    actionCost *= favoring.calculate(hashCode);
                }

                PathNode neighbor = getNodeAtPosition(newX, newY, newZ, hashCode);
                double tentativeCost = currentNode.cost + actionCost;

                if (neighbor.cost - tentativeCost > minimumImprovement) {
                    neighbor.previous = currentNode;
                    neighbor.cost = tentativeCost;
                    neighbor.combinedCost = tentativeCost + neighbor.estimatedCostToGoal;

                    // Store movement reference directly in node
                    neighbor.previousMovement = movement;

                    // Keep ordinal for backward compatibility
                    if (movement instanceof Movement) {
                        neighbor.movementOrdinal = (byte) ((Movement) movement).getMovesOrdinal();
                    }

                    if (neighbor.isOpen()) {
                        openSet.update(neighbor);
                    } else {
                        openSet.insert(neighbor);
                    }

                    for (int i = 0; i < COEFFICIENTS.length; i++) {
                        double heuristic =
                                neighbor.estimatedCostToGoal + neighbor.cost / COEFFICIENTS[i];
                        if (bestHeuristicSoFar[i] - heuristic > minimumImprovement) {
                            bestHeuristicSoFar[i] = heuristic;
                            bestSoFar[i] = neighbor;
                            if (failing
                                    && getDistFromStartSq(neighbor)
                                            > MIN_DIST_PATH * MIN_DIST_PATH) {
                                failing = false;
                            }
                        }
                    }
                }
            }
        }
        if (cancelRequested) {
            return Optional.empty();
        }
        Optional<IPath> result = bestSoFar(true, numNodes);
        if (result.isPresent()) {
            log.atDebug()
                    .addKeyValue("duration_ms", System.currentTimeMillis() - startTime)
                    .addKeyValue("movements_considered", numMovementsConsidered)
                    .log("Path segment found");
        }
        return result;
    }
}
