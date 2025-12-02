package maestro.pathing.calc

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import maestro.Agent
import maestro.api.pathing.calc.IPath
import maestro.api.pathing.calc.IPathFinder
import maestro.api.pathing.goals.Goal
import maestro.api.utils.LoggingUtils
import maestro.api.utils.MaestroLogger
import maestro.api.utils.PackedBlockPos
import maestro.api.utils.PathCalculationResult
import maestro.pathing.movement.CalculationContext
import org.slf4j.Logger
import java.util.Optional
import kotlin.math.sqrt

/**
 * Any pathfinding algorithm that keeps track of nodes recursively by their cost (e.g. A*, dijkstra)
 */
abstract class AbstractNodeCostSearch(
    protected val realStart: PackedBlockPos,
    protected val startX: Int,
    protected val startY: Int,
    protected val startZ: Int,
    @JvmField protected val goal: Goal,
    private val context: CalculationContext,
) : IPathFinder {
    /**
     * @see [Issue #107](https://github.com/cabaletta/baritone/issues/107)
     */
    private val map: Long2ObjectOpenHashMap<PathNode> =
        Long2ObjectOpenHashMap(
            Agent.settings().pathingMapDefaultSize.value,
            Agent.settings().pathingMapLoadFactor.value,
        )

    protected var startNode: PathNode? = null

    protected var mostRecentConsidered: PathNode? = null

    protected val bestSoFar: Array<PathNode?> = arrayOfNulls(COEFFICIENTS.size)

    @Volatile
    private var isFinished: Boolean = false

    protected var cancelRequested: Boolean = false

    fun cancel() {
        cancelRequested = true
    }

    @Synchronized
    override fun calculate(
        primaryTimeout: Long,
        failureTimeout: Long,
    ): PathCalculationResult {
        check(!isFinished) { "Path finder cannot be reused!" }
        cancelRequested = false

        return try {
            var path: IPath? =
                calculate0(primaryTimeout, failureTimeout).map { it.postProcess() }.orElse(null)

            if (cancelRequested) {
                return PathCalculationResult(PathCalculationResult.Type.CANCELLATION)
            }
            if (path == null) {
                return PathCalculationResult(PathCalculationResult.Type.FAILURE)
            }

            var previousLength = path.length()
            path = path.cutoffAtLoadedChunks(context.bsi)
            if (path.length() < previousLength) {
                log.atDebug().log("Cutting off path at edge of loaded chunks")
                log
                    .atDebug()
                    .addKeyValue("length_decrease", previousLength - path.length())
                    .log("Path length decreased")
            } else {
                log.atDebug().log("Path ends within loaded chunks")
            }

            previousLength = path.length()
            path = path.staticCutoff(goal)
            if (path.length() < previousLength) {
                log
                    .atDebug()
                    .addKeyValue("previous_length", previousLength)
                    .addKeyValue("new_length", path.length())
                    .log("Static cutoff applied")
            }

            if (goal.isInGoal(path.dest.toBlockPos())) {
                PathCalculationResult(PathCalculationResult.Type.SUCCESS_TO_GOAL, path)
            } else {
                PathCalculationResult(PathCalculationResult.Type.SUCCESS_SEGMENT, path)
            }
        } catch (e: Exception) {
            log.atError().setCause(e).log("Pathing exception")
            PathCalculationResult(PathCalculationResult.Type.EXCEPTION)
        } finally {
            // this is run regardless of what exception may or may not be raised by calculate0
            isFinished = true
        }
    }

    protected abstract fun calculate0(
        primaryTimeout: Long,
        failureTimeout: Long,
    ): Optional<IPath>

    /**
     * Determines the distance squared from the specified node to the start node. Intended for use
     * in distance comparison, rather than anything that considers the real distance value, hence
     * the "sq".
     *
     * @param n A node
     * @return The distance, squared
     */
    protected fun getDistFromStartSq(n: PathNode): Double {
        val xDiff = n.x - startX
        val yDiff = n.y - startY
        val zDiff = n.z - startZ
        return (xDiff * xDiff + yDiff * yDiff + zDiff * zDiff).toDouble()
    }

    /**
     * Attempts to search the block position hashCode long to [PathNode] map for the node
     * mapped to the specified pos. If no node is found, a new node is created.
     *
     * @param x The x position of the node
     * @param y The y position of the node
     * @param z The z position of the node
     * @param hashCode The hash code of the node, provided by [BetterBlockPos.longHash]
     * @return The associated node
     * @see [Issue #107](https://github.com/cabaletta/baritone/issues/107)
     */
    protected fun getNodeAtPosition(
        x: Int,
        y: Int,
        z: Int,
        hashCode: Long,
    ): PathNode {
        var node = map.get(hashCode)
        if (node == null) {
            node = PathNode(x, y, z, goal)
            map.put(hashCode, node)
        }
        return node
    }

    override fun pathToMostRecentNodeConsidered(): Optional<IPath> =
        Optional
            .ofNullable(mostRecentConsidered)
            .map { node -> Path(realStart, startNode!!, node, 0, goal, context) }

    override fun bestPathSoFar(): Optional<IPath> = bestSoFar(false, 0, 0, null)

    protected fun bestSoFar(
        logInfo: Boolean,
        numNodes: Int,
        durationMs: Long,
        failureReason: PathfindingFailureReason?,
    ): Optional<IPath> {
        if (startNode == null) {
            return Optional.empty()
        }

        var bestDist = 0.0
        for (i in COEFFICIENTS.indices) {
            val node = bestSoFar[i] ?: continue
            val dist = getDistFromStartSq(node)
            if (dist > bestDist) {
                bestDist = dist
            }
            // square the comparison since distFromStartSq is squared
            if (dist > MIN_DIST_PATH * MIN_DIST_PATH) {
                if (logInfo) {
                    log
                        .atDebug()
                        .addKeyValue("cost_coefficient", COEFFICIENTS[i])
                        .log("Using A* cost coefficient")
                }
                return Optional.of(Path(realStart, startNode!!, node, numNodes, goal, context))
            }
        }

        // instead of returning bestSoFar[0], be less misleading
        // if it actually won't find any path, don't make them think it will by rendering a dark
        // blue that will never actually happen
        if (logInfo) {
            requireNotNull(failureReason) { "failureReason required when logInfo=true" }

            val maxDistReached = sqrt(bestDist)
            val goalDescription = goal.toString()

            // Calculate distance to goal for progress estimation
            val startToGoalDist = goal.heuristic(startX, startY, startZ)
            val progressPct =
                if (startToGoalDist > 0.0) {
                    (maxDistReached / startToGoalDist * 100.0).coerceIn(0.0, 100.0)
                } else {
                    0.0
                }

            // Choose log level based on failure severity
            val logBuilder =
                when (failureReason) {
                    PathfindingFailureReason.UNREACHABLE,
                    PathfindingFailureReason.FAILURE_TIMEOUT,
                    -> log.atWarn()
                    PathfindingFailureReason.PRIMARY_TIMEOUT,
                    PathfindingFailureReason.CHUNK_LOAD_LIMIT,
                    -> log.atInfo()
                    PathfindingFailureReason.CANCELLED -> log.atDebug()
                }

            logBuilder
                .addKeyValue("reason", failureReason.name.lowercase())
                .addKeyValue("goal", goalDescription)
                .addKeyValue("start", LoggingUtils.formatCoords(startNode!!.x, startNode!!.y, startNode!!.z))
                .addKeyValue("nodes_explored", numNodes)
                .addKeyValue("max_distance_blocks", LoggingUtils.formatFloat(maxDistReached))
                .addKeyValue("duration_ms", durationMs)

            // Add reason-specific context and message
            when (failureReason) {
                PathfindingFailureReason.UNREACHABLE -> {
                    logBuilder.log("Goal unreachable - stuck at start position")
                }
                PathfindingFailureReason.PRIMARY_TIMEOUT -> {
                    logBuilder
                        .addKeyValue("progress_pct", LoggingUtils.formatFloat(progressPct))
                        .log("Timeout - making partial progress toward goal")
                }
                PathfindingFailureReason.FAILURE_TIMEOUT -> {
                    logBuilder.log("Failure timeout - maximum search time exhausted")
                }
                PathfindingFailureReason.CHUNK_LOAD_LIMIT -> {
                    logBuilder
                        .addKeyValue("chunk_fetch_limit", Agent.settings().pathingMaxChunkBorderFetch.value)
                        .log("Chunk load limit - unloaded chunks block path")
                }
                PathfindingFailureReason.CANCELLED -> {
                    logBuilder.log("Pathfinding cancelled")
                }
            }
        }
        return Optional.empty()
    }

    final override fun isFinished(): Boolean = isFinished

    override fun getGoal(): Goal = goal

    fun getStart(): PackedBlockPos = PackedBlockPos(startX, startY, startZ)

    protected fun mapSize(): Int = map.size

    companion object {
        private val log: Logger = MaestroLogger.get("path")

        /**
         * This is really complicated and hard to explain. I wrote a comment in the old version of
         * MineBot, but it was so long it was easier as a Google Doc (because I could insert charts).
         *
         * @see [here](https://docs.google.com/document/d/1WVHHXKXFdCR1Oz__KtK8sFqyvSwJN_H4lftkHFgmzlc/edit)
         */
        @JvmField
        protected val COEFFICIENTS = doubleArrayOf(1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0)

        /**
         * If a path goes less than 5 blocks and doesn't make it to its goal, it's not worth
         * considering.
         */
        protected const val MIN_DIST_PATH = 5.0

        /**
         * there are floating point errors caused by random combinations of traverse and diagonal over a
         * flat area that means that sometimes there's a cost improvement of like 10 ^ -16 it's not
         * worth the time to update the costs, decrease-key the heap, potentially repropagate, etc
         *
         * who cares about a hundredth of a tick? that's half a millisecond for crying out loud!
         */
        protected const val MIN_IMPROVEMENT = 0.01
    }
}
