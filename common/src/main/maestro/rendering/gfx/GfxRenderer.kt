package maestro.rendering.gfx

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3

/**
 * Central singleton managing all SDF-based rendering operations.
 *
 * Provides unified color handling (ARGB ints), automatic camera-relative
 * coordinate handling, and state management for batched rendering.
 *
 * Usage:
 * ```kotlin
 * GfxRenderer.begin(poseStack, ignoreDepth = true)
 *
 * // Draw various SDF shapes
 * GfxLines.line(start, end, color = 0xFFFF0000.toInt())
 * GfxQuad.quad(center, width, height, color = 0xFF00FF00.toInt())
 * GfxCircle.circle(center, radius, color = 0xFF0000FF.toInt())
 *
 * GfxRenderer.end()
 * ```
 */
object GfxRenderer {
    private var currentPose: PoseStack? = null
    private var depthDisabled = false
    private var cameraPos = Vec3.ZERO
    private var isActive = false

    /**
     * Quality presets for SDF rendering.
     *
     * Higher quality settings use more geometry padding for smoother anti-aliasing
     * and finer miter calculations, at the cost of slightly more GPU work.
     */
    enum class Quality(
        /** Geometry expansion multiplier for AA padding (circles, quads) */
        val aaPadding: Float,
        /** Miter limit for polyline corner joins */
        val miterLimit: Float,
        /** Minimum thickness to prevent sub-pixel artifacts */
        val minThickness: Float,
        /** Description for UI/debugging */
        val description: String,
    ) {
        /** Fast rendering with minimal AA - suitable for many shapes */
        LOW(
            aaPadding = 1.05f,
            miterLimit = 2f,
            minThickness = 0.01f,
            description = "Low quality - faster rendering",
        ),

        /** Balanced quality and performance */
        MEDIUM(
            aaPadding = 1.1f,
            miterLimit = 4f,
            minThickness = 0.005f,
            description = "Medium quality - balanced",
        ),

        /** High quality antialiasing - default */
        HIGH(
            aaPadding = 1.15f,
            miterLimit = 8f,
            minThickness = 0.002f,
            description = "High quality - smooth edges",
        ),

        /** Maximum quality for screenshots/close inspection */
        ULTRA(
            aaPadding = 1.2f,
            miterLimit = 16f,
            minThickness = 0.001f,
            description = "Ultra quality - maximum smoothness",
        ),
    }

    /**
     * Current quality setting. Defaults to HIGH for smooth rendering.
     *
     * This affects:
     * - Anti-aliasing padding on circles and quads
     * - Miter limit for polyline corner joins
     * - Minimum thickness to prevent artifacts
     */
    var quality: Quality = Quality.HIGH

    /**
     * Get the current AA padding multiplier based on quality setting.
     * Used by circle/quad renderers to expand geometry for smooth edges.
     */
    val aaPadding: Float
        get() = quality.aaPadding

    /**
     * Get the current miter limit based on quality setting.
     * Used by polyline renderer for corner join calculations.
     */
    val miterLimit: Float
        get() = quality.miterLimit

    /**
     * Get the minimum thickness based on quality setting.
     * Prevents sub-pixel rendering artifacts on thin lines.
     */
    val minThickness: Float
        get() = quality.minThickness

    /**
     * Apply minimum thickness constraint based on quality setting.
     */
    fun constrainThickness(thickness: Float): Float = maxOf(thickness, minThickness)

    /**
     * Current camera position, available during active rendering.
     */
    val camera: Vec3
        get() = cameraPos

    /**
     * Current pose stack, available during active rendering.
     * @throws IllegalStateException if called outside begin/end block
     */
    val pose: PoseStack
        get() = currentPose ?: error("GfxRenderer.pose accessed outside begin/end block")

    /**
     * Whether depth testing is disabled for current batch.
     */
    val ignoresDepth: Boolean
        get() = depthDisabled

    /**
     * Whether a rendering batch is currently active.
     */
    val active: Boolean
        get() = isActive

    /**
     * Begin an SDF rendering batch.
     *
     * Sets up shared render state (blend, depth, cull) for all SDF primitives.
     * Individual shape renderers (GfxLines, GfxQuad, etc.) can be used after this.
     *
     * @param poseStack The current pose stack with camera transforms applied
     * @param ignoreDepth If true, shapes render through blocks
     */
    fun begin(
        poseStack: PoseStack,
        ignoreDepth: Boolean = false,
    ) {
        check(!isActive) { "GfxRenderer.begin() called while already active. Call end() first." }

        val mc = Minecraft.getInstance()
        val camera = mc.gameRenderer.mainCamera
        cameraPos = camera.position

        currentPose = poseStack
        depthDisabled = ignoreDepth
        isActive = true

        // Shared render state for all SDF primitives
        RenderSystem.enableBlend()
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO,
        )
        RenderSystem.depthMask(false)
        RenderSystem.disableCull()

