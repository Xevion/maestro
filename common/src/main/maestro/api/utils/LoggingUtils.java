package maestro.api.utils;

import maestro.api.pathing.goals.Goal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Utilities for compact, readable logging output.
 *
 * <p>Provides formatting methods for common types to reduce log verbosity while maintaining
 * clarity.
 */
public final class LoggingUtils {

    private LoggingUtils() {}

    /**
     * Format BlockPos as compact coordinate string.
     *
     * @param pos Block position to format
     * @return Formatted string: "x,y,z" (e.g., "100,64,200")
     */
    public static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /**
     * Format PackedBlockPos as compact coordinate string.
     *
     * @param pos Packed block position to format
     * @return Formatted string: "x,y,z" (e.g., "100,64,200")
     */
    public static String formatPos(PackedBlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /**
     * Format Vec3 as compact coordinate string with 3 decimal precision.
     *
     * @param pos Vector position to format
     * @return Formatted string: "x,y,z" (e.g., "100.500,64.000,200.250")
     */
    public static String formatPos(Vec3 pos) {
        return formatFloat(pos.x) + "," + formatFloat(pos.y) + "," + formatFloat(pos.z);
    }

    /**
     * Format individual coordinates as compact string.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Formatted string: "x,y,z" (e.g., "100,64,200")
     */
    public static String formatCoords(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /**
     * Format floating point number with 3 decimal precision.
     *
     * <p>Use for critical user-facing values (distances, speeds, percentages). For debug-level
     * internal calculations, prefer raw values to avoid formatting overhead.
     *
     * <p>Examples: 6.634636 → "6.635", 0.000123 → "0.000", 123.456789 → "123.457"
     *
     * @param value Value to format
     * @return Formatted string with 3 decimal places
     */
    public static String formatFloat(double value) {
        return String.format("%.3f", value);
    }

    /**
     * Format Goal for logging with smart truncation.
     *
     * <p>Goal types already implement toString() for compact output, so this method primarily
     * serves as a documentation point and future extension hook.
     *
     * @param goal Goal to format
     * @return Goal's toString() output
     */
    public static String formatGoal(Goal goal) {
        return goal.toString();
    }
}
