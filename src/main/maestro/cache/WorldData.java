package maestro.cache;

import java.nio.file.Path;
import maestro.Agent;
import maestro.api.cache.ICachedWorld;
import maestro.api.cache.IWaypointCollection;
import maestro.api.cache.IWorldData;
import maestro.api.utils.MaestroLogger;
import net.minecraft.world.level.dimension.DimensionType;
import org.slf4j.Logger;

/**
 * Data about a world, from maestro's point of view. Includes cached chunks, waypoints, and map
 * data.
 */
public class WorldData implements IWorldData {

    private static final Logger log = MaestroLogger.get("cache");

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
        Agent.getExecutor()
                .execute(
                        () -> {
                            log.atInfo().log("World save started");
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
