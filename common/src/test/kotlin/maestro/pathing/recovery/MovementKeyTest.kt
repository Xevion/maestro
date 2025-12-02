package maestro.pathing.recovery

import maestro.api.utils.pack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MovementKeyTest {
    @Test
    fun `equals returns true for same source and destination`() {
        val pos1 = pack(100, 64, 200)
        val pos2 = pack(150, 65, 250)

        val key1 = MovementKey(pos1, pos2)
        val key2 = MovementKey(pos1, pos2)

        assertEquals(key1, key2)
    }

    @Test
    fun `equals returns false for different source`() {
        val pos1 = pack(100, 64, 200)
        val pos2 = pack(101, 64, 200)
        val dest = pack(150, 65, 250)

        val key1 = MovementKey(pos1, dest)
        val key2 = MovementKey(pos2, dest)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `equals returns false for different destination`() {
        val src = pack(100, 64, 200)
        val dest1 = pack(150, 65, 250)
        val dest2 = pack(151, 65, 250)

        val key1 = MovementKey(src, dest1)
        val key2 = MovementKey(src, dest2)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `hashCode is consistent`() {
        val pos1 = pack(100, 64, 200)
        val pos2 = pack(150, 65, 250)

        val key1 = MovementKey(pos1, pos2)
        val key2 = MovementKey(pos1, pos2)

        assertEquals(key1.hashCode(), key2.hashCode())
    }

    @Test
    fun `toString contains arrow`() {
        val pos1 = pack(100, 64, 200)
        val pos2 = pack(150, 65, 250)

        val key = MovementKey(pos1, pos2)

        assertTrue(key.toString().contains("→"))
    }
}
