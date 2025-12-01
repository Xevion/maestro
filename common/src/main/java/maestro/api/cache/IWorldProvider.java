package maestro.api.cache;

import java.util.function.Consumer;

public interface IWorldProvider {

    /**
     * Returns the data of the currently loaded world
     *
     * @return The current world data
     */
    IWorldData getCurrentWorld();

    default void ifWorldLoaded(Consumer<IWorldData> callback) {
        final IWorldData currentWorld = this.getCurrentWorld();
        if (currentWorld != null) {
            callback.accept(currentWorld);
        }
    }
}
