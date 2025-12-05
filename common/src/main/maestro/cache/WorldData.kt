package maestro.cache

import maestro.Agent
import maestro.api.cache.IWorldData
import maestro.api.utils.Loggers
import maestro.cache.WorldData
import net.minecraft.world.level.dimension.DimensionType
import java.nio.file.Path

private val log = Loggers.Cache.get()

/**
 * Data about a world, from maestro's point of view. Includes cached chunks, waypoints, and map
 * data.
 */
class WorldData internal constructor(
    val directory: Path,
    val dimension: DimensionType,
) : IWorldData {
    val cache: CachedWorld = CachedWorld(directory.resolve("cache"), dimension)
    private val waypoints: WaypointCollection = WaypointCollection(directory.resolve("waypoints"))

    fun onClose() {
        Agent.getExecutor().execute {
            log.atInfo().log("World save started")
            cache.save()
        }
    }

    override fun getCachedWorld(): CachedWorld = cache

    override fun getWaypoints(): WaypointCollection = waypoints
}
