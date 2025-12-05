package maestro.pathing.calc

import maestro.Agent
import maestro.api.pathing.calc.IPath
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.movement.ActionCosts
import maestro.api.utils.Loggers
import maestro.api.utils.PackedBlockPos
import maestro.api.utils.SettingsUtil
import maestro.api.utils.format
import maestro.api.utils.pack
import maestro.pathing.BetterWorldBorder
import maestro.pathing.MutableMoveResult
import maestro.pathing.PreferredPaths
import maestro.pathing.calc.openset.BinaryHeapOpenSet
import maestro.pathing.movement.CalculationContext
import maestro.pathing.movement.CompositeMovementProvider
import maestro.pathing.movement.IMovementProvider
import maestro.pathing.movement.Movement
import maestro.pathing.movement.StandardMovementProvider
import maestro.pathing.movement.SwimmingProvider
import maestro.pathing.movement.TeleportMovementProvider
import org.slf4j.Logger
import java.util.Optional

/** The actual A* pathfinding */
class AStarPathFinder
    @JvmOverloads
    constructor(
        realStart: PackedBlockPos,
        startX: Int,
        startY: Int,
        startZ: Int,
        goal: Goal,
        private val preferredPaths: PreferredPaths,
        private val calcContext: CalculationContext,
        private val movementProvider: IMovementProvider = createDefaultProvider(),
    ) : AbstractNodeCostSearch(realStart, startX, startY, startZ, goal, calcContext) {
        override fun calculate0(
            primaryTimeout: Long,
            failureTimeout: Long,
        ): Optional<IPath> {
            val minY = calcContext.world.dimensionType().minY()
            val height = calcContext.world.dimensionType().height()
            startNode = getNodeAtPosition(startX, startY, startZ, pack(startX, startY, startZ).packed)
            startNode!!.cost = 0.0
            startNode!!.combinedCost = startNode!!.estimatedCostToGoal
            val openSet = BinaryHeapOpenSet()
            openSet.insert(startNode!!)

            val res = MutableMoveResult()
            val worldBorder = BetterWorldBorder(calcContext.world.worldBorder)
            val startTime = System.currentTimeMillis()

            // Phase configuration for progressive epsilon search
            data class SearchPhase(
                val epsilon: Double,
                val durationMs: Long,
            )
            val phases =
                listOf(
                    SearchPhase(1.0, 200L), // Standard A*
                    SearchPhase(3.0, 150L), // Modest goal bias
                    SearchPhase(10.0, 150L), // Greedy
                    SearchPhase(30.0, 500L), // Very greedy (extended)
                    SearchPhase(100.0, 1000L), // Extremely greedy (extended)
                )

            var currentPhaseIndex = 0
            var currentEpsilon = phases[0].epsilon
            var phaseStartTime = startTime

            var bestNodeThisSearch: PathNode? = startNode
            var bestHeuristicThisSearch = startNode!!.estimatedCostToGoal
            val slowPath = Agent.settings().slowPath.value

            if (slowPath) {
                log
                    .atDebug()
                    .addKeyValue("slow_timeout_ms", Agent.settings().slowPathTimeoutMS.value)
                    .addKeyValue("normal_timeout_ms", primaryTimeout)
                    .log("Slow path enabled")
            }

            val primaryTimeoutTime = startTime + (if (slowPath) Agent.settings().slowPathTimeoutMS.value else primaryTimeout)
            val failureTimeoutTime = startTime + (if (slowPath) Agent.settings().slowPathTimeoutMS.value else failureTimeout)
            var failing = true
            var numNodes = 0
            var numMovementsConsidered = 0
            var numEmptyChunk = 0
            val isFavoring = !preferredPaths.isEmpty
            val timeCheckInterval = 1 shl 6

            // Grab all settings beforehand so that changing settings during pathing doesn't cause a crash or unpredictable behavior
            val pathingMaxChunkBorderFetch = Agent.settings().pathingMaxChunkBorderFetch.value
            val minimumImprovement = if (Agent.settings().minimumImprovementRepropagation.value) MIN_IMPROVEMENT else 0.0

            while (!openSet.isEmpty() && numEmptyChunk < pathingMaxChunkBorderFetch && !cancelRequested) {
                // Only call this once every 64 nodes (about half a millisecond)
                if ((numNodes and (timeCheckInterval - 1)) == 0) {
                    val now = System.currentTimeMillis() // since nanoTime is slow on windows (takes many microseconds)

                    // Debug: Log timing info every 1000 checkpoints (64000 nodes)
                    if ((numNodes and 0xFFFF) == 0) {
                        log
                            .atDebug()
                            .addKeyValue("nodes", numNodes)
                            .addKeyValue("elapsed_ms", now - startTime)
                            .addKeyValue("phase", currentPhaseIndex + 1)
                            .addKeyValue("phase_elapsed_ms", now - phaseStartTime)
                            .addKeyValue("phase_duration_ms", phases[currentPhaseIndex].durationMs)
                            .addKeyValue("failing", failing)
                            .log("Search progress checkpoint")
                    }

                    // Check phase transition
                    if (currentPhaseIndex < phases.size - 1 &&
                        now - phaseStartTime >= phases[currentPhaseIndex].durationMs
                    ) {
                        currentPhaseIndex++
                        val newPhase = phases[currentPhaseIndex]

                        // Only activate extended phases if failing
                        if (currentPhaseIndex >= 3 && !failing) {
                            log
                                .atInfo()
                                .addKeyValue("reason", "not_failing")
                                .log("Skipping extended phases")
                            break
                        }

                        currentEpsilon = newPhase.epsilon
                        phaseStartTime = now
                        openSet.rebuildWithEpsilon(currentEpsilon)

                        log
                            .atDebug()
                            .addKeyValue("phase", currentPhaseIndex + 1)
                            .addKeyValue("epsilon", currentEpsilon)
                            .addKeyValue("elapsed_ms", now - startTime)
                            .log("Phase transition")
                    }

                    // Existing timeout checks
                    if (now - failureTimeoutTime >= 0 || (!failing && now - primaryTimeoutTime >= 0)) {
                        break
                    }
                }

                if (slowPath) {
                    try {
                        Thread.sleep(
                            Agent
                                .settings()
                                .slowPathTimeDelayMS.value
                                .toLong(),
                        )
                    } catch (ignored: InterruptedException) {
                    }
                }

                val currentNode = openSet.removeLowest()
                mostRecentConsidered = currentNode
                numNodes++

                if (goal.isInGoal(currentNode.x, currentNode.y, currentNode.z)) {
                    log
                        .atInfo()
                        .addKeyValue("duration_ms", System.currentTimeMillis() - startTime)
                        .addKeyValue("movements_considered", numMovementsConsidered)
                        .log("Path found")
                    return Optional.of(Path(realStart, startNode!!, currentNode, numNodes, goal, calcContext))
                }

                // Generate movements for current position
                val currentPos = PackedBlockPos(currentNode.x, currentNode.y, currentNode.z)
                val movements = movementProvider.generateMovements(calcContext, currentPos).toList()

                for (movement in movements) {
                    numMovementsConsidered++

                    val dest = movement.dest
                    val newX = dest.x
                    val newY = dest.y
                    val newZ = dest.z

                    // Chunk loading check
                    if ((newX shr 4 != currentNode.x shr 4 || newZ shr 4 != currentNode.z shr 4) &&
                        !calcContext.isLoaded(newX, newZ)
                    ) {
                        numEmptyChunk++
                        continue
                    }

                    // World border check
                    if (!worldBorder.entirelyContains(newX, newZ)) {
                        continue
                    }

                    // Height bounds check
                    if (newY !in minY..height) {
                        continue
                    }

                    // Get cost (already calculated during movement creation)
                    var actionCost = movement.cost
                    if (actionCost >= ActionCosts.COST_INF) {
                        continue
                    }

                    // Apply failure memory penalties (only for Movement subclasses)
                    if (movement is Movement) {
                        val movementClass = movement::class.java
                        val src = PackedBlockPos(currentNode.x, currentNode.y, currentNode.z)

                        // Check if movement should be filtered due to excessive failures
                        if (calcContext.failureMemory.shouldFilter(src, dest, movementClass)) {
                            log
                                .atDebug()
                                .addKeyValue("src", src.format())
                                .addKeyValue("dest", dest.format())
                                .addKeyValue("movement_type", movement::class.java.simpleName)
                                .log("Filtered movement due to excessive failures")
                            continue
                        }

                        // Apply cost penalty based on failure history
                        val penalty = calcContext.failureMemory.getCostPenalty(src, dest, movementClass)
                        if (penalty > 1.0) {
                            log
                                .atDebug()
                                .addKeyValue("src", src.format())
                                .addKeyValue("dest", dest.format())
                                .addKeyValue("movement_type", movement::class.java.simpleName)
                                .addKeyValue("penalty", penalty.format())
                                .addKeyValue("original_cost", actionCost.format())
                                .addKeyValue("penalized_cost", (actionCost * penalty).format())
                                .log("Applying failure penalty")
                            actionCost *= penalty
                        }

                        // Re-check if penalized cost is now infinite
                        if (actionCost >= ActionCosts.COST_INF) {
                            continue
                        }
                    }

                    if (actionCost <= 0 || actionCost.isNaN()) {
                        throw IllegalStateException(
                            String.format(
                                "%s from %s %s %s calculated implausible cost %s",
                                movement::class.java.simpleName,
                                SettingsUtil.maybeCensor(currentNode.x),
                                SettingsUtil.maybeCensor(currentNode.y),
                                SettingsUtil.maybeCensor(currentNode.z),
                                actionCost,
                            ),
                        )
                    }

                    val hashCode = pack(newX, newY, newZ).packed
                    if (isFavoring) {
                        // see issue #18
                        actionCost *= preferredPaths.calculate(hashCode)
                    }

                    val neighbor = getNodeAtPosition(newX, newY, newZ, hashCode)
                    val tentativeCost = currentNode.cost + actionCost

                    if (neighbor.cost - tentativeCost > minimumImprovement) {
                        neighbor.previous = currentNode
                        neighbor.cost = tentativeCost
                        neighbor.combinedCost = tentativeCost + neighbor.estimatedCostToGoal * currentEpsilon

                        // Store movement reference directly in node
                        neighbor.previousMovement = movement

                        if (neighbor.isOpen()) {
                            openSet.update(neighbor)
                        } else {
                            openSet.insert(neighbor)
                        }

                        val heuristic = neighbor.estimatedCostToGoal * currentEpsilon + neighbor.cost
                        if (bestHeuristicThisSearch - heuristic > minimumImprovement) {
                            bestHeuristicThisSearch = heuristic
                            bestNodeThisSearch = neighbor

                            if (failing && getDistFromStartSq(neighbor) > 1.0) {
                                failing = false
                            }
                        }
                    }
                }
            }

            val durationMs = System.currentTimeMillis() - startTime

            // Determine why pathfinding stopped
            val reason =
                when {
                    cancelRequested -> PathfindingFailureReason.CANCELLED
                    numEmptyChunk >= pathingMaxChunkBorderFetch -> PathfindingFailureReason.CHUNK_LOAD_LIMIT
                    failing -> PathfindingFailureReason.UNREACHABLE
                    System.currentTimeMillis() >= failureTimeoutTime -> PathfindingFailureReason.FAILURE_TIMEOUT
                    else -> PathfindingFailureReason.PRIMARY_TIMEOUT
                }

            if (cancelRequested) {
                return Optional.empty()
            }

            val result = bestSoFar(true, numNodes, durationMs, reason, bestNodeThisSearch)
            if (result.isPresent) {
                log
                    .atDebug()
                    .addKeyValue("start", PackedBlockPos(startNode!!.x, startNode!!.y, startNode!!.z).format())
                    .addKeyValue("goal", goal.toString())
                    .addKeyValue("duration_ms", durationMs)
                    .addKeyValue("movements_considered", numMovementsConsidered)
                    .addKeyValue("nodes_explored", numNodes)
                    .log("Path segment found")
            }
            return result
        }

        companion object {
            private val log: Logger = Loggers.Path.get()

            /** Creates the default movement provider with all movement types. */
            fun createDefaultProvider(): IMovementProvider =
                CompositeMovementProvider(
                    StandardMovementProvider(), // Standard terrestrial movements
                    SwimmingProvider(), // Swimming movements
                    TeleportMovementProvider(), // Teleport movements (self-filters via settings)
                )
        }
    }
