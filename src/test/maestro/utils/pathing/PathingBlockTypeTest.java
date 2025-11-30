package maestro.utils.pathing;

import static org.junit.Assert.assertSame;

import org.junit.Test;

public class PathingBlockTypeTest {

    @Test
    public void testBits() {
        for (PathingBlockType type : PathingBlockType.getEntries()) {
            boolean[] bits = type.getBits();
            assertSame(type, PathingBlockType.fromBits(bits[0], bits[1]));
        }
    }
}
