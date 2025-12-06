package maestro.gui.radial

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import maestro.Agent
import maestro.behavior.FreecamMode
import maestro.gui.drawText
import maestro.pathing.goals.GoalBlock
import maestro.pathing.goals.GoalXZ
import maestro.rendering.text.TextRenderer
import maestro.utils.Helper
import maestro.utils.Loggers
import maestro.utils.Rotation
import maestro.utils.RotationUtils
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.CoreShaders
import net.minecraft.network.chat.Component
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.lwjgl.glfw.GLFW
import org.slf4j.Logger
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Radial debug menu that appears when right-click is held.
 *
 * Uses mouse delta tracking (via MixinMouseHandler) to determine which menu item
 * is selected. Camera rotation is blocked while this menu is open.
 */
class RadialMenu :
    Screen(Component.literal("Radial Menu")),
    Helper {
    private var deltaX: Float = 0f
    private var deltaY: Float = 0f
    private var selectedItem: RadialMenuItem? = null
    private val items: List<RadialMenuItem> = buildMenuItems()
    private var centerX: Int = 0
    private var centerY: Int = 0
    private var openedAtMs: Long = 0L

    private fun buildMenuItems(): List<RadialMenuItem> =
        listOf(
            RadialMenuItem(
                id = "stop",
                label = "Stop",
                action = ::executeStopPath,
            ),
            RadialMenuItem(
                id = "goto",
                label = "Goto",
                action = ::executeGotoCursor,
            ),
            RadialMenuItem(
                id = "direction",
                label = "Direction",
                action = ::executePathDirection,
            ),
            RadialMenuItem(
                id = "teleport",
                label = "Teleport",
                action = ::executeTeleport,
            ),
            RadialMenuItem(
                id = "debug_paths",
                label = "Debug",
                action = ::togglePathfindingDebug,
            ),
        )

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        super.init()
        centerX = width / 2
        centerY = height / 2
        openedAtMs = System.currentTimeMillis()
        currentInstance = this
    }

    override fun removed() {
        super.removed()
        if (currentInstance === this) {
            currentInstance = null
        }
    }

    companion object {
        private const val INNER_RADIUS = 30f
        private const val OUTER_RADIUS = 80f
        private const val DEAD_ZONE = 15f

        /** Number of triangle segments per menu item arc (higher = smoother) */
        private const val SEGMENTS_PER_ITEM = 32

        private const val COLOR_BACKGROUND = 0x80000000.toInt()
        private const val COLOR_SEGMENT = 0xCC333333.toInt()
        private const val COLOR_SEGMENT_SELECTED = 0xCC4488FF.toInt()
        private const val COLOR_BORDER = 0xFFAAAAAA.toInt()
        private const val COLOR_TEXT = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_SELECTED = 0xFFFFFF00.toInt()

        private val log: Logger = Loggers.Cmd.get()

        @Volatile
        private var currentInstance: RadialMenu? = null

        fun getInstance(): RadialMenu? = currentInstance
    }

    private fun updateSelectionFromMouse(
        mouseX: Int,
        mouseY: Int,
    ) {
        deltaX = (mouseX - centerX).toFloat()
        deltaY = (mouseY - centerY).toFloat()

        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

        if (distance < DEAD_ZONE) {
            selectedItem = null
            return
        }

        // Calculate angle: 0 = up (negative Y), clockwise
        // atan2(x, -y) gives us angle from "up" direction
        val angle = atan2(deltaX.toDouble(), -deltaY.toDouble()).toFloat() * RotationUtils.RAD_TO_DEG_F
        val normalizedAngle = (angle + 360f) % 360f

        val itemAngle = 360f / items.size
        val index = (normalizedAngle / itemAngle).toInt().coerceIn(0, items.size - 1)
        selectedItem = items[index]
    }

    override fun tick() {
        super.tick()

        // Grace period - don't check release for first 100ms to avoid timing issues
        if (System.currentTimeMillis() - openedAtMs < 100) return

        // Check if right-click is still held
        val window = minecraft?.window?.window ?: return
        val rightClickHeld = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS

        if (!rightClickHeld) {
            // Execute selected action and close
            selectedItem?.action?.invoke()
            minecraft?.setScreen(null)
        }
    }

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        // Update selection based on mouse position relative to center
        updateSelectionFromMouse(mouseX, mouseY)

        // Semi-transparent full-screen overlay
        graphics.fill(0, 0, width, height, COLOR_BACKGROUND)

        renderRadialSegments(graphics)
        renderLabels(graphics)
        renderCenterIndicator(graphics)
    }

    private fun renderRadialSegments(graphics: GuiGraphics) {
        val matrix = graphics.pose().last().pose()
        val itemCount = items.size
        val anglePerItem = 360f / itemCount

        // Set up render state for triangles
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader(CoreShaders.POSITION_COLOR)

        items.forEachIndexed { index, item ->
            val startAngle = index * anglePerItem - 90f // -90 to start from top
            val isSelected = item == selectedItem
            val fillColor = if (isSelected) COLOR_SEGMENT_SELECTED else COLOR_SEGMENT

            renderArcSegment(
                matrix,
                centerX.toFloat(),
                centerY.toFloat(),
                INNER_RADIUS,
                OUTER_RADIUS,
                startAngle,
                anglePerItem,
                fillColor,
            )
        }

        // Render borders between segments
        items.forEachIndexed { index, _ ->
            val angle = (index * anglePerItem - 90f) * RotationUtils.DEG_TO_RAD_F
            renderRadialLine(
                matrix,
                centerX.toFloat(),
                centerY.toFloat(),
                INNER_RADIUS,
                OUTER_RADIUS,
                angle,
                COLOR_BORDER,
            )
        }

        // Render inner and outer ring borders
        renderRingOutline(matrix, centerX.toFloat(), centerY.toFloat(), INNER_RADIUS, COLOR_BORDER)
        renderRingOutline(matrix, centerX.toFloat(), centerY.toFloat(), OUTER_RADIUS, COLOR_BORDER)

        RenderSystem.disableBlend()
    }

    /**
     * Renders a filled arc segment using GPU triangle strip.
     */
    private fun renderArcSegment(
        matrix: Matrix4f,
        cx: Float,
        cy: Float,
        innerRadius: Float,
        outerRadius: Float,
        startAngleDeg: Float,
        sweepAngleDeg: Float,
        color: Int,
    ) {
        val tesselator = Tesselator.getInstance()
        val buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR)

        val startRad = startAngleDeg * RotationUtils.DEG_TO_RAD_F
        val sweepRad = sweepAngleDeg * RotationUtils.DEG_TO_RAD_F

        // Extract ARGB components
        val a = (color shr 24 and 0xFF) / 255f
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        for (i in 0..SEGMENTS_PER_ITEM) {
            val angle = startRad + (sweepRad * i / SEGMENTS_PER_ITEM)
            val cosA = cos(angle.toDouble()).toFloat()
            val sinA = sin(angle.toDouble()).toFloat()

            // Outer vertex first, then inner - creates proper winding for triangle strip
            buffer
                .addVertex(matrix, cx + cosA * outerRadius, cy + sinA * outerRadius, 0f)
                .setColor(r, g, b, a)
            buffer
                .addVertex(matrix, cx + cosA * innerRadius, cy + sinA * innerRadius, 0f)
                .setColor(r, g, b, a)
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }

    /**
     * Renders a radial line from inner to outer radius at the given angle.
     */
    private fun renderRadialLine(
        matrix: Matrix4f,
        cx: Float,
        cy: Float,
        innerRadius: Float,
        outerRadius: Float,
        angleRad: Float,
        color: Int,
    ) {
        val tesselator = Tesselator.getInstance()
        val buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR)

        val cosA = cos(angleRad.toDouble()).toFloat()
        val sinA = sin(angleRad.toDouble()).toFloat()

        val a = (color shr 24 and 0xFF) / 255f
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        buffer
            .addVertex(matrix, cx + cosA * innerRadius, cy + sinA * innerRadius, 0f)
            .setColor(r, g, b, a)
        buffer
            .addVertex(matrix, cx + cosA * outerRadius, cy + sinA * outerRadius, 0f)
            .setColor(r, g, b, a)

        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }

    /**
     * Renders a circular outline at the given radius.
     */
    private fun renderRingOutline(
        matrix: Matrix4f,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
    ) {
        val tesselator = Tesselator.getInstance()
        val buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR)

        val a = (color shr 24 and 0xFF) / 255f
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        val segments = SEGMENTS_PER_ITEM * items.size
        for (i in 0..segments) {
            val angle = 360.0 * i / segments * RotationUtils.DEG_TO_RAD
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()

            buffer
                .addVertex(matrix, cx + cosA * radius, cy + sinA * radius, 0f)
                .setColor(r, g, b, a)
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }

    private fun renderLabels(graphics: GuiGraphics) {
        val itemCount = items.size
        val anglePerItem = 360f / itemCount
        val labelRadius = (INNER_RADIUS + OUTER_RADIUS) / 2

        items.forEachIndexed { index, item ->
            val midAngle = (index * anglePerItem - 90f + anglePerItem / 2) * RotationUtils.DEG_TO_RAD_F
            val labelX = centerX + (labelRadius * cos(midAngle.toDouble())).toInt()
            val labelY = centerY + (labelRadius * sin(midAngle.toDouble())).toInt()

            val isSelected = item == selectedItem
            val textColor = if (isSelected) COLOR_TEXT_SELECTED else COLOR_TEXT

            val textWidth = TextRenderer.getWidthForVanillaFont(item.label, font)
            graphics.drawText(
                font,
                item.label,
                labelX - textWidth / 2,
                labelY - font.lineHeight / 2,
                textColor,
                shadow = true,
            )
        }
    }

    private fun renderCenterIndicator(graphics: GuiGraphics) {
        // Draw small indicator showing current mouse direction
        val indicatorLength = 10
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

        if (distance > 2) {
            val normalizedX = (deltaX / distance * indicatorLength).toInt()
            val normalizedY = (deltaY / distance * indicatorLength).toInt()

            graphics.fill(
                centerX - 2,
                centerY - 2,
                centerX + 2,
                centerY + 2,
                COLOR_BORDER,
            )
            graphics.fill(
                centerX + normalizedX - 1,
                centerY + normalizedY - 1,
                centerX + normalizedX + 1,
                centerY + normalizedY + 1,
                COLOR_TEXT_SELECTED,
            )
        } else {
            // Just center dot when in dead zone
            graphics.fill(
                centerX - 3,
                centerY - 3,
                centerX + 3,
                centerY + 3,
                COLOR_BORDER,
            )
        }
    }

    private fun executeStopPath() {
        val agent = Agent.getPrimaryAgent()
        agent.pathingBehavior.cancelEverything()
        log.atDebug().log("Stop action executed")
    }

    private fun executeGotoCursor() {
        val target = raycastFromFreecam()
        if (target == null) {
            log.atDebug().log("Goto action: raycast returned null")
            return
        }
        if (target.type != HitResult.Type.BLOCK) {
            log.atDebug().log("Goto action: hit result is not a block")
            return
        }
        val blockPos = (target as BlockHitResult).blockPos
        val agent = Agent.getPrimaryAgent()
        agent.customGoalTask.setGoalAndPath(GoalBlock(blockPos))
        log.atDebug().addKeyValue("target", blockPos.toShortString()).log("Goto action executed")
    }

    private fun executePathDirection() {
        val agent = Agent.getPrimaryAgent()
        val yaw = agent.freeLookYaw
        val freecamPos = getFreecamPosition(agent)
        if (freecamPos == null) {
            log.atDebug().log("Direction action: no freecam position")
            return
        }

        val distance = 10_000.0
        val yawRad = yaw * RotationUtils.DEG_TO_RAD
        val dx = -sin(yawRad)
        val dz = cos(yawRad)

        val goal =
            GoalXZ(
                (freecamPos.x + dx * distance).toInt(),
                (freecamPos.z + dz * distance).toInt(),
            )
        agent.customGoalTask.setGoalAndPath(goal)
        log.atDebug().addKeyValue("yaw", yaw).log("Direction action executed")
    }

    private fun executeTeleport() {
        val target = raycastFromFreecam()
        if (target == null) {
            log.atDebug().log("Teleport action: raycast returned null")
            return
        }
        if (target.type != HitResult.Type.BLOCK) {
            log.atDebug().log("Teleport action: hit result is not a block")
            return
        }

        val pos = target.location
        val mc = minecraft
        if (mc == null) {
            log.atDebug().log("Teleport action: minecraft instance null")
            return
        }
        val player = mc.player
        if (player == null) {
            log.atDebug().log("Teleport action: player null")
            return
        }

        player.connection.sendUnsignedCommand("tp ${pos.x} ${pos.y} ${pos.z}")
        log.atDebug().addKeyValue("pos", "${pos.x}, ${pos.y}, ${pos.z}").log("Teleport action executed")
    }

    private fun togglePathfindingDebug() {
        val settings = Agent.getPrimaryAgent().settings
        val wasEnabled = settings.pathfindingDebugEnabled.value

        settings.pathfindingDebugEnabled.value = !wasEnabled

        // Also enable capture when enabling debug
        if (settings.pathfindingDebugEnabled.value) {
            settings.pathfindingDebugCapture.value = true
        }

        log
            .atDebug()
            .addKeyValue("enabled", settings.pathfindingDebugEnabled.value)
            .log("Pathfinding debug toggled")
    }

    private fun getFreecamPosition(agent: Agent): Vec3? {
        val freecam = agent.freecamBehavior
        return when (freecam.mode) {
            FreecamMode.FOLLOW ->
                agent.playerContext
                    .player()
                    .position()
            else -> freecam.position
        }
    }

    private fun raycastFromFreecam(): HitResult? {
        val agent = Agent.getPrimaryAgent()
        val mc = minecraft ?: return null
        val level = mc.level ?: return null
        val player = mc.player ?: return null

        val start = getFreecamPosition(agent) ?: return null
        val rotation = Rotation(agent.freeLookYaw, agent.freeLookPitch)
        val direction = RotationUtils.calcLookDirectionFromRotation(rotation)
        val distance = 1024.0
        val end = start.add(direction.scale(distance))

        return level.clip(
            ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player,
            ),
        )
    }
}
