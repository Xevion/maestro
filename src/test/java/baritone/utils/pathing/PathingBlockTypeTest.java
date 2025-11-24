package baritone.utils.pathing;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PathingBlockTypeTest {

    @Test
    public void testBits() {
        for (PathingBlockType type : PathingBlockType.values()) {
            boolean[] bits = type.getBits();
            assertTrue(type == PathingBlockType.fromBits(bits[0], bits[1]));
        }
    }
}
