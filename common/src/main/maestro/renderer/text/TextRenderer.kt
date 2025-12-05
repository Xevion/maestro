package maestro.renderer.text

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import maestro.api.utils.Loggers
import net.minecraft.client.renderer.CoreShaders
import org.lwjgl.system.MemoryUtil
import org.slf4j.Logger
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Main text rendering API.
 *
 * Provides high-quality custom font rendering with multiple pre-generated sizes
 * for crisp text at any scale. Uses Tesselator and CoreShaders for 1.21.4 compatibility.
 */
object TextRenderer {
    private val log: Logger = Loggers.Text.get()

    private val SHADOW_COLOR = TextColor(60, 60, 60, 180)

    // Minecraft's default font is 9px tall, but Monocraft appears smaller at the same height
    // so we use 10px base height to better match vanilla visual size
    private const val RENDER_SCALE = 3.0
    private const val BASE_HEIGHT = 11

    // Font sizes at 2x render scale: 20 (1x), 30 (1.5x), 40 (2x), 60 (3x)
    private val FONT_SIZES = intArrayOf(20, 30, 40, 60)

    private var fonts: Array<Font>? = null
    private var fontBuffer: ByteBuffer? = null
    private var currentFont: Font? = null
    private var building = false
    private var fontScale = 1.0
    private var scale = 1.0
    private var initFailed = false

    @Synchronized
    fun init() {
        if (fonts != null || initFailed) {
            if (fonts != null) {
                log.atDebug().log("Init skipped: already initialized")
            } else {
                log.atDebug().log("Init skipped: previous initialization failed")
            }
            return
        }

        var createdFonts: MutableList<Font>? = null
        var loadedBuffer: ByteBuffer? = null
        try {
            log.atInfo().log("Initializing custom font renderer")
            loadedBuffer = loadFontFromResources()

            log
                .atInfo()
                .addKeyValue("buffer_size", loadedBuffer.remaining())
                .log("Font buffer loaded")

            createdFonts = mutableListOf()
            for (i in FONT_SIZES.indices) {
                log.atDebug().addKeyValue("size", FONT_SIZES[i]).log("Creating font")
                createdFonts.add(Font(loadedBuffer, FONT_SIZES[i]))
            }

            fonts = createdFonts.toTypedArray()
            fontBuffer = loadedBuffer

            log
                .atInfo()
                .addKeyValue("sizes", FONT_SIZES.size)
                .addKeyValue("font_sizes", FONT_SIZES.joinToString(","))
                .log("Custom font renderer initialized")
        } catch (e: Exception) {
            log
                .atError()
                .setCause(e)
                .log("Failed to initialize custom font renderer")
            // Clean up partially created fonts
            createdFonts?.forEach { it.close() }
            if (loadedBuffer != null) {
                MemoryUtil.memFree(loadedBuffer)
            }
            initFailed = true
        }
    }

    private fun loadFontFromResources(): ByteBuffer {
        val resourcePath = "/assets/maestro/fonts/Minecraftia-Regular.ttf"
        val stream: InputStream =
            TextRenderer::class.java.getResourceAsStream(resourcePath)
                ?: throw RuntimeException("Font resource not found: $resourcePath")

        val bytes = stream.use { it.readBytes() }
        val buffer = MemoryUtil.memAlloc(bytes.size)
        buffer.put(bytes).flip()
        return buffer
    }

    fun isInitialized(): Boolean = fonts != null && !initFailed

    fun begin(
        requestedScale: Double = 1.0,
        big: Boolean = false,
    ) {
        check(!building) { "TextRenderer.begin() called while already building" }

        val fontsArray = fonts
        if (fontsArray == null) {
            log.atWarn().log("Begin called with uninitialized fonts")
            return
        }

        val selectedFont =
            if (big) {
                fontsArray.last()
            } else {
                selectFontForScale(requestedScale, fontsArray)
            }
        currentFont = selectedFont

        log
            .atDebug()
            .addKeyValue("font_size", selectedFont.height)
            .addKeyValue("requested_scale", requestedScale)
            .log("Text rendering session started")

        this.building = true
        // Target screen height = BASE_HEIGHT * requestedScale (e.g., 9px for 1.0x)
        // Font atlas height = currentFont.height (e.g., 27px)
        // scale = target / atlas to shrink supersampled glyphs to screen size
        val targetScreenHeight = BASE_HEIGHT * requestedScale
        this.fontScale = selectedFont.height.toDouble()
        this.scale = targetScreenHeight / fontScale
    }

