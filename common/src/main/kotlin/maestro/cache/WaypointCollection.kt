package maestro.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maestro.api.cache.IWaypoint
import maestro.api.cache.IWaypointCollection
import maestro.api.cache.Waypoint
import maestro.api.utils.BetterBlockPos
import maestro.api.utils.MaestroLogger
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

private val log = MaestroLogger.get("cache")

/** Waypoints for a world */
class WaypointCollection internal constructor(
    private val directory: Path,
) : IWaypointCollection {
    /** Magic value to detect invalid waypoint files */
    private val waypoints: MutableMap<IWaypoint.Tag, MutableSet<IWaypoint>> = mutableMapOf()
    private val saveMutex = Mutex()

    init {
        if (!directory.exists()) {
            try {
                directory.createDirectories()
            } catch (ignored: IOException) {
            }
        }
        log.atDebug().addKeyValue("directory", directory).log("Waypoint directory initialized")
        load()
    }

    private fun load() {
        for (tag in IWaypoint.Tag.entries) {
            load(tag)
        }
    }

    private fun load(tag: IWaypoint.Tag) {
        // Use runBlocking with mutex for Java interop
        kotlinx.coroutines.runBlocking {
            saveMutex.withLock {
                loadInternal(tag)
            }
        }
    }

    private fun loadInternal(tag: IWaypoint.Tag) {
        waypoints[tag] = mutableSetOf()

        val fileName = directory.resolve(tag.name.lowercase() + ".mp4")
        if (!fileName.exists()) {
            return
        }

        try {
            FileInputStream(File(fileName.toString())).use { fileIn ->
                BufferedInputStream(fileIn).use { bufIn ->
                    DataInputStream(bufIn).use { input ->
                        val magic = input.readLong()
                        if (magic != WAYPOINT_MAGIC_VALUE) {
                            throw IOException("Bad magic value $magic")
                        }

                        var length =
                            input.readLong() // Yes I want 9,223,372,036,854,775,807 waypoints, do you not?

                        while (length-- > 0) {
                            val name = input.readUTF()
                            val creationTimestamp = input.readLong()
                            val x = input.readInt()
                            val y = input.readInt()
                            val z = input.readInt()

                            waypoints[tag]?.add(
                                Waypoint(
                                    name,
                                    tag,
                                    BetterBlockPos(x, y, z),
                                    creationTimestamp,
                                ),
                            )
                        }
                    }
                }
            }
        } catch (ignored: IOException) {
        }
    }

    private fun save(tag: IWaypoint.Tag) {
        // Use runBlocking with mutex for Java interop
        kotlinx.coroutines.runBlocking {
            saveMutex.withLock {
                saveInternal(tag)
            }
        }
    }

    private fun saveInternal(tag: IWaypoint.Tag) {
        val fileName = directory.resolve(tag.name.lowercase() + ".mp4")

        try {
            FileOutputStream(File(fileName.toString())).use { fileOut ->
                BufferedOutputStream(fileOut).use { bufOut ->
                    DataOutputStream(bufOut).use { out ->
                        out.writeLong(WAYPOINT_MAGIC_VALUE)
                        out.writeLong(waypoints[tag]?.size?.toLong() ?: 0L)

                        for (waypoint in waypoints[tag] ?: emptySet()) {
                            out.writeUTF(waypoint.getName())
                            out.writeLong(waypoint.getCreationTimestamp())
                            out.writeInt(waypoint.getLocation().x)
                            out.writeInt(waypoint.getLocation().y)
                            out.writeInt(waypoint.getLocation().z)
                        }
                    }
                }
            }
        } catch (ex: IOException) {
            log
                .atError()
                .setCause(ex)
                .addKeyValue("tag", tag.name)
                .log("Failed to save waypoints")
        }
    }

    override fun addWaypoint(waypoint: IWaypoint) {
        // no need to check for duplicate, because it's a Set not a List
        if (waypoints[waypoint.getTag()]?.add(waypoint) == true) {
            save(waypoint.getTag())
        }
    }

    override fun removeWaypoint(waypoint: IWaypoint) {
        if (waypoints[waypoint.getTag()]?.remove(waypoint) == true) {
            save(waypoint.getTag())
        }
    }

    override fun getMostRecentByTag(tag: IWaypoint.Tag): IWaypoint? =
        // Find a waypoint of the given tag which has the greatest timestamp value, indicating the
        // most recent
        waypoints[tag]
            ?.minByOrNull { -it.getCreationTimestamp() }

    override fun getByTag(tag: IWaypoint.Tag): Set<IWaypoint> = waypoints[tag]?.toSet() ?: emptySet()

    override fun getAllWaypoints(): Set<IWaypoint> = waypoints.values.flatten().toSet()

    companion object {
        private const val WAYPOINT_MAGIC_VALUE = 121977993584L // good value
    }
}
