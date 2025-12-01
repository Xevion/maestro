package maestro.api.utils

import net.minecraft.core.BlockPos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for PackedBlockPos, particularly the equals() override that handles
 * cross-type comparisons with BlockPos.
 *
 * This addresses the issue where PackedBlockPos.equals(BlockPos) and
 * BlockPos.equals(PackedBlockPos) were both returning false, causing
 * movement execution bugs.
 */
class PackedBlockPosTest {
    @Test
    fun testPackedBlockPosEqualsItself() {
        val pos = PackedBlockPos(100, 64, 200)
        assertEquals(pos, pos)
        assertTrue(pos == pos)
    }

    @Test
    fun testPackedBlockPosEqualsIdenticalPackedBlockPos() {
        val pos1 = PackedBlockPos(100, 64, 200)
        val pos2 = PackedBlockPos(100, 64, 200)
        assertEquals(pos1, pos2)
        assertEquals(pos2, pos1)
        assertTrue(pos1 == pos2)
        assertTrue(pos2 == pos1)
    }

    @Test
    fun testPackedBlockPosNotEqualsDifferentPackedBlockPos() {
        val pos1 = PackedBlockPos(100, 64, 200)
        val pos2 = PackedBlockPos(101, 64, 200)
        assertNotEquals(pos1, pos2)
        assertNotEquals(pos2, pos1)
        assertFalse(pos1 == pos2)
        assertFalse(pos2 == pos1)
    }

    /**
     * CRITICAL TEST: PackedBlockPos.equals(BlockPos) must work
     * This is the direction fixed by the equals override.
     */
    @Test
    fun testPackedBlockPosEqualsBlockPos() {
        val packed = PackedBlockPos(100, 64, 200)
        val blockPos = BlockPos(100, 64, 200)

        // PackedBlockPos.equals(BlockPos) should work with our override
        assertTrue("PackedBlockPos.equals(BlockPos) should return true", packed == blockPos)
        assertEquals("PackedBlockPos.equals(BlockPos) should return true", packed, blockPos)
    }

    /**
     * KNOWN LIMITATION: BlockPos.equals(PackedBlockPos) returns false
     * We can't fix this without modifying Minecraft's BlockPos class.
     * The manual .toBlockPos() conversions in movement code handle this.
     */
    @Test
    fun testBlockPosDoesNotEqualPackedBlockPos() {
        val blockPos = BlockPos(100, 64, 200)
        val packed = PackedBlockPos(100, 64, 200)

        // BlockPos.equals(PackedBlockPos) always returns false (Minecraft limitation)
        assertFalse("BlockPos.equals(PackedBlockPos) returns false (known limitation)", blockPos == packed)
        assertNotEquals("BlockPos.equals(PackedBlockPos) returns false (known limitation)", blockPos, packed)
    }

    @Test
    fun testPackedBlockPosNotEqualsNull() {
        val pos = PackedBlockPos(100, 64, 200)
        assertNotEquals(pos, null)
        assertFalse(pos == null)
    }

    @Test
    fun testPackedBlockPosNotEqualsOtherType() {
        val pos = PackedBlockPos(100, 64, 200)
        assertNotEquals(pos, "not a position")
        assertNotEquals(pos, 12345)
        assertFalse(pos.equals("not a position"))
        assertFalse(pos.equals(12345))
    }

    @Test
    fun testPackedBlockPosHashCode() {
        val pos1 = PackedBlockPos(100, 64, 200)
        val pos2 = PackedBlockPos(100, 64, 200)
        val pos3 = PackedBlockPos(101, 64, 200)

        // Equal objects must have equal hash codes
        assertEquals(pos1.hashCode(), pos2.hashCode())

        // Unequal objects should (usually) have different hash codes
        // Note: hash collisions are possible, so we just document the expectation
        assertNotEquals(pos1.hashCode(), pos3.hashCode())
    }

    @Test
    fun testPackedBlockPosFromBlockPos() {
        val blockPos = BlockPos(100, 64, 200)
        val packed = PackedBlockPos(blockPos)

        assertEquals(100, packed.x)
        assertEquals(64, packed.y)
        assertEquals(200, packed.z)
        assertEquals(packed, blockPos)
    }

    @Test
    fun testPackedBlockPosToBlockPos() {
        val packed = PackedBlockPos(100, 64, 200)
        val blockPos = packed.toBlockPos()

        assertEquals(100, blockPos.x)
        assertEquals(64, blockPos.y)
        assertEquals(200, blockPos.z)
    }

    @Test
    fun testPackedBlockPosRoundTrip() {
        val original = BlockPos(100, 64, 200)
        val packed = PackedBlockPos(original)
        val converted = packed.toBlockPos()

        assertEquals(original.x, converted.x)
        assertEquals(original.y, converted.y)
        assertEquals(original.z, converted.z)
    }

    @Test
    fun testNegativeCoordinates() {
        val packed = PackedBlockPos(-100, -64, -200)
        val blockPos = BlockPos(-100, -64, -200)

        assertEquals(-100, packed.x)
        assertEquals(-64, packed.y)
        assertEquals(-200, packed.z)
        assertTrue(packed == blockPos)
    }

    @Test
    fun testOriginConstant() {
        val origin = PackedBlockPos.ORIGIN
        assertEquals(0, origin.x)
        assertEquals(0, origin.y)
        assertEquals(0, origin.z)
        assertEquals(origin, BlockPos(0, 0, 0))
    }
}
