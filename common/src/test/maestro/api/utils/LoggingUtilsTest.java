package maestro.api.utils;

import static maestro.api.utils.LoggingUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

public class LoggingUtilsTest {

    @Test
    public void formatPos_BlockPos_formatsCorrectly() {
        assertEquals("100,64,200", formatPos(new BlockPos(100, 64, 200)));
        assertEquals("0,0,0", formatPos(BlockPos.ZERO));
        assertEquals("-50,128,-75", formatPos(new BlockPos(-50, 128, -75)));
    }

    @Test
    public void formatPos_PackedBlockPos_formatsCorrectly() {
        assertEquals("100,64,200", formatPos(new PackedBlockPos(100, 64, 200)));
        assertEquals("0,0,0", formatPos(new PackedBlockPos(0, 0, 0)));
        assertEquals("-50,128,-75", formatPos(new PackedBlockPos(-50, 128, -75)));
    }

    @Test
    public void formatCoords_formatsCorrectly() {
        assertEquals("100,64,200", formatCoords(100, 64, 200));
        assertEquals("0,0,0", formatCoords(0, 0, 0));
        assertEquals("-50,128,-75", formatCoords(-50, 128, -75));
    }

    @Test
    public void formatFloat_roundsToThreeDecimals() {
        assertEquals("6.635", formatFloat(6.634636));
        assertEquals("4.123", formatFloat(4.123456));
        assertEquals("123.457", formatFloat(123.456789));
        assertEquals("0.000", formatFloat(0.000123));
        assertEquals("1.000", formatFloat(1.0));
        assertEquals("-2.500", formatFloat(-2.5));
    }
}