        if (ignoreDepth) {
            RenderSystem.disableDepthTest()
        }
    }

    /**
     * End the SDF rendering batch and restore render state.
     */
    fun end() {
        check(isActive) { "GfxRenderer.end() called without matching begin()" }

        if (depthDisabled) {
            RenderSystem.enableDepthTest()
        }
        RenderSystem.enableCull()
        RenderSystem.depthMask(true)
        RenderSystem.disableBlend()

        currentPose = null
        depthDisabled = false
        isActive = false
    }

    /**
     * Execute a rendering block with automatic begin/end management.
     *
     * @param poseStack The current pose stack
     * @param ignoreDepth If true, shapes render through blocks
     * @param block The rendering code to execute
     */
    inline fun draw(
        poseStack: PoseStack,
        ignoreDepth: Boolean = false,
        block: () -> Unit,
    ) {
        begin(poseStack, ignoreDepth)
        try {
            block()
        } finally {
            end()
        }
    }

    /**
     * Convert world-space position to camera-relative coordinates.
     */
    fun toCameraSpace(worldPos: Vec3): Vec3 = worldPos.subtract(cameraPos)

    /**
     * Convert world-space coordinates to camera-relative coordinates.
     */
    fun toCameraSpace(
        x: Double,
        y: Double,
        z: Double,
    ): Vec3 = Vec3(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z)

    /**
     * Extract alpha component (0.0-1.0) from ARGB color.
     */
    fun alpha(argb: Int): Float = ((argb shr 24) and 0xFF) / 255f

    /**
     * Extract red component (0.0-1.0) from ARGB color.
     */
    fun red(argb: Int): Float = ((argb shr 16) and 0xFF) / 255f

    /**
     * Extract green component (0.0-1.0) from ARGB color.
     */
    fun green(argb: Int): Float = ((argb shr 8) and 0xFF) / 255f

    /**
     * Extract blue component (0.0-1.0) from ARGB color.
     */
    fun blue(argb: Int): Float = (argb and 0xFF) / 255f

    /**
     * Create ARGB color from components (0.0-1.0).
     */
    fun argb(
        a: Float,
        r: Float,
        g: Float,
        b: Float,
    ): Int =
        ((a * 255).toInt() shl 24) or
            ((r * 255).toInt() shl 16) or
            ((g * 255).toInt() shl 8) or
            (b * 255).toInt()

    /**
     * Create ARGB color from RGB components with full opacity.
     */
    fun rgb(
        r: Float,
        g: Float,
        b: Float,
    ): Int = argb(1f, r, g, b)

    /**
     * Modify alpha of existing ARGB color.
     */
    fun withAlpha(
        argb: Int,
        alpha: Float,
    ): Int = (argb and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)

    /**
     * Convert HSV to ARGB color.
     *
     * @param h Hue (0.0-1.0)
     * @param s Saturation (0.0-1.0)
     * @param v Value/brightness (0.0-1.0)
     * @param a Alpha (0.0-1.0)
     */
    fun hsvToArgb(
        h: Float,
        s: Float,
        v: Float,
        a: Float = 1f,
    ): Int {
        val hi = ((h * 6).toInt() % 6)
        val f = h * 6 - hi
        val p = v * (1 - s)
        val q = v * (1 - f * s)
        val t = v * (1 - (1 - f) * s)

        val (r, g, b) =
            when (hi) {
                0 -> Triple(v, t, p)
                1 -> Triple(q, v, p)
                2 -> Triple(p, v, t)
                3 -> Triple(p, q, v)
                4 -> Triple(t, p, v)
                else -> Triple(v, p, q)
            }

        return argb(a, r, g, b)
    }

    object Colors {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val RED = 0xFFFF0000.toInt()
        const val GREEN = 0xFF00FF00.toInt()
        const val BLUE = 0xFF0000FF.toInt()
        const val YELLOW = 0xFFFFFF00.toInt()
        const val CYAN = 0xFF00FFFF.toInt()
        const val MAGENTA = 0xFFFF00FF.toInt()
        const val ORANGE = 0xFFFF8000.toInt()
        const val PURPLE = 0xFF8000FF.toInt()

        // Semi-transparent variants
        const val WHITE_50 = 0x80FFFFFF.toInt()
        const val RED_50 = 0x80FF0000.toInt()
        const val GREEN_50 = 0x8000FF00.toInt()
        const val BLUE_50 = 0x800000FF.toInt()
    }
}
