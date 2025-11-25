package maestro.api.process;

import java.util.List;
import java.util.function.Predicate;
import maestro.api.combat.TrajectoryResult;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Process for ranged combat using bows with ballistic trajectory calculations. Handles target
 * selection, positioning, aiming, charging, and shooting.
 */
public interface IRangedCombatProcess extends IMaestroProcess {

    /**
     * Start shooting at entities matching the filter.
     *
     * @param filter Predicate to select target entities
     */
    void shoot(Predicate<Entity> filter);

    /**
     * Get the current target entity being engaged.
     *
     * @return Current target, or null if no target selected
     */
    @Nullable
    Entity getCurrentTarget();

    /**
     * Get all valid targets matching the current filter.
     *
     * @return List of target entities
     */
    List<Entity> getTargets();

    /**
     * Get the currently calculated trajectory for rendering/debugging.
     *
     * @return Current trajectory result, or null if none calculated
     */
    @Nullable
    TrajectoryResult getCurrentTrajectory();

    /** Stop shooting and clear all targets. Cancels charging if in progress. */
    default void cancel() {
        onLostControl();
    }
}
