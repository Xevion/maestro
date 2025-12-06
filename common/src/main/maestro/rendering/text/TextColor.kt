package maestro.renderer.text

/**
 * Color representation for text rendering.
 *
 * Stores RGBA components as 0-255 integers.
 */
data class TextColor(
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Int,
) {
    companion object {
        val WHITE: TextColor = TextColor(255, 255, 255, 255)
        val BLACK: TextColor = TextColor(0, 0, 0, 255)
        val SHADOW: TextColor = TextColor(60, 60, 60, 180)
        const val SHADOW_ALPHA_FACTOR: Double = 0.7

        /**
         * Creates a TextColor from an ARGB packed integer (Minecraft's format).
         */
        fun fromARGB(argb: Int): TextColor =
            TextColor(
                r = (argb shr 16) and 0xFF,
                g = (argb shr 8) and 0xFF,
                b = argb and 0xFF,
                a = (argb shr 24) and 0xFF,
            )

        /**
         * Creates a TextColor from an RGBA packed integer.
         */
        fun fromRGBA(rgba: Int): TextColor =
            TextColor(
                r = (rgba shr 24) and 0xFF,
                g = (rgba shr 16) and 0xFF,
                b = (rgba shr 8) and 0xFF,
                a = rgba and 0xFF,
            )
    }

    /**
     * Converts to ARGB packed integer (Minecraft's format).
     */
    fun toARGB(): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

    /**
     * Creates a shadow color from this color.
     * Shadow uses the standard shadow RGB with reduced alpha (70% of original).
     */
    fun toShadow(): TextColor =
        TextColor(
            r = SHADOW.r,
            g = SHADOW.g,
            b = SHADOW.b,
            a = (a * SHADOW_ALPHA_FACTOR).toInt().coerceIn(0, 255),
        )

    /**
     * Creates a copy with adjusted alpha.
     */
    fun withAlpha(newAlpha: Int): TextColor = copy(a = newAlpha.coerceIn(0, 255))

    /**
     * Creates a copy with alpha multiplied by the given factor.
     */
    fun withAlphaMultiplied(factor: Double): TextColor = copy(a = (a * factor).toInt().coerceIn(0, 255))
}
