package maestro.api.process;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;

public interface IAttackProcess extends IMaestroProcess {

    /**
     * Begin attacking entities matching the specified predicate. The bot will path to targets and
     * attack them until no matching entities remain in range.
     *
     * @param filter the predicate to match entities
     */
    void attack(Predicate<Entity> filter);

    /**
     * @return The entities that are currently being targeted. null if not currently attacking,
     *     empty if nothing matches the predicate
     */
    List<Entity> getTargets();

    /** Cancels the attack behavior and clears the current target filter. */
    default void cancel() {
        onLostControl();
    }
}
