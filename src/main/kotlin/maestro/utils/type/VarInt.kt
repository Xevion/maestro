package maestro.utils.type

/**
 * Variable-length integer encoding following the VarInt protocol.
 *
 * VarInt encodes integers using 1-5 bytes, where the most significant bit
 * indicates if another byte follows.
 */
class VarInt(
    val value: Int,
) {
    val serialized: ByteArray = serialize0(value)
    val size: Int = serialized.size

    companion object {
        /**
         * Reads a VarInt from a byte array starting at the given offset.
         *
         * @param bytes The byte array to read from
         * @param start The offset to start reading from (default: 0)
         * @return The decoded VarInt
         * @throws IllegalArgumentException if VarInt size exceeds 5 bytes
         */
        @JvmStatic
        @JvmOverloads
        fun read(
            bytes: ByteArray,
            start: Int = 0,
        ): VarInt {
            var value = 0
            var size = 0
            var index = start

            while (true) {
                val b = bytes[index++]
                value = value or ((b.toInt() and 0x7F) shl (size++ * 7))

                require(size <= 5) { "VarInt size cannot exceed 5 bytes" }

                // Most significant bit denotes another byte is to be read.
                if ((b.toInt() and 0x80) == 0) {
                    break
                }
            }

            return VarInt(value)
        }

        private fun serialize0(valueIn: Int): ByteArray {
            val bytes = mutableListOf<Byte>()
            var value = valueIn

            while ((value and 0x80) != 0) {
                bytes.add(((value and 0x7F) or 0x80).toByte())
                value = value ushr 7 // Unsigned right shift
            }
            bytes.add((value and 0xFF).toByte())

            return bytes.toByteArray()
        }
    }
}
