package maestro.api.pathing.movement;

import maestro.api.utils.PackedBlockPos;
import net.minecraft.core.BlockPos;

public interface IMovement {

    double getCost();

    MovementStatus update();

    /** Resets the current state status to {@link MovementStatus#PREPPING} */
    void reset();

    /** Resets the cache for special break, place, and walk into blocks */
    void resetBlockCache();

    /**
     * @return Whether it is safe to cancel the current movement state
     */
    boolean safeToCancel();

    boolean calculatedWhileLoaded();

    PackedBlockPos getSrc();

    PackedBlockPos getDest();

    BlockPos getDirection();

    /**
     * Get compact debug information for HUD display.
     *
     * @return compact debug string (e.g., "dist:0.52 drift:Y"), empty if no debug info
     */
    String getDebugInfo();
}
