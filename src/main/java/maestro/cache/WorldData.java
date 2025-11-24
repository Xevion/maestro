package maestro.cache;

import java.nio.file.Path;
import maestro.Maestro;
import maestro.api.cache.ICachedWorld;
import maestro.api.cache.IWaypointCollection;
import maestro.api.cache.IWorldData;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Data about a world, from maestro's point of view. Includes cached chunks, waypoints, and map
 * data.
 *
 * @author leijurv
 */
public class WorldData implements IWorldData {

    public final CachedWorld cache;
    private final WaypointCollection waypoints;
    // public final MapData map;
    public final Path directory;
    public final DimensionType dimension;

    WorldData(Path directory, DimensionType dimension) {
        this.directory = directory;
        this.cache = new CachedWorld(directory.resolve("cache"), dimension);
        this.waypoints = new WaypointCollection(directory.resolve("waypoints"));
        this.dimension = dimension;
    }

    public void onClose() {
        Maestro.getExecutor()
                .execute(
                        () -> {
                            System.out.println("Started saving the world in a new thread");
                            cache.save();
                        });
    }

    @Override
    public ICachedWorld getCachedWorld() {
        return this.cache;
    }

    @Override
    public IWaypointCollection getWaypoints() {
        return this.waypoints;
    }
}
