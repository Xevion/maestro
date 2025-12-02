package maestro.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CachedRegionTest {
    @Test
    fun blockPosSaving() {
        for (x in 0..15) {
            for (z in 0..15) {
                for (y in 0..255) {
                    val part1 = (z shl 4 or x).toByte()
                    val part2 = (y).toByte()
                    val decodedX = part1.toInt() and 0x0f
                    val decodedZ = (part1.toInt() ushr 4) and 0x0f
                    val decodedY = part2.toInt() and 0xff
                    if (x != decodedX || y != decodedY || z != decodedZ) {
                        println("$x $decodedX $y $decodedY $z $decodedZ")
                    }
                    assertEquals(x, decodedX)
                    assertEquals(y, decodedY)
                    assertEquals(z, decodedZ)
                }
            }
        }
    }
}
