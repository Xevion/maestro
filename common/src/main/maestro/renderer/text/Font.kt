package maestro.renderer.text

import com.mojang.blaze3d.platform.NativeImage
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import maestro.api.utils.Loggers
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTTPackContext
import org.lwjgl.stb.STBTTPackRange
import org.lwjgl.stb.STBTTPackedchar
import org.lwjgl.stb.STBTruetype
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.slf4j.Logger
import java.nio.ByteBuffer

/**
 * A single font at a specific size with its texture atlas.
 *
 * Uses STB TrueType to rasterize glyphs into a texture atlas.
 * Uses DynamicTexture for GPU upload (1.21.4 compatible).
 */
class Font(
    fontBuffer: ByteBuffer,
    val height: Int,
) {
    val textureId: ResourceLocation
    private val dynamicTexture: DynamicTexture
    private val scale: Float
    private val ascent: Float
    private val charMap = Int2ObjectOpenHashMap<CharData>()

    companion object {
        private val log: Logger = Loggers.Text.get()
        const val ATLAS_SIZE: Int = 2048
        const val SPACE_CODEPOINT: Int = 32
    }

    init {
        log
            .atInfo()
            .addKeyValue("height", height)
            .addKeyValue("atlas_size", ATLAS_SIZE)
            .log("Creating font atlas")

        val fontInfo = STBTTFontinfo.create()
        val bitmap = MemoryUtil.memAlloc(ATLAS_SIZE * ATLAS_SIZE)
        val cdata =
            arrayOf(
                STBTTPackedchar.create(95), // Basic Latin (U+0020-U+007E)
                STBTTPackedchar.create(96), // Latin-1 Supplement (U+00A0-U+00FF)
                STBTTPackedchar.create(128), // Latin Extended-A (U+0100-U+017F)
            )
        val packContext = STBTTPackContext.create()
        val packRange = STBTTPackRange.create(cdata.size)

        try {
            if (!STBTruetype.stbtt_InitFont(fontInfo, fontBuffer)) {
                log.atError().addKeyValue("height", height).log("Failed to initialize STB font info")
                throw RuntimeException("Failed to initialize STB font info for height $height")
            }

            if (!STBTruetype.stbtt_PackBegin(packContext, bitmap, ATLAS_SIZE, ATLAS_SIZE, 0, 1)) {
                log.atError().addKeyValue("height", height).log("Failed to begin font packing")
                throw RuntimeException("Failed to begin font packing for height $height")
            }

            packRange.put(STBTTPackRange.create().set(height.toFloat(), 32, null, 95, cdata[0], 2.toByte(), 2.toByte()))
            packRange.put(STBTTPackRange.create().set(height.toFloat(), 160, null, 96, cdata[1], 2.toByte(), 2.toByte()))
            packRange.put(STBTTPackRange.create().set(height.toFloat(), 256, null, 128, cdata[2], 2.toByte(), 2.toByte()))
            packRange.flip()

            if (!STBTruetype.stbtt_PackFontRanges(packContext, fontBuffer, 0, packRange)) {
                log.atError().addKeyValue("height", height).log("Failed to pack font ranges")
                throw RuntimeException("Failed to pack font ranges for height $height")
            }
            STBTruetype.stbtt_PackEnd(packContext)

            log.atDebug().addKeyValue("height", height).log("Font packing complete")

            // Create NativeImage with RGBA format
            // We store glyph alpha in the alpha channel with white RGB so that
            // CoreShaders.POSITION_TEX_COLOR properly tints the text.
            val nativeImage = NativeImage(NativeImage.Format.RGBA, ATLAS_SIZE, ATLAS_SIZE, false)

            // Convert single-channel bitmap to RGBA: white RGB + luminance as alpha
            bitmap.rewind()
            for (y in 0 until ATLAS_SIZE) {
                for (x in 0 until ATLAS_SIZE) {
                    val alpha = bitmap.get().toInt() and 0xFF
                    // NativeImage.setPixelRGBA uses ABGR format internally
                    val abgr = (alpha shl 24) or 0x00FFFFFF // White RGB + alpha from bitmap
                    nativeImage.setPixel(x, y, abgr)
                }
            }

            log.atDebug().addKeyValue("height", height).log("Texture data copied to NativeImage")

            // Create DynamicTexture and register with TextureManager
            dynamicTexture = DynamicTexture(nativeImage)
            // Use nearest-neighbor filtering to keep text crisp (no blur when scaling)
            dynamicTexture.setFilter(false, false)
            textureId = ResourceLocation.fromNamespaceAndPath("maestro", "font_atlas_$height")
            Minecraft.getInstance().textureManager.register(textureId, dynamicTexture)

            log
                .atInfo()
                .addKeyValue("height", height)
                .addKeyValue("texture_id", textureId.toString())
                .log("Font texture registered")

            scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, height.toFloat())

            MemoryStack.stackPush().use { stack ->
                val ascentBuf = stack.mallocInt(1)
                STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascentBuf, null, null)
                ascent = ascentBuf.get(0).toFloat()
            }

            for (i in cdata.indices) {
                val cbuf = cdata[i]
                val offset = packRange.get(i).first_unicode_codepoint_in_range()

                for (j in 0 until cbuf.capacity()) {
                    val packedChar = cbuf.get(j)
                    val ipw = 1f / ATLAS_SIZE
                    val iph = 1f / ATLAS_SIZE

                    charMap.put(
                        j + offset,
                        CharData(
                            x0 = packedChar.xoff(),
                            y0 = packedChar.yoff(),
                            x1 = packedChar.xoff2(),
                            y1 = packedChar.yoff2(),
                            u0 = packedChar.x0() * ipw,
                            v0 = packedChar.y0() * iph,
                            u1 = packedChar.x1() * ipw,
                            v1 = packedChar.y1() * iph,
                            xAdvance = packedChar.xadvance(),
                        ),
                    )
                }
            }

            log
                .atInfo()
                .addKeyValue("height", height)
                .addKeyValue("glyphs", charMap.size)
                .log("Font atlas created")
        } finally {
            // Free native STB structures (not garbage collected)
            fontInfo.free()
            packContext.free()
            packRange.free()
            cdata.forEach { it.free() }
            MemoryUtil.memFree(bitmap)
        }
    }

    /**
     * Gets character data for a codepoint, falling back to space if not found.
     * Returns null only if space glyph is also missing (should never happen).
     */
    fun getChar(codePoint: Int): CharData? = charMap.get(codePoint) ?: charMap.get(SPACE_CODEPOINT)

    fun getWidth(
        text: String,
        length: Int = text.length,
    ): Double {
        val spaceChar = charMap.get(SPACE_CODEPOINT)
        var width = 0.0
        for (i in 0 until length) {
            val cp = text[i].code
            val c = charMap.get(cp) ?: spaceChar ?: continue
            width += c.xAdvance
        }
        return width
    }

    fun getAscent(): Float = ascent * scale

    fun close() {
        dynamicTexture.close()
    }
}
