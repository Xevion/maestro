package maestro.rendering.text

/**
 * Glyph metadata for a single character.
 *
 * @property x0 Left offset from cursor position
 * @property y0 Top offset from baseline
 * @property x1 Right offset from cursor position
 * @property y1 Bottom offset from baseline
 * @property u0 Left UV coordinate in atlas
 * @property v0 Top UV coordinate in atlas
 * @property u1 Right UV coordinate in atlas
 * @property v1 Bottom UV coordinate in atlas
 * @property xAdvance Horizontal advance to next character
 */
data class CharData(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
    val xAdvance: Float,
)
