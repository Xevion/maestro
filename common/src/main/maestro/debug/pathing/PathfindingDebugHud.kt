package maestro.debug.pathing

import maestro.Agent
import maestro.utils.WorldToScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.phys.Vec3
import java.text.DecimalFormat

/**
 * HUD overlay for pathfinding debug information.
 *
 * Displays:
 * - Hover panel (lower-right): Info about node being looked at
 * - Pinned panel (upper-right): Info about selected/pinned node
 * - Stats summary (upper-left): Path search statistics
 * - 3D text labels: Node index and cost for hovered node and neighbors
 */
class PathfindingDebugHud(
    private val renderer: PathfindingDebugRenderer,
) {
    private val costFormat = DecimalFormat("0.0")

    companion object {
        private const val PANEL_PADDING = 6
        private const val LINE_HEIGHT = 10
        private const val PANEL_MARGIN = 10
        private const val PANEL_BG_COLOR = 0xB0000000.toInt()
        private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        private const val LABEL_COLOR = 0xFFAAAAAA.toInt()
        private const val VALUE_COLOR = 0xFFFFFF00.toInt()
        private const val PATH_COLOR = 0xFF00FF00.toInt()
        private const val OPEN_COLOR = 0xFFFFFF00.toInt()
        private const val CLOSED_COLOR = 0xFF4444FF.toInt()
    }

    /**
     * Renders the debug HUD overlay.
     *
     * @param graphics The GUI graphics context
     * @param partialTicks Partial tick time for interpolation
     */
    fun render(
        graphics: GuiGraphics,
        partialTicks: Float,
    ) {
        if (!Agent.settings().pathfindingDebugEnabled.value) return

        val mc = Minecraft.getInstance()
        val screenWidth = mc.window.guiScaledWidth
        val screenHeight = mc.window.guiScaledHeight

        // Render stats summary (upper-left)
        val snapshotInfo = renderer.getSnapshotInfo()
        if (snapshotInfo != null) {
            renderStatsPanel(graphics, PANEL_MARGIN, PANEL_MARGIN, snapshotInfo)
        }

        // Render pinned node panel (upper-right)
        val selectedInfo = renderer.getSelectedNodeInfo()
        if (selectedInfo != null) {
            val panelWidth = 140
            renderNodePanel(
                graphics,
                screenWidth - panelWidth - PANEL_MARGIN,
                PANEL_MARGIN,
                "PINNED",
                selectedInfo,
            )
        }

        // Render hover panel (lower-right)
        val hoveredInfo = renderer.getHoveredNodeInfo()
        if (hoveredInfo != null && hoveredInfo != selectedInfo) {
            val panelWidth = 140
            val panelHeight = 80
            renderNodePanel(
                graphics,
                screenWidth - panelWidth - PANEL_MARGIN,
                screenHeight - panelHeight - PANEL_MARGIN,
                "LOOKING AT",
                hoveredInfo,
            )
        }

        // TODO: Re-enable 3D text labels once rendering is improved
        // render3DTextLabels(graphics)
    }

    private fun renderStatsPanel(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
        info: SnapshotInfo,
    ) {
        val mc = Minecraft.getInstance()
        val font = mc.font

        val lines =
            buildList {
                add("Pathfinding Debug")
                add("")
                add("Nodes: ${info.nodesExplored}")
                if (info.pathFound) {
                    add("Path: ${info.pathLength} movements")
                } else {
                    add("Path: NOT FOUND")
                }
                add("Time: ${info.durationMs}ms")
                add("Phases: ${info.phases}")
                add("Open set: ${info.openSetSize}")
            }

        val maxWidth = lines.maxOf { font.width(it) }
        val panelWidth = maxWidth + PANEL_PADDING * 2
        val panelHeight = lines.size * LINE_HEIGHT + PANEL_PADDING * 2

        // Background
        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL_BG_COLOR)

        // Text
        var textY = y + PANEL_PADDING
        for ((index, line) in lines.withIndex()) {
            val color =
                when {
                    index == 0 -> TEXT_COLOR
                    line.isEmpty() -> TEXT_COLOR
                    line.startsWith("Path: NOT") -> 0xFFFF4444.toInt()
                    line.startsWith("Path:") -> PATH_COLOR
                    else -> LABEL_COLOR
                }
            graphics.drawString(font, line, x + PANEL_PADDING, textY, color, false)
            textY += LINE_HEIGHT
        }
    }

    private fun renderNodePanel(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
        title: String,
        info: NodeInfo,
    ) {
        val mc = Minecraft.getInstance()
        val font = mc.font

        val stateStr =
            when (info.state) {
                NodeState.PATH -> "PATH"
                NodeState.OPEN -> "OPEN"
                NodeState.CLOSED -> "CLOSED"
            }

        val stateColor =
            when (info.state) {
                NodeState.PATH -> PATH_COLOR
                NodeState.OPEN -> OPEN_COLOR
                NodeState.CLOSED -> CLOSED_COLOR
            }

        val movementStr = info.movementType?.replace("Movement", "") ?: "N/A"

        val lines =
            buildList {
                add(title to TEXT_COLOR)
                add("" to TEXT_COLOR)
                add("Pos: (${info.x}, ${info.y}, ${info.z})" to LABEL_COLOR)
                add("" to TEXT_COLOR)
                add("g: ${costFormat.format(info.g)}" to VALUE_COLOR)
                add("h: ${costFormat.format(info.h)}" to VALUE_COLOR)
                add("f: ${costFormat.format(info.f)}" to VALUE_COLOR)
                add("" to TEXT_COLOR)
                add("State: $stateStr" to stateColor)
                add("Via: $movementStr" to LABEL_COLOR)
                add("#${info.discoveryOrder} of ${info.totalNodes}" to LABEL_COLOR)
            }

        val maxWidth = lines.maxOf { font.width(it.first) }
        val panelWidth = maxWidth + PANEL_PADDING * 2
        val panelHeight = lines.size * LINE_HEIGHT + PANEL_PADDING * 2

        // Background
        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL_BG_COLOR)

        // Text
        var textY = y + PANEL_PADDING
        for ((text, color) in lines) {
            if (text.isNotEmpty()) {
                graphics.drawString(font, text, x + PANEL_PADDING, textY, color, false)
            }
            textY += LINE_HEIGHT
        }
    }

    /**
     * Renders 3D text labels for pathfinding nodes in world space.
     *
     * Shows node index and f-score for:
     * - The currently hovered node
     * - Nodes physically near the hovered node (within Manhattan distance 2)
     *
     * Text is scaled based on distance from camera to remain readable.
     */
    private fun render3DTextLabels(graphics: GuiGraphics) {
        // Get matrices from the 3D render pass
        val modelView = renderer.lastModelView ?: return
        val projection = renderer.lastProjection ?: return

        // Get hovered node
        val hoveredNode = renderer.hoveredNode ?: return
        val snapshot = PathfindingSnapshotStore.currentSnapshot ?: return

        // Get nodes to label (hovered + nearby)
        val nodesToLabel = snapshot.getNodesNear(hoveredNode, maxDistance = 2)

        val mc = Minecraft.getInstance()
        val font = mc.font
        val cameraPos = WorldToScreen.getCameraPos()

        for (node in nodesToLabel) {
            // Project node center to screen space
            val worldPos = Vec3(node.x + 0.5, node.y + 0.5, node.z + 0.5)
            val screenPos = WorldToScreen.project(worldPos, modelView, projection) ?: continue

            // Calculate distance-based scale and opacity
            val distance = worldPos.distanceTo(cameraPos)
            val scale = calculateTextScale(distance)
            val opacity = calculateTextOpacity(distance)

            if (opacity < 0.05f) continue // Skip nearly invisible text

            // Format text: "#123 f=12.5"
            val text = "#${node.discoveryOrder} f=${costFormat.format(node.f)}"

            // Apply scale transform
            val pose = graphics.pose()
            pose.pushPose()
            pose.translate(screenPos.x, screenPos.y, 0.0)
            pose.scale(scale, scale, 1.0f)

            // Calculate scaled text dimensions
            val textWidth = font.width(text)
            val textX = -textWidth / 2 // Center horizontally
            val textY = -4 // Offset slightly above center

            // Base color with opacity applied
            val baseColor =
                when {
                    snapshot.isOnPath(node.packedPos) -> PATH_COLOR
                    node.inOpenSet -> OPEN_COLOR
                    else -> CLOSED_COLOR
                }
            val color = applyOpacity(baseColor, opacity)
            val bgColor = applyOpacity(PANEL_BG_COLOR, opacity * 0.8f)

            // Background for readability
            graphics.fill(
                textX - 2,
                textY - 1,
                textX + textWidth + 2,
                textY + LINE_HEIGHT - 1,
                bgColor,
            )

            // Render text
            graphics.drawString(font, text, textX, textY, color, false)

            pose.popPose()
        }
    }

    /**
     * Calculates scale factor for text based on distance from camera.
     *
     * Text scales down with distance (natural perspective).
     *
     * @param distance Distance from camera in blocks
     * @return Scale factor (1.0 at close range, decreases with distance)
     */
    private fun calculateTextScale(distance: Double): Float =
        when {
            distance < 2.0 -> 1.0f // Full size very close
            distance < 8.0 -> (1.0f - (distance - 2.0f) / 12.0f).toFloat() // Scale down 2-8 blocks
            else -> 0.5f // Half size at far distance
        }.coerceIn(0.5f, 1.0f)

    /**
     * Calculates opacity for text based on distance from camera.
     *
     * Text fades out quickly at distance.
     *
     * @param distance Distance from camera in blocks
     * @return Opacity (1.0 = fully opaque, 0.0 = fully transparent)
     */
    private fun calculateTextOpacity(distance: Double): Float =
        when {
            distance < 3.0 -> 1.0f // Fully opaque up to 3 blocks
            distance < 10.0 -> (1.0f - (distance - 3.0f) / 7.0f).toFloat() // Fade quickly 3-10 blocks
            else -> 0.0f // Fully transparent beyond 10 blocks
        }.coerceIn(0.0f, 1.0f)

    /**
     * Applies opacity to an ARGB color value.
     */
    private fun applyOpacity(
        color: Int,
        opacity: Float,
    ): Int {
        val alpha = ((color ushr 24) * opacity).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }
}
