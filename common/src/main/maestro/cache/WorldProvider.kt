package maestro.cache

import maestro.Agent
import maestro.api.player.PlayerContext
import maestro.cache.WorldData
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.LevelResource
import org.apache.commons.lang3.SystemUtils
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.function.Consumer
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class WorldProvider(
    private val maestro: Agent,
) {
    private val ctx: PlayerContext = maestro.playerContext

    /**
     * This lets us detect a broken load/unload hook.
     *
     * @see detectAndHandleBrokenLoading
     */
    private var mcWorld: Level? = null
    internal var currentWorld: WorldData? = null

    fun getCurrentWorld(): WorldData? {
        detectAndHandleBrokenLoading()
        return currentWorld
    }

    fun ifWorldLoaded(callback: Consumer<WorldData>) {
        val currentWorld = getCurrentWorld()
        if (currentWorld != null) {
            callback.accept(currentWorld)
        }
    }

    /**
     * Called when a new world is initialized to discover the save directory
     *
     * @param world The new world
     */
    fun initWorld(world: Level) {
        getSaveDirectories(world)?.let { (worldDir, readmeDir) ->
            try {
                readmeDir.createDirectories()
                readmeDir
                    .resolve("readme.txt")
                    .writeText(
                        "https://github.com/cabaletta/baritone\n",
                        StandardCharsets.US_ASCII,
                    )
            } catch (ignored: IOException) {
            }

            // We will actually store the world data in a subfolder: "DIM<id>"
            val worldDataDir = getWorldDataDirectory(worldDir, world)
            try {
                worldDataDir.createDirectories()
            } catch (ignored: IOException) {
            }

            synchronized(worldCache) {
                currentWorld =
                    worldCache.computeIfAbsent(worldDataDir) { d ->
                        WorldData(d, world.dimensionType())
                    }
            }
            mcWorld = ctx.world()
        }
    }

    fun closeWorld() {
        val world = currentWorld
        currentWorld = null
        mcWorld = null
        world?.onClose()
    }

    private fun getWorldDataDirectory(
        parent: Path,
        world: Level,
    ): Path {
        val dimId = world.dimension().location()
        val height = world.dimensionType().logicalHeight()
        return parent.resolve(dimId.namespace).resolve("${dimId.path}_$height")
    }

    /**
     * @param world The world
     * @return A pair containing the world's maestro dir and readme dir, or null if the world isn't
     *     valid for caching.
     */
    private fun getSaveDirectories(world: Level): Pair<Path, Path>? {
        val worldDir: Path
        val readmeDir: Path

        // If there is an integrated server running (Aka Singleplayer) then do magic to find the
        // world save file
        if (ctx.minecraft().hasSingleplayerServer()) {
            var dir = ctx.minecraft().singleplayerServer?.getWorldPath(LevelResource.ROOT) ?: return null

            // Gets the "depth" of this directory relative to the game's run directory, 2 is the
            // location of the world
            if (dir.relativize(ctx.minecraft().gameDirectory.toPath()).nameCount != 2) {
                // subdirectory of the main save directory for this world
                dir = dir.parent
            }

            worldDir = dir.resolve("maestro")
            readmeDir = worldDir
        } else { // Otherwise, the server must be remote...
            val serverData = ctx.minecraft().currentServer
            val folderName =
                when {
                    serverData != null -> if (serverData.isRealm) "realms" else serverData.ip
                    else -> {
                        // replaymod causes null currentServer and false singleplayer.
                        currentWorld = null
                        mcWorld = ctx.world()
                        return null
                    }
                }

            val sanitizedFolderName =
                if (SystemUtils.IS_OS_WINDOWS) {
                    folderName.replace(":", "_")
                } else {
                    folderName
                }

            // TODO: This should probably be in "maestro/servers"
            worldDir = maestro.directory.resolve(sanitizedFolderName)
            // Just write the readme to the maestro directory instead of each server save in it
            readmeDir = maestro.directory
        }

        return Pair(worldDir, readmeDir)
    }

    /** Why does this exist instead of fixing the event? Some mods break the event. */
    private fun detectAndHandleBrokenLoading() {
        if (mcWorld != ctx.world()) {
            if (currentWorld != null) {
                closeWorld()
            }
            if (ctx.world() != null) {
                initWorld(ctx.world()!!)
            }
        } else if (
            currentWorld == null &&
            ctx.world() != null &&
            (ctx.minecraft().hasSingleplayerServer() || ctx.minecraft().currentServer != null)
        ) {
            initWorld(ctx.world()!!)
        }
    }

    companion object {
        private val worldCache = mutableMapOf<Path, WorldData>()
    }
}