    private fun selectFontForScale(
        requestedScale: Double,
        fontsArray: Array<Font>,
    ): Font {
        // Target atlas height = screen height * render scale
        // e.g., for 1.0x scale: 9px screen * 2x render = 18px atlas
        val targetAtlasHeight = BASE_HEIGHT * requestedScale * RENDER_SCALE

        // Find the smallest font that's at least as large as target
        for (i in fontsArray.indices) {
            if (fontsArray[i].height >= targetAtlasHeight) {
                return fontsArray[i]
            }
        }

        // Fall back to largest font if target exceeds all available sizes
        return fontsArray.last()
    }

    fun getWidth(
        text: String,
        length: Int = text.length,
        shadow: Boolean = false,
    ): Double {
        val font = if (building) currentFont else fonts?.getOrNull(0)
        if (font == null) return 0.0

        return (font.getWidth(text, length) + if (shadow) 1.0 else 0.0) * scale
    }

    /**
     * Gets the width of text as rendered by our custom font, returning an Int
     * suitable for use with vanilla font positioning calculations.
     *
     * @param text The text to measure
     * @param vanillaFont Fallback font if custom renderer is unavailable
     * @param scale Optional scale factor (default 1.0)
     */
    fun getWidthForVanillaFont(
        text: String,
        vanillaFont: net.minecraft.client.gui.Font,
        scale: Float = 1.0f,
    ): Int {
        if (!isInitialized()) {
            return (vanillaFont.width(text) * scale).toInt()
        }

        // Use the base font (index 0) at scale 1.0 for consistent measurement
        val font = fonts?.getOrNull(0) ?: return (vanillaFont.width(text) * scale).toInt()
        val targetScreenHeight = BASE_HEIGHT
        val measureScale = targetScreenHeight / font.height.toDouble()
        return (font.getWidth(text, text.length) * measureScale * scale).toInt()
    }

    fun getHeight(shadow: Boolean = false): Double {
        val font = if (building) currentFont else fonts?.getOrNull(0)
        if (font == null) return 0.0

        return (font.height + if (shadow) 1.0 else 0.0) * scale
    }

    fun render(
        text: String,
        x: Double,
        y: Double,
        color: TextColor,
        shadow: Boolean = false,
    ): Double {
        val font = currentFont
        if (fonts == null || font == null) {
            log.atWarn().log("Render called with uninitialized renderer")
            return x
        }

        val wasBuilding = building
        if (!wasBuilding) begin()

        val width: Double
        if (shadow) {
            val shadowColor =
                TextColor(
                    SHADOW_COLOR.r,
                    SHADOW_COLOR.g,
                    SHADOW_COLOR.b,
                    (color.a / 255.0 * SHADOW_COLOR.a).toInt(),
                )

            val shadowOffset = scale
            renderText(font, text, x + shadowOffset, y + shadowOffset, shadowColor)
            width = renderText(font, text, x, y, color)
        } else {
            width = renderText(font, text, x, y, color)
        }

        if (!wasBuilding) end()
        return width
    }

    /**
     * Renders text using Tesselator and the POSITION_TEX_COLOR shader.
     */
    private fun renderText(
        font: Font,
        text: String,
        startX: Double,
        startY: Double,
        color: TextColor,
    ): Double {
        if (text.isEmpty()) return startX

        val renderScale = scale
        var xPos = startX
        val yPos = startY + font.getAscent() * renderScale

        // Setup render state
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR)
        RenderSystem.setShaderTexture(0, font.textureId)

        val tesselator = Tesselator.getInstance()
        val buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)

        val r = color.r / 255f
        val g = color.g / 255f
        val b = color.b / 255f
        val a = color.a / 255f

        for (i in text.indices) {
            val cp = text[i].code
            val c = font.getChar(cp) ?: continue

            val x0 = (xPos + c.x0 * renderScale).toFloat()
            val y0 = (yPos + c.y0 * renderScale).toFloat()
            val x1 = (xPos + c.x1 * renderScale).toFloat()
            val y1 = (yPos + c.y1 * renderScale).toFloat()

            // Emit quad (counter-clockwise)
            buffer.addVertex(x0, y0, 0f).setUv(c.u0, c.v0).setColor(r, g, b, a)
            buffer.addVertex(x0, y1, 0f).setUv(c.u0, c.v1).setColor(r, g, b, a)
            buffer.addVertex(x1, y1, 0f).setUv(c.u1, c.v1).setColor(r, g, b, a)
            buffer.addVertex(x1, y0, 0f).setUv(c.u1, c.v0).setColor(r, g, b, a)

            xPos += c.xAdvance * renderScale
        }

        val meshData = buffer.build()
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData)
        }

        RenderSystem.disableBlend()

        return xPos
    }

    fun end() {
        check(building) { "TextRenderer.end() called while not building" }

        building = false
        scale = 1.0
    }

    fun destroy() {
        fonts?.forEach { it.close() }
        fonts = null
        currentFont = null
        fontBuffer?.let { MemoryUtil.memFree(it) }
        fontBuffer = null
    }
}
