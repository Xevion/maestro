package maestro.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import maestro.api.cache.Waypoint
import maestro.api.utils.Loggers
import maestro.api.utils.PackedBlockPos
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

private val log = Loggers.get("cache")

/** Waypoints for a world */
class WaypointCollection internal constructor(
    private val directory: Path,
) {
    /** Magic value to detect invalid waypoint files */
    private val waypoints: MutableMap<Waypoint.Tag, MutableSet<Waypoint>> = mutableMapOf()
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
        for (tag in Waypoint.Tag.entries) {
            load(tag)
        }
    }

    private fun load(tag: Waypoint.Tag) {
        // Use runBlocking with mutex for Java interop
        kotlinx.coroutines.runBlocking {
            saveMutex.withLock {
                loadInternal(tag)
            }
        }
    }

    private fun loadInternal(tag: Waypoint.Tag) {
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
                                    PackedBlockPos(x, y, z),
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

    private fun save(tag: Waypoint.Tag) {
        // Use runBlocking with mutex for Java interop
        kotlinx.coroutines.runBlocking {
            saveMutex.withLock {
                saveInternal(tag)
            }
        }
    }

    private fun saveInternal(tag: Waypoint.Tag) {
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

    fun addWaypoint(waypoint: Waypoint) {
        // no need to check for duplicate, because it's a Set not a List
        if (waypoints[waypoint.getTag()]?.add(waypoint) == true) {
            save(waypoint.getTag())
        }
    }

    fun removeWaypoint(waypoint: Waypoint) {
        if (waypoints[waypoint.getTag()]?.remove(waypoint) == true) {
            save(waypoint.getTag())
        }
    }

    fun getMostRecentByTag(tag: Waypoint.Tag): Waypoint? =
        // Find a waypoint of the given tag which has the greatest timestamp value, indicating the
        // most recent
        waypoints[tag]
            ?.minByOrNull { -it.getCreationTimestamp() }

    fun getByTag(tag: Waypoint.Tag): Set<Waypoint> = waypoints[tag]?.toSet() ?: emptySet()

    fun getAllWaypoints(): Set<Waypoint> = waypoints.values.flatten().toSet()

    companion object {
        private const val WAYPOINT_MAGIC_VALUE = 121977993584L // good value
    }
}
