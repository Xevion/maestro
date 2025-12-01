package maestro.api.event.events;

import java.util.function.BiFunction;
import maestro.api.event.events.type.EventState;
import net.minecraft.client.Minecraft;

/**
 * Called on and after each game tick of the primary {@link Minecraft} instance and dispatched to
 * all Maestro instances.
 *
 * <p>When {@link #state} is {@link EventState#PRE}, the event is being called just prior to when
 * the current in-game screen is ticked. When {@link #state} is {@link EventState#POST}, the event
 * is being called at the very end of the {@link Minecraft#runTick(boolean)} method.
 */
public record TickEvent(EventState state, Type type, int count) {

    private static int overallTickCount;

    public static synchronized BiFunction<EventState, Type, TickEvent> createNextProvider() {
        final int count = overallTickCount;
        overallTickCount++;
        return (state, type) -> new TickEvent(state, type, count);
    }

    public enum Type {
        /** When guarantees can be made about the game state and in-game variables. */
        IN,
        /**
         * No guarantees can be made about the game state. This probably means we are at the main
         * menu.
         */
        OUT,
    }
}
