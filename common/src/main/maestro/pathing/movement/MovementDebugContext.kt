package maestro.pathing.movement

import com.mojang.blaze3d.vertex.PoseStack
import maestro.utils.IRenderer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.awt.Color

/**
 * Debug context for movement behaviors.
 *
 * Provides a unified API for both 3D rendering and HUD display of movement debug information.
 * Uses inline functions to eliminate overhead when debugging is disabled.
 *
 * All debug calls are made inline during computeIntent(), ensuring no implementation drift
 * between actual computation and debug visualization.
 */
sealed class MovementDebugContext {
    /**
     * Render a 3D line between two points.
     *
     * @param key Unique identifier for this line (replaces previous line with same key)
     * @param from Start position in world coordinates
     * @param to End position in world coordinates
     * @param color Line color (default: white)
     */
    fun line(
        key: String,
        from: Vec3,
        to: Vec3,
        color: Color = Color.WHITE,
    ) {
        if (isEnabled()) lineImpl(key, from, to, color)
    }

    /**
     * Render a 3D point (small sphere/box).
     *
     * @param key Unique identifier for this point
     * @param pos Position in world coordinates
     * @param color Point color (default: yellow)
     * @param size Point radius in blocks (default: 0.1)
     */
    fun point(
        key: String,
        pos: Vec3,
        color: Color = Color.YELLOW,
        size: Float = 0.1f,
    ) {
        if (isEnabled()) pointImpl(key, pos, color, size)
    }

    /**
     * Render a block highlight.
     *
     * @param key Unique identifier for this block
     * @param pos Block position
     * @param color Block color (default: cyan)
     * @param alpha Transparency 0.0-1.0 (default: 0.5)
     */
    fun block(
        key: String,
        pos: BlockPos,
        color: Color = Color.CYAN,
        alpha: Float = 0.5f,
    ) {
        if (isEnabled()) blockImpl(key, pos, color, alpha)
    }

    // GUI Metrics (compact key:value format)

    /**
     * Add a numeric metric to HUD display.
     *
     * Rendered as "key:formatted_value" (e.g., "dist:0.52")
     *
     * Automatically formats based on type:
     * - Float/Double: 1 decimal place (e.g., "3.1")
     * - Int/Long: No decimal places (e.g., "42")
     *
     * @param key Metric name (should be short, 3-5 chars)
     * @param value Numeric value (Int, Long, Float, or Double)
     */
    fun metric(
        key: String,
        value: Number,
    ) {
        if (isEnabled()) metricImpl(key, value)
    }

    /**
     * Add a boolean flag to HUD display.
     *
     * Rendered as "key:Y" or "key:N"
     *
     * @param key Flag name (should be short)
     * @param enabled Flag state
     */
    fun flag(
        key: String,
        enabled: Boolean,
    ) {
        if (isEnabled()) flagImpl(key, enabled)
    }

    /**
     * Add a status string to HUD display.
     *
     * Rendered as "key:value" (e.g., "drift:ok")
     *
     * @param key Status name (should be short)
     * @param value Status text (should be short, 2-8 chars)
     */
    fun status(
        key: String,
        value: String,
    ) {
        if (isEnabled()) statusImpl(key, value)
    }

    // Abstract interface

    /**
     * Check if debugging is enabled.
     *
     * @return true if debug info should be collected
     */
    abstract fun isEnabled(): Boolean

    /**
     * Generate compact HUD text from collected metrics.
     *
     * Format: "key1:val1 key2:val2 key3:val3"
     *
     * @return formatted HUD string
     */
    abstract fun getHudText(): String

    /**
     * Render all 3D debug primitives.
     *
     * @param stack PoseStack for transformations
     * @param partialTicks Partial tick delta for interpolation
     */
    abstract fun render3D(
        stack: PoseStack,
        partialTicks: Float,
    )

    /**
     * Clear all debug data from previous tick.
     */
    abstract fun clear()

    // Public implementation methods (overridden by Active, no-ops in Disabled)
    abstract fun lineImpl(
        key: String,
        from: Vec3,
        to: Vec3,
        color: Color,
    )

    abstract fun pointImpl(
        key: String,
        pos: Vec3,
        color: Color,
        size: Float,
    )

    abstract fun blockImpl(
        key: String,
        pos: BlockPos,
        color: Color,
        alpha: Float,
    )

    abstract fun metricImpl(
        key: String,
        value: Number,
    )

    abstract fun flagImpl(
        key: String,
        enabled: Boolean,
    )

    abstract fun statusImpl(
        key: String,
        value: String,
    )
}

/**
 * Active debug context that collects and renders debug information.
 */
class ActiveDebugContext : MovementDebugContext() {
    // 3D rendering data
    internal data class LineData(
        val from: Vec3,
        val to: Vec3,
        val color: Color,
    )

    internal data class PointData(
        val pos: Vec3,
        val color: Color,
        val size: Float,
    )

    internal data class BlockData(
        val pos: BlockPos,
        val color: Color,
        val alpha: Float,
    )

    private val lines = mutableMapOf<String, LineData>()
    private val points = mutableMapOf<String, PointData>()
    private val blocks = mutableMapOf<String, BlockData>()

