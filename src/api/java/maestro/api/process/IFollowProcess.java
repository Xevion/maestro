package maestro.api.process;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public interface IFollowProcess extends IMaestroProcess {

    /**
     * Set the follow target to any entities matching this predicate
     *
     * @param filter the predicate
     */
    void follow(Predicate<Entity> filter);

    /**
     * Try to pick up any items matching this predicate
     *
     * @param filter the predicate
     */
    void pickup(Predicate<ItemStack> filter);

    /**
     * @return The entities that are currently being followed. null if not currently following,
     *     empty if nothing matches the predicate
     */
    List<Entity> following();

    Predicate<Entity> currentFilter();

    /** Cancels the follow behavior, this will clear the current follow target. */
    default void cancel() {
        onLostControl();
    }
}
