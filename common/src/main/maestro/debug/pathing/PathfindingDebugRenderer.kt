package maestro.debug.pathing

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import maestro.Agent
import maestro.event.events.RenderEvent
import maestro.event.listener.AbstractGameEventListener
import maestro.pathing.goals.GoalBlock
import maestro.rendering.gfx.GfxCube
import maestro.rendering.gfx.GfxLines
import maestro.rendering.gfx.GfxRenderer
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Renders pathfinding debug visualization in 3D world space.
 *
 * Features:
 * - Distance-based LOD (close nodes show details, far nodes show dots)
 * - View-direction opacity (nodes in view brighter)
 * - Color coding by node state (open, closed, path)
 * - Movement type coloring for edges
 * - Hover highlight with easing animation
 */
class PathfindingDebugRenderer(
    private val agent: Agent,
) : AbstractGameEventListener {
    /** Cached chunk cache for current snapshot */
    private var cachedChunkCache: ChunkNodeCache? = null
    private var cachedSnapshot: PathfindingSnapshot? = null

    /** Currently hovered node (for highlighting) */
    var hoveredNode: SnapshotNode? = null

    /** Currently selected node (for pinned info) */
    var selectedNode: SnapshotNode? = null

    /** Matrices for 3D-to-2D projection (updated each render frame) */
    var lastModelView: org.joml.Matrix4f? = null
    var lastProjection: org.joml.Matrix4f? = null

    /** Hover controller for geometry-aware detection and animation */
    private val hoverController = HoverController()

    override fun onRenderPass(event: RenderEvent) {
        if (!Agent
                .getPrimaryAgent()
                .settings.pathfindingDebugEnabled.value
        ) {
            return
        }

        // Store matrices for 3D-to-2D projection (used by HUD text rendering)
        lastModelView = org.joml.Matrix4f(event.modelViewStack.last().pose())
        lastProjection = org.joml.Matrix4f(event.projectionMatrix)

        val snapshot = PathfindingSnapshotStore.currentSnapshot ?: return

        // Update cache if snapshot changed
        if (snapshot !== cachedSnapshot) {
            cachedSnapshot = snapshot
            cachedChunkCache = ChunkNodeCache(snapshot)
        }

        val cache = cachedChunkCache ?: return
        val settings = Agent.getPrimaryAgent().settings

        val mc = Minecraft.getInstance()
        val camera = mc.gameRenderer.mainCamera
        val cameraPos = camera.position
        val cameraDir = Vec3.directionFromRotation(camera.xRot, camera.yRot)

        val maxNodes = settings.pathfindingDebugMaxNodes.value
        val lodDistance = settings.pathfindingDebugLODDistance.value

        // Get nodes near camera, sorted by distance
        val nearNodes = cache.getNodesNear(cameraPos, lodDistance * 2.5, maxNodes)

        // Update hover detection and animation
        hoverController.update(snapshot, nearNodes, cameraPos, cameraDir, lodDistance, hoveredNode, selectedNode)
        hoveredNode = hoverController.hoveredNode

        // Begin Gfx rendering batch
        GfxRenderer.begin(event.modelViewStack, ignoreDepth = true)

        // Render nodes with LOD
        renderNodes(snapshot, nearNodes, cameraPos, cameraDir, lodDistance)

        // Render edges if enabled
        if (settings.pathfindingDebugShowEdges.value) {
            renderEdges(snapshot, nearNodes, cameraPos, lodDistance)
        }

        // End Gfx rendering batch
        GfxRenderer.end()
    }

    // ==================== Interaction Methods ====================

    /**
     * Handles a mouse click on the debug view.
     *
     * @param button 0 = left, 1 = right, 2 = middle
     * @param isCtrlHeld Whether Ctrl key is held
     * @return True if the click was consumed
     */
    fun handleClick(
        button: Int,
        isCtrlHeld: Boolean,
    ): Boolean {
        if (!Agent
                .getPrimaryAgent()
                .settings.pathfindingDebugEnabled.value
        ) {
            return false
        }

        return when (button) {
            0 -> handleLeftClick(isCtrlHeld)
            1 -> handleRightClick()
            else -> false
        }
    }

    private fun handleLeftClick(isCtrlHeld: Boolean): Boolean {
        val hovered = hoveredNode

        if (isCtrlHeld && hovered != null) {
            // Ctrl+click: Set goal and start pathfinding
            val goal = GoalBlock(hovered.x, hovered.y, hovered.z)
            agent.pathingBehavior.cancelEverything()
            agent.customGoalTask.setGoalAndPath(goal)
            return true
        }

        // Regular click: Toggle selection
        if (hovered != null) {
            selectedNode = if (selectedNode?.packedPos == hovered.packedPos) null else hovered
            return true
        }

        return false
    }

    private fun handleRightClick(): Boolean {
        if (selectedNode != null) {
            selectedNode = null
            return true
        }
        return false
    }

    /**
     * Checks if the debug view should consume keyboard input.
     *
     * @param keyCode The key code
     * @return True if input was consumed
     */
    fun handleKeyPress(keyCode: Int): Boolean {
        if (!Agent
                .getPrimaryAgent()
                .settings.pathfindingDebugEnabled.value
        ) {
            return false
        }

        // ESC clears selection
        if (keyCode == 256 && selectedNode != null) {
            selectedNode = null
            return true
        }

        return false
    }

    // ==================== Info Methods for HUD ====================

    /** Gets info about the hovered node for display. */
    fun getHoveredNodeInfo(): NodeInfo? {
        val node = hoveredNode ?: return null
        val snapshot = PathfindingSnapshotStore.currentSnapshot ?: return null
        return createNodeInfo(node, snapshot)
    }

    /** Gets info about the selected node for display. */
    fun getSelectedNodeInfo(): NodeInfo? {
        val node = selectedNode ?: return null
        val snapshot = PathfindingSnapshotStore.currentSnapshot ?: return null
        return createNodeInfo(node, snapshot)
    }

    /** Gets summary info about the current snapshot. */
    fun getSnapshotInfo(): SnapshotInfo? {
        val snapshot = PathfindingSnapshotStore.currentSnapshot ?: return null
        return SnapshotInfo(
            nodesExplored = snapshot.nodesExplored,
            pathFound = snapshot.pathFound,
            pathLength = snapshot.finalPath?.size ?: 0,
            durationMs = snapshot.totalDurationMs,
            phases = snapshot.phases.size,
            openSetSize = snapshot.openSet.size,
        )
    }

    private fun createNodeInfo(
        node: SnapshotNode,
        snapshot: PathfindingSnapshot,
    ): NodeInfo {
        val state =
            when {
                snapshot.isOnPath(node.packedPos) -> NodeState.PATH
                node.inOpenSet -> NodeState.OPEN
                else -> NodeState.CLOSED
            }

        return NodeInfo(
            x = node.x,
            y = node.y,
            z = node.z,
            g = node.g,
            h = node.h,
            f = node.f,
            state = state,
            movementType = node.movementType,
            discoveryOrder = node.discoveryOrder,
            totalNodes = snapshot.nodesExplored,
        )
    }

    // ==================== Rendering Methods ====================

    private fun renderNodes(
        snapshot: PathfindingSnapshot,
        nodes: List<SnapshotNode>,
        cameraPos: Vec3,
        cameraDir: Vec3,
        lodDistance: Double,
    ) {
        // Group nodes by LOD level for batched rendering
        val detailedNodes = mutableListOf<SnapshotNode>() // 0-10 blocks
        val mediumNodes = mutableListOf<SnapshotNode>() // 10-30 blocks
        val farNodes = mutableListOf<SnapshotNode>() // 30-64 blocks

        val detailedDistSq = (lodDistance / 3).let { it * it }
        val mediumDistSq = lodDistance * lodDistance
        val farDistSq = (lodDistance * 2.5).let { it * it }

        for (node in nodes) {
            val dx = node.x + 0.5 - cameraPos.x
            val dy = node.y + 0.5 - cameraPos.y
            val dz = node.z + 0.5 - cameraPos.z
            val distSq = dx * dx + dy * dy + dz * dz

            when {
                distSq <= detailedDistSq -> detailedNodes.add(node)
                distSq <= mediumDistSq -> mediumNodes.add(node)
                distSq <= farDistSq -> farNodes.add(node)
            }
        }

        // Render far nodes (smallest, fastest)
        if (farNodes.isNotEmpty()) {
            renderFarNodes(snapshot, farNodes, cameraPos, cameraDir)
        }

        // Render medium nodes
        if (mediumNodes.isNotEmpty()) {
            renderMediumNodes(snapshot, mediumNodes, cameraPos, cameraDir)
        }

        // Render detailed nodes (largest, slowest)
        if (detailedNodes.isNotEmpty()) {
            renderDetailedNodes(snapshot, detailedNodes, cameraPos, cameraDir)
        }
    }

    private fun renderFarNodes(
        snapshot: PathfindingSnapshot,
        nodes: List<SnapshotNode>,
        cameraPos: Vec3,
        cameraDir: Vec3,
    ) {
        // Render as tiny transparent cubes
        for (node in nodes) {
            var opacity = getViewOpacity(node, cameraPos, cameraDir) * OPACITY_FAR
            // Boost closed nodes slightly for better visibility
            if (!node.inOpenSet && !snapshot.isOnPath(node.packedPos)) {
                opacity += CLOSED_NODE_OPACITY_BOOST
            }
            if (opacity < 0.02f) continue

            val (size, expandedOpacity) = hoverController.getSizeAndOpacity(node, SIZE_FAR.toDouble(), opacity, selectedNode)

            val aabb =
                AABB(
                    node.x + 0.5 - size / 2,
                    node.y + 0.5 - size / 2,
                    node.z + 0.5 - size / 2,
                    node.x + 0.5 + size / 2,
                    node.y + 0.5 + size / 2,
                    node.z + 0.5 + size / 2,
                )

            val baseColor = getNodeColor(node, snapshot)
            val color = GfxRenderer.withAlpha(GfxRenderer.awtToArgb(baseColor), expandedOpacity)
            GfxCube.filled(aabb, color)
        }
    }

    private fun renderMediumNodes(
        snapshot: PathfindingSnapshot,
        nodes: List<SnapshotNode>,
        cameraPos: Vec3,
        cameraDir: Vec3,
    ) {
        // Render as small transparent cubes
        for (node in nodes) {
            var opacity = getViewOpacity(node, cameraPos, cameraDir) * OPACITY_MEDIUM
            // Boost closed nodes slightly for better visibility
            if (!node.inOpenSet && !snapshot.isOnPath(node.packedPos)) {
                opacity += CLOSED_NODE_OPACITY_BOOST
            }
            if (opacity < 0.02f) continue

            val (size, expandedOpacity) = hoverController.getSizeAndOpacity(node, SIZE_MEDIUM.toDouble(), opacity, selectedNode)

            val aabb =
                AABB(
                    node.x + 0.5 - size / 2,
                    node.y + 0.5 - size / 2,
                    node.z + 0.5 - size / 2,
                    node.x + 0.5 + size / 2,
                    node.y + 0.5 + size / 2,
                    node.z + 0.5 + size / 2,
                )

            val baseColor = getNodeColor(node, snapshot)
            val color = GfxRenderer.withAlpha(GfxRenderer.awtToArgb(baseColor), expandedOpacity)
            GfxCube.filled(aabb, color)
        }
    }

    private fun renderDetailedNodes(
        snapshot: PathfindingSnapshot,
        nodes: List<SnapshotNode>,
        cameraPos: Vec3,
        cameraDir: Vec3,
    ) {
        // Render as 1/3 sized transparent cubes
        for (node in nodes) {
            var opacity = getViewOpacity(node, cameraPos, cameraDir) * OPACITY_DETAILED
            // Boost closed nodes slightly for better visibility
            if (!node.inOpenSet && !snapshot.isOnPath(node.packedPos)) {
                opacity += CLOSED_NODE_OPACITY_BOOST
            }
            if (opacity < 0.02f) continue

            val (size, expandedOpacity) = hoverController.getSizeAndOpacity(node, SIZE_DETAILED.toDouble(), opacity, selectedNode)

            val halfSize = size / 2
            val aabb =
                AABB(
                    node.x + 0.5 - halfSize,
                    node.y + 0.5 - halfSize,
                    node.z + 0.5 - halfSize,
                    node.x + 0.5 + halfSize,
                    node.y + 0.5 + halfSize,
                    node.z + 0.5 + halfSize,
                )

            val baseColor = getNodeColor(node, snapshot)
            val color = GfxRenderer.withAlpha(GfxRenderer.awtToArgb(baseColor), expandedOpacity)
            GfxCube.filled(aabb, color)
        }
    }

    private fun renderEdges(
        snapshot: PathfindingSnapshot,
        nodes: List<SnapshotNode>,
        cameraPos: Vec3,
        lodDistance: Double,
    ) {
        // Only render edges for nodes within medium distance
        val maxDistSq = lodDistance * lodDistance

        val hoveredPos = hoveredNode?.packedPos
        val parentChain = hoverController.parentChain
        val hasHover = hoveredPos != null

        // Edge highlight levels: 0 = none, 1 = partial (one node in chain), 2 = full (both in chain)
        data class EdgeInfo(
            val from: SnapshotNode,
            val to: SnapshotNode,
            val color: java.awt.Color,
            val highlightLevel: Int,
        )

        val edges = mutableListOf<EdgeInfo>()

        for (node in nodes) {
            val prevPos = node.previousPos ?: continue
            val prevNode = snapshot.nodes[prevPos] ?: continue

            // Check distance
            val dx = node.x + 0.5 - cameraPos.x
            val dy = node.y + 0.5 - cameraPos.y
            val dz = node.z + 0.5 - cameraPos.z
            if (dx * dx + dy * dy + dz * dz > maxDistSq) continue

            val color = getEdgeColor(node.movementType)

            // Determine highlight level based on how many nodes are in the parent chain
            val highlightLevel =
                if (!hasHover) {
                    0
                } else {
                    val nodeInChain = node.packedPos == hoveredPos || parentChain.contains(node.packedPos)
                    val prevInChain = prevNode.packedPos == hoveredPos || parentChain.contains(prevNode.packedPos)
                    when {
                        nodeInChain && prevInChain -> 2 // Both in chain - full highlight
                        nodeInChain || prevInChain -> 1 // One in chain - partial highlight
                        else -> 0 // Neither in chain
                    }
                }

            edges.add(EdgeInfo(prevNode, node, color, highlightLevel))
        }

        // Render edges by highlight level (lowest first for proper layering)
        for (edge in edges.filter { it.highlightLevel == 0 }) {
            val opacity = if (hasHover) EDGE_DIM_OPACITY else EDGE_NORMAL_OPACITY
            val color = GfxRenderer.withAlpha(GfxRenderer.awtToArgb(edge.color), opacity)
            val start = BlockPos(edge.from.x, edge.from.y, edge.from.z).center
            val end = BlockPos(edge.to.x, edge.to.y, edge.to.z).center
            GfxLines.line(start, end, color, thickness = 0.015f)
        }

        // Partial highlight edges (one node in chain)
        for (edge in edges.filter { it.highlightLevel == 1 }) {
            val color = GfxRenderer.withAlpha(GfxRenderer.awtToArgb(edge.color), EDGE_PARTIAL_OPACITY)
            val start = BlockPos(edge.from.x, edge.from.y, edge.from.z).center
            val end = BlockPos(edge.to.x, edge.to.y, edge.to.z).center
            GfxLines.line(start, end, color, thickness = 0.02f)
        }

        // Full highlight edges (both nodes in chain)
        for (edge in edges.filter { it.highlightLevel == 2 }) {
            val color = GfxRenderer.withAlpha(GfxRenderer.awtToArgb(edge.color), EDGE_HIGHLIGHT_OPACITY)
            val start = BlockPos(edge.from.x, edge.from.y, edge.from.z).center
            val end = BlockPos(edge.to.x, edge.to.y, edge.to.z).center
            GfxLines.line(start, end, color, thickness = 0.025f)
        }
    }

    private fun getNodeColor(
        node: SnapshotNode,
        snapshot: PathfindingSnapshot,
    ): java.awt.Color {
        // Selected nodes get special color
        if (node.packedPos == selectedNode?.packedPos) return COLOR_SELECTED

        // Hovered nodes use their original state color (no override)
        // Color by search state
        return when {
            snapshot.isOnPath(node.packedPos) -> COLOR_PATH
            node.inOpenSet -> COLOR_OPEN
            else -> COLOR_CLOSED
        }
    }

    private fun getEdgeColor(movementType: String?): java.awt.Color =
        when {
            movementType == null -> COLOR_EDGE_DEFAULT
            movementType.contains("Traverse", ignoreCase = true) -> COLOR_EDGE_TRAVERSE
            movementType.contains("Ascend", ignoreCase = true) -> COLOR_EDGE_ASCEND
            movementType.contains("Descend", ignoreCase = true) -> COLOR_EDGE_DESCEND
            movementType.contains("Diagonal", ignoreCase = true) -> COLOR_EDGE_DIAGONAL
            movementType.contains("Downward", ignoreCase = true) -> COLOR_EDGE_DOWNWARD
            else -> COLOR_EDGE_DEFAULT
        }

    private fun getViewOpacity(
        node: SnapshotNode,
        cameraPos: Vec3,
        cameraDir: Vec3,
    ): Float {
        val toNode =
            Vec3(
                node.x + 0.5 - cameraPos.x,
                node.y + 0.5 - cameraPos.y,
                node.z + 0.5 - cameraPos.z,
            ).normalize()

        val dot = cameraDir.dot(toNode)
        // 1.0 = directly ahead, 0.0 = perpendicular, -1.0 = behind
        return (dot * 0.5 + 0.3).coerceIn(0.1, 0.8).toFloat()
    }

    companion object {
        // Node state colors
        private val COLOR_PATH = java.awt.Color(0, 255, 0) // Green - on final path
        private val COLOR_OPEN = java.awt.Color(255, 255, 0) // Yellow - in open set
        private val COLOR_CLOSED = java.awt.Color(68, 68, 255) // Blue - explored
        private val COLOR_SELECTED = java.awt.Color(0, 255, 255) // Cyan - selected

        // Edge colors by movement type
        private val COLOR_EDGE_DEFAULT = java.awt.Color(128, 128, 128) // Gray
        private val COLOR_EDGE_TRAVERSE = java.awt.Color(180, 180, 180) // Light gray
        private val COLOR_EDGE_ASCEND = java.awt.Color(100, 200, 100) // Light green
        private val COLOR_EDGE_DESCEND = java.awt.Color(255, 165, 0) // Orange
        private val COLOR_EDGE_DIAGONAL = java.awt.Color(180, 100, 255) // Purple
        private val COLOR_EDGE_DOWNWARD = java.awt.Color(255, 100, 100) // Light red

        // Rendering constants - adjust these to tweak visualization
        // Base cube sizes (as fraction of 1 block)
        private const val SIZE_FAR = 0.25 // Far nodes (30-64 blocks)
        private const val SIZE_MEDIUM = 0.33 // Medium nodes (10-30 blocks)
        private const val SIZE_DETAILED = 0.33 // Close nodes (0-10 blocks)

        // Base opacity multipliers (applied to view-direction opacity)
        private const val OPACITY_FAR = 0.08f
        private const val OPACITY_MEDIUM = 0.12f
        private const val OPACITY_DETAILED = 0.18f

        // Closed node opacity boost (closed nodes are slightly more visible by default)
        private const val CLOSED_NODE_OPACITY_BOOST = 0.07f

        // Hover animation
        private const val HOVER_TARGET_SIZE = 0.5 // Size when fully hovered (50% of block)
        private const val HOVER_TARGET_OPACITY = 0.5f // Opacity when fully hovered
        private const val HOVER_ANIMATION_SPEED = 5.0f // Units per second

        // Selected node appearance
        private const val SELECTED_SIZE_MULTIPLIER = 1.8 // How much larger than base
        private const val SELECTED_OPACITY_BOOST = 0.25f // Added opacity
        private const val SELECTED_OPACITY_MAX = 0.6f // Maximum opacity for selected

        // Hover context modifiers
        private const val PARENT_OPACITY_BOOST = 0.15f // Added opacity for parent chain nodes
        private const val DIM_OPACITY_FACTOR = 0.5f // Multiplier for non-hovered nodes (dimming)

        // Edge rendering
        private const val EDGE_NORMAL_OPACITY = 0.5f // Normal edge opacity
        private const val EDGE_HIGHLIGHT_OPACITY = 0.85f // Highlighted edge opacity (both nodes in chain)
        private const val EDGE_PARTIAL_OPACITY = 0.45f // Partial highlight (one node in chain)
        private const val EDGE_DIM_OPACITY = 0.2f // Dimmed edge opacity when hovering
    }

    /**
     * Manages hover detection and animation for pathfinding nodes.
     *
     * Handles geometry-aware raycasting against actual rendered cube sizes
     * and bidirectional easing (ease-out-cubic for increase, ease-in-cubic for decrease).
     */
    internal class HoverController {
        /** Currently hovered node */
        var hoveredNode: SnapshotNode? = null
            private set

        /** Parent chain of hovered node (for highlighting) */
        var parentChain: Set<Long> = emptySet()
            private set

        /** Animation progress (0.0 = not hovered, 1.0 = fully hovered) */
        private var animationProgress = 0.0f

        /** Last hovered node (for detecting changes) */
        private var lastHoveredPackedPos: Long? = null

        /** Last frame time for delta calculation */
        private var lastFrameTime = System.currentTimeMillis()

        /** Cached node sizes from previous frame (for accurate raycasting) */
        private val nodeSizeCache = mutableMapOf<Long, Double>()

        /** Last snapshot reference for detecting changes */
        private var lastSnapshot: PathfindingSnapshot? = null

        /**
         * Updates hover detection and animation.
         *
         * @param snapshot Current pathfinding snapshot
         * @param nearNodes Nodes near camera (candidates for hover)
         * @param cameraPos Camera position
         * @param cameraDir Camera direction (normalized)
         * @param lodDistance LOD distance for size calculation
         * @param currentHovered Previously hovered node (from external state)
         * @param selectedNode Currently selected node
         */
        fun update(
            snapshot: PathfindingSnapshot,
            nearNodes: List<SnapshotNode>,
            cameraPos: Vec3,
            cameraDir: Vec3,
            lodDistance: Double,
            currentHovered: SnapshotNode?,
            selectedNode: SnapshotNode?,
        ) {
            // Update animation timing
            val currentTime = System.currentTimeMillis()
            val deltaTime = (currentTime - lastFrameTime).coerceIn(0, 100) / 1000.0f
            lastFrameTime = currentTime

            // Perform geometry-aware raycast
            hoveredNode = raycastAgainstRenderedGeometry(nearNodes, cameraPos, cameraDir, lodDistance, selectedNode)

            // Update animation progress
            val currentHoveredPos = hoveredNode?.packedPos

            // Detect if snapshot changed (need to recompute parent chain)
            val snapshotChanged = snapshot !== lastSnapshot
            if (snapshotChanged) {
                lastSnapshot = snapshot
            }

            // Reset animation and recompute parent chain if hovered node or snapshot changed
            if (currentHoveredPos != lastHoveredPackedPos || snapshotChanged) {
                if (currentHoveredPos != lastHoveredPackedPos) {
                    animationProgress = 0.0f
                    lastHoveredPackedPos = currentHoveredPos
                }
                // Compute parent chain for hovered node
                parentChain = computeParentChain(hoveredNode, snapshot)
            }

            // Animate towards target
            val targetProgress = if (currentHoveredPos != null) 1.0f else 0.0f

            if (animationProgress < targetProgress) {
                // Increasing - use existing speed
                animationProgress = (animationProgress + deltaTime * HOVER_ANIMATION_SPEED).coerceAtMost(targetProgress)
            } else if (animationProgress > targetProgress) {
                // Decreasing - use existing speed
                animationProgress = (animationProgress - deltaTime * HOVER_ANIMATION_SPEED).coerceAtLeast(targetProgress)
            }

            // Update size cache for next frame's raycast
            updateSizeCache(nearNodes, cameraPos, lodDistance, selectedNode)
        }

        /**
         * Gets size and opacity for a node, applying hover/selection animations.
         *
         * When a node is hovered:
         * - Hovered node: size expands, opacity increases
         * - Parent chain nodes: slight opacity boost
         * - Other nodes: slight opacity decrease (dimming)
         *
         * @param node The node to render
         * @param baseSize Base size without hover
         * @param baseOpacity Base opacity without hover
         * @param selectedNode Currently selected node (or null)
         * @return Pair of (size, opacity) with animations applied
         */
        fun getSizeAndOpacity(
            node: SnapshotNode,
            baseSize: Double,
            baseOpacity: Float,
            selectedNode: SnapshotNode?,
        ): Pair<Double, Float> {
            val isHovered = node.packedPos == hoveredNode?.packedPos
            val isSelected = node.packedPos == selectedNode?.packedPos
            val isParent = parentChain.contains(node.packedPos)
            val wasRecentlyHovered = node.packedPos == lastHoveredPackedPos && animationProgress > 0.0f
            val hasHoveredNode = hoveredNode != null

            return if (isHovered || wasRecentlyHovered) {
                // Apply bidirectional easing
                val t = animationProgress
                val eased =
                    if (isHovered) {
                        // Increasing toward hover - ease-out-cubic (fast start, slow end)
                        1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t)
                    } else {
                        // Decreasing from hover - ease-in-cubic (slow start, fast end)
                        t * t * t
                    }

                // Expand from baseSize to HOVER_TARGET_SIZE
                val size = baseSize + eased * (HOVER_TARGET_SIZE - baseSize)
                val opacity = baseOpacity + eased * (HOVER_TARGET_OPACITY - baseOpacity)
                Pair(size, opacity.coerceIn(0f, 1f))
            } else if (isSelected) {
                // Selected nodes are moderately larger and more opaque
                val size = baseSize * SELECTED_SIZE_MULTIPLIER
                val opacity = (baseOpacity + SELECTED_OPACITY_BOOST).coerceIn(0f, SELECTED_OPACITY_MAX)
                Pair(size, opacity)
            } else if (hasHoveredNode && isParent) {
                // Parent chain nodes: slight opacity boost
                val opacity = (baseOpacity + PARENT_OPACITY_BOOST).coerceIn(0f, 0.5f)
                Pair(baseSize, opacity)
            } else if (hasHoveredNode) {
                // Other nodes: slight opacity decrease when something is hovered
                val opacity = (baseOpacity * DIM_OPACITY_FACTOR).coerceIn(0.02f, 1f)
                Pair(baseSize, opacity)
            } else {
                Pair(baseSize, baseOpacity)
            }
        }

        /**
         * Raycasts against actual rendered geometry (AABBs) instead of block centers.
         *
         * Uses cached sizes from previous frame to avoid circular dependency.
         * Sorts nodes front-to-back along ray for proper occlusion.
         *
         * @return The closest node hit by the ray, or null if none
         */
        private fun raycastAgainstRenderedGeometry(
            nodes: List<SnapshotNode>,
            cameraPos: Vec3,
            cameraDir: Vec3,
            lodDistance: Double,
            selectedNode: SnapshotNode?,
        ): SnapshotNode? {
            val maxDistance = 64.0

            // Calculate distance along ray for each node, filter out nodes behind camera
            val nodesWithDistance =
                nodes.mapNotNull { node ->
                    val nodeCenter = BlockPos(node.x, node.y, node.z).center
                    val toNode = nodeCenter.subtract(cameraPos)
                    val projectionLength = toNode.dot(cameraDir)

                    if (projectionLength < 0 || projectionLength > maxDistance) {
                        null
                    } else {
                        Triple(node, nodeCenter, projectionLength)
                    }
                }

            // Sort front-to-back (closest first for proper occlusion)
            val sortedNodes = nodesWithDistance.sortedBy { it.third }

            // Test each node's rendered AABB in front-to-back order
            for ((node, nodeCenter, projectionLength) in sortedNodes) {
                // Get cached size from previous frame (or fallback to base size)
                val size = nodeSizeCache[node.packedPos] ?: getLODSize(node, cameraPos, lodDistance)

                // Create AABB for this node's rendered geometry
                val halfSize = size / 2
                val aabb =
                    AABB(
                        node.x + 0.5 - halfSize,
                        node.y + 0.5 - halfSize,
                        node.z + 0.5 - halfSize,
                        node.x + 0.5 + halfSize,
                        node.y + 0.5 + halfSize,
                        node.z + 0.5 + halfSize,
                    )

                // Test ray-AABB intersection
                if (rayIntersectsAABB(cameraPos, cameraDir, aabb, maxDistance)) {
                    return node
                }
            }

            return null
        }

        /**
         * Updates the size cache for nodes (used for next frame's raycast).
         */
        private fun updateSizeCache(
            nodes: List<SnapshotNode>,
            cameraPos: Vec3,
            lodDistance: Double,
            selectedNode: SnapshotNode?,
        ) {
            nodeSizeCache.clear()
            for (node in nodes) {
                val baseSize = getLODSize(node, cameraPos, lodDistance)
                val (actualSize, _) = getSizeAndOpacity(node, baseSize, 1.0f, selectedNode)
                nodeSizeCache[node.packedPos] = actualSize
            }
        }

        /**
         * Gets the base LOD size for a node based on distance from camera.
         */
        private fun getLODSize(
            node: SnapshotNode,
            cameraPos: Vec3,
            lodDistance: Double,
        ): Double {
            val dx = node.x + 0.5 - cameraPos.x
            val dy = node.y + 0.5 - cameraPos.y
            val dz = node.z + 0.5 - cameraPos.z
            val distSq = dx * dx + dy * dy + dz * dz

            val detailedDistSq = (lodDistance / 3).let { it * it }
            val mediumDistSq = lodDistance * lodDistance

            return when {
                distSq <= detailedDistSq -> SIZE_DETAILED.toDouble()
                distSq <= mediumDistSq -> SIZE_MEDIUM.toDouble()
                else -> SIZE_FAR.toDouble()
            }
        }

        /**
         * Tests if a ray intersects an AABB.
         *
         * Uses slab method for accurate ray-box intersection.
         */
        private fun rayIntersectsAABB(
            origin: Vec3,
            direction: Vec3,
            aabb: AABB,
            maxDistance: Double,
        ): Boolean {
            var tMin = 0.0
            var tMax = maxDistance

            // X slab
            if (direction.x.let { kotlin.math.abs(it) } > 1e-6) {
                val t1 = (aabb.minX - origin.x) / direction.x
                val t2 = (aabb.maxX - origin.x) / direction.x
                tMin = maxOf(tMin, minOf(t1, t2))
                tMax = minOf(tMax, maxOf(t1, t2))
            } else if (origin.x < aabb.minX || origin.x > aabb.maxX) {
                return false
            }

            // Y slab
            if (direction.y.let { kotlin.math.abs(it) } > 1e-6) {
                val t1 = (aabb.minY - origin.y) / direction.y
                val t2 = (aabb.maxY - origin.y) / direction.y
                tMin = maxOf(tMin, minOf(t1, t2))
                tMax = minOf(tMax, maxOf(t1, t2))
            } else if (origin.y < aabb.minY || origin.y > aabb.maxY) {
                return false
            }

            // Z slab
            if (direction.z.let { kotlin.math.abs(it) } > 1e-6) {
                val t1 = (aabb.minZ - origin.z) / direction.z
                val t2 = (aabb.maxZ - origin.z) / direction.z
                tMin = maxOf(tMin, minOf(t1, t2))
                tMax = minOf(tMax, maxOf(t1, t2))
            } else if (origin.z < aabb.minZ || origin.z > aabb.maxZ) {
                return false
            }

            return tMax >= tMin && tMin <= maxDistance
        }

        /**
         * Computes the parent chain from a node back to the start.
         *
         * @param node The node to trace from
         * @param snapshot The pathfinding snapshot containing node data
         * @return Set of packed positions in the parent chain (excluding the node itself)
         */
        private fun computeParentChain(
            node: SnapshotNode?,
            snapshot: PathfindingSnapshot,
        ): Set<Long> {
            if (node == null) return emptySet()

            val parents = mutableSetOf<Long>()
            var currentPos = node.previousPos

            // Walk back through parent chain (limit to prevent infinite loops)
            var iterations = 0
            while (currentPos != null && iterations < 1000) {
                parents.add(currentPos)
                val parentNode = snapshot.nodes[currentPos] ?: break
                currentPos = parentNode.previousPos
                iterations++
            }

            return parents
        }
    }

    /**
     * Spatial partitioning cache for efficient node lookup and culling.
     *
     * Groups nodes by chunk (16x16 horizontally) for:
     * - Frustum culling at chunk level
     * - Efficient raycast queries
     * - Distance-based LOD batch processing
     */
    private class ChunkNodeCache(
        snapshot: PathfindingSnapshot,
    ) {
        /** Nodes grouped by chunk position (packed as long) */
        private val chunkMap = Long2ObjectOpenHashMap<MutableList<SnapshotNode>>()

        init {
            // Group nodes by chunk
            for (node in snapshot.nodes.values) {
                val chunkKey = packChunkPos(node.x shr 4, node.z shr 4)
                chunkMap.getOrPut(chunkKey) { mutableListOf() }.add(node)
            }
        }

        /**
         * Gets all nodes in a specific chunk.
         */
        fun getNodesInChunk(
            chunkX: Int,
            chunkZ: Int,
        ): List<SnapshotNode> {
            val key = packChunkPos(chunkX, chunkZ)
            return chunkMap[key] ?: emptyList()
        }

        /**
         * Gets nodes within a distance from a position, sorted by distance.
         */
        fun getNodesNear(
            pos: Vec3,
            maxDistance: Double,
            maxNodes: Int,
        ): List<SnapshotNode> {
            val maxDistSq = maxDistance * maxDistance
            val minChunkX = ((pos.x - maxDistance).toInt() shr 4)
            val maxChunkX = ((pos.x + maxDistance).toInt() shr 4)
            val minChunkZ = ((pos.z - maxDistance).toInt() shr 4)
            val maxChunkZ = ((pos.z + maxDistance).toInt() shr 4)

            val nearNodes = mutableListOf<Pair<SnapshotNode, Double>>()

            for (cx in minChunkX..maxChunkX) {
                for (cz in minChunkZ..maxChunkZ) {
                    val nodes = getNodesInChunk(cx, cz)
                    for (node in nodes) {
                        val dx = node.x + 0.5 - pos.x
                        val dy = node.y + 0.5 - pos.y
                        val dz = node.z + 0.5 - pos.z
                        val distSq = dx * dx + dy * dy + dz * dz
                        if (distSq <= maxDistSq) {
                            nearNodes.add(node to distSq)
                        }
                    }
                }
            }

            return nearNodes.sortedBy { it.second }.take(maxNodes).map { it.first }
        }

        companion object {
            private fun packChunkPos(
                chunkX: Int,
                chunkZ: Int,
            ): Long = (chunkX.toLong() and 0xFFFFFFFFL) or ((chunkZ.toLong() and 0xFFFFFFFFL) shl 32)
        }
    }
}

// ==================== Data Classes for HUD ====================

/**
 * Information about a single node for display.
 */
data class NodeInfo(
    val x: Int,
    val y: Int,
    val z: Int,
    val g: Double,
    val h: Double,
    val f: Double,
    val state: NodeState,
    val movementType: String?,
    val discoveryOrder: Int,
    val totalNodes: Int,
)

/**
 * Node search state.
 */
enum class NodeState {
    OPEN,
    CLOSED,
    PATH,
}

/**
 * Summary information about a pathfinding snapshot.
 */
data class SnapshotInfo(
    val nodesExplored: Int,
    val pathFound: Boolean,
    val pathLength: Int,
    val durationMs: Long,
    val phases: Int,
    val openSetSize: Int,
)