    // HUD data (key -> formatted value)
    private val hudData = LinkedHashMap<String, String>() // Preserve insertion order

    override fun isEnabled(): Boolean = true

    override fun getHudText(): String {
        if (hudData.isEmpty()) return ""
        return hudData.entries.joinToString(" ") { "${it.key}:${it.value}" }
    }

    override fun render3D(
        stack: PoseStack,
        partialTicks: Float,
    ) {
        if (lines.isEmpty() && points.isEmpty() && blocks.isEmpty()) return

        val vpX =
            IRenderer.renderManager
                .renderPosX()
        val vpY =
            IRenderer.renderManager
                .renderPosY()
        val vpZ =
            IRenderer.renderManager
                .renderPosZ()

        // Render lines
        if (lines.isNotEmpty()) {
            val bufferBuilder =
                IRenderer.startLines(
                    Color.WHITE,
                    2.0f,
                    true,
                )

            lines.values.forEach { line ->
                IRenderer.glColor(line.color, 1.0f)
                IRenderer.emitLine(
                    bufferBuilder,
                    stack,
                    line.from.x - vpX,
                    line.from.y - vpY,
                    line.from.z - vpZ,
                    line.to.x - vpX,
                    line.to.y - vpY,
                    line.to.z - vpZ,
                )
            }

            IRenderer.endLines(bufferBuilder, true)
        }

        // Render points (as small boxes)
        if (points.isNotEmpty()) {
            val bufferBuilder =
                IRenderer.startLines(
                    Color.WHITE,
                    2.0f,
                    true,
                )

            points.values.forEach { point ->
                IRenderer.glColor(point.color, 1.0f)
                val halfSize = point.size.toDouble()
                // Don't subtract camera position - emitAABB does that for us
                val minX = point.pos.x - halfSize
                val minY = point.pos.y - halfSize
                val minZ = point.pos.z - halfSize
                val maxX = point.pos.x + halfSize
                val maxY = point.pos.y + halfSize
                val maxZ = point.pos.z + halfSize

                // Draw a small box (emitAABB handles camera offset internally)
                val aabb =
                    net.minecraft.world.phys
                        .AABB(minX, minY, minZ, maxX, maxY, maxZ)
                IRenderer.emitAABB(bufferBuilder, stack, aabb, 0.0)
            }

            IRenderer.endLines(bufferBuilder, true)
        }

        // Render blocks using BlockHighlightRenderer
        if (blocks.isNotEmpty()) {
            val blockMap = blocks.values.associate { it.pos to it.color }
            maestro.debug.BlockHighlightRenderer.renderBlocks(
                stack,
                blockMap,
                maestro.debug.SideHighlights.all(),
                blocks.values.first().alpha,
                true,
            )
        }
    }

    override fun clear() {
        lines.clear()
        points.clear()
        blocks.clear()
        hudData.clear()
    }

    override fun lineImpl(
        key: String,
        from: Vec3,
        to: Vec3,
        color: Color,
    ) {
        lines[key] = LineData(from, to, color)
    }

    override fun pointImpl(
        key: String,
        pos: Vec3,
        color: Color,
        size: Float,
    ) {
        points[key] = PointData(pos, color, size)
    }

    override fun blockImpl(
        key: String,
        pos: BlockPos,
        color: Color,
        alpha: Float,
    ) {
        blocks[key] = BlockData(pos, color, alpha)
    }

    override fun metricImpl(
        key: String,
        value: Number,
    ) {
        hudData[key] =
            when (value) {
                is Float, is Double -> String.format("%.1f", value.toDouble())
                is Int, is Long -> value.toString()
                else -> String.format("%.1f", value.toDouble())
            }
    }

    override fun flagImpl(
        key: String,
        enabled: Boolean,
    ) {
        hudData[key] = if (enabled) "Y" else "N"
    }

    override fun statusImpl(
        key: String,
        value: String,
    ) {
        hudData[key] = value
    }

    // Internal accessors for rendering (package-private)
    internal fun getLines(): Map<String, LineData> = lines

    internal fun getPoints(): Map<String, PointData> = points

    internal fun getBlocks(): Map<String, BlockData> = blocks
}

/**
 * Disabled debug context (no-op singleton).
 *
 * All methods are no-ops. Used when debugging is disabled to avoid overhead.
 */
object DisabledDebugContext : MovementDebugContext() {
    override fun isEnabled(): Boolean = false

    override fun getHudText(): String = ""

    override fun render3D(
        stack: PoseStack,
        partialTicks: Float,
    ) {
    }

    override fun clear() {}

    override fun lineImpl(
        key: String,
        from: Vec3,
        to: Vec3,
        color: Color,
    ) {
    }

    override fun pointImpl(
        key: String,
        pos: Vec3,
        color: Color,
        size: Float,
    ) {
    }

    override fun blockImpl(
        key: String,
        pos: BlockPos,
        color: Color,
        alpha: Float,
    ) {
    }

    override fun metricImpl(
        key: String,
        value: Number,
    ) {
    }

    override fun flagImpl(
        key: String,
        enabled: Boolean,
    ) {
    }

    override fun statusImpl(
        key: String,
        value: String,
    ) {
    }
}
