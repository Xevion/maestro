package maestro.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import maestro.Maestro;
import maestro.api.cache.IWorldProvider;
import maestro.api.utils.IPlayerContext;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.commons.lang3.SystemUtils;

public class WorldProvider implements IWorldProvider {

    private static final Map<Path, WorldData> worldCache = new HashMap<>();

    private final Maestro maestro;
    private final IPlayerContext ctx;
    private WorldData currentWorld;

    /**
     * This lets us detect a broken load/unload hook.
     *
     * @see #detectAndHandleBrokenLoading()
     */
    private Level mcWorld;

    public WorldProvider(Maestro maestro) {
        this.maestro = maestro;
        this.ctx = maestro.getPlayerContext();
    }

    @Override
    public final WorldData getCurrentWorld() {
        this.detectAndHandleBrokenLoading();
        return this.currentWorld;
    }

    /**
     * Called when a new world is initialized to discover the
     *
     * @param world The new world
     */
    public final void initWorld(Level world) {
        this.getSaveDirectories(world)
                .ifPresent(
                        dirs -> {
                            final Path worldDir = dirs.getA();
                            final Path readmeDir = dirs.getB();

                            try {
                                // lol wtf is this baritone folder in my minecraft save?
                                // good thing we have a readme
                                Files.createDirectories(readmeDir);
                                Files.write(
                                        readmeDir.resolve("readme.txt"),
                                        "https://github.com/cabaletta/baritone\n"
                                                .getBytes(StandardCharsets.US_ASCII));
                            } catch (IOException ignored) {
                            }

                            // We will actually store the world data in a subfolder: "DIM<id>"
                            final Path worldDataDir = this.getWorldDataDirectory(worldDir, world);
                            try {
                                Files.createDirectories(worldDataDir);
                            } catch (IOException ignored) {
                            }

                            System.out.println("Maestro world data dir: " + worldDataDir);
                            synchronized (worldCache) {
                                this.currentWorld =
                                        worldCache.computeIfAbsent(
                                                worldDataDir,
                                                d -> new WorldData(d, world.dimensionType()));
                            }
                            this.mcWorld = ctx.world();
                        });
    }

    public final void closeWorld() {
        WorldData world = this.currentWorld;
        this.currentWorld = null;
        this.mcWorld = null;
        if (world == null) {
            return;
        }
        world.onClose();
    }

    private Path getWorldDataDirectory(Path parent, Level world) {
        ResourceLocation dimId = world.dimension().location();
        int height = world.dimensionType().logicalHeight();
        return parent.resolve(dimId.getNamespace()).resolve(dimId.getPath() + "_" + height);
    }

    /**
     * @param world The world
     * @return An {@link Optional} containing the world's baritone dir and readme dir, or {@link
     *     Optional#empty()} if the world isn't valid for caching.
     */
    private Optional<Tuple<Path, Path>> getSaveDirectories(Level world) {
        Path worldDir;
        Path readmeDir;

        // If there is an integrated server running (Aka Singleplayer) then do magic to find the
        // world save file
        if (ctx.minecraft().hasSingleplayerServer()) {
            worldDir = ctx.minecraft().getSingleplayerServer().getWorldPath(LevelResource.ROOT);

            // Gets the "depth" of this directory relative to the game's run directory, 2 is the
            // location of the world
            if (worldDir.relativize(ctx.minecraft().gameDirectory.toPath()).getNameCount() != 2) {
                // subdirectory of the main save directory for this world
                worldDir = worldDir.getParent();
            }

            worldDir = worldDir.resolve("maestro");
            readmeDir = worldDir;
        } else { // Otherwise, the server must be remote...
            String folderName;
            final ServerData serverData = ctx.minecraft().getCurrentServer();
            if (serverData != null) {
                folderName = serverData.isRealm() ? "realms" : serverData.ip;
            } else {
                // replaymod causes null currentServer and false singleplayer.
                System.out.println("World seems to be a replay. Not loading Maestro cache.");
                currentWorld = null;
                mcWorld = ctx.world();
                return Optional.empty();
            }
            if (SystemUtils.IS_OS_WINDOWS) {
                folderName = folderName.replace(":", "_");
            }
            // TODO: This should probably be in "maestro/servers"
            worldDir = maestro.getDirectory().resolve(folderName);
            // Just write the readme to the maestro directory instead of each server save in it
            readmeDir = maestro.getDirectory();
        }

        return Optional.of(new Tuple<>(worldDir, readmeDir));
    }

    /** Why does this exist instead of fixing the event? Some mods break the event. Lol. */
    private void detectAndHandleBrokenLoading() {
        if (this.mcWorld != ctx.world()) {
            if (this.currentWorld != null) {
                System.out.println("mc.world unloaded unnoticed! Unloading Maestro cache now.");
                closeWorld();
            }
            if (ctx.world() != null) {
                System.out.println("mc.world loaded unnoticed! Loading Maestro cache now.");
                initWorld(ctx.world());
            }
        } else if (this.currentWorld == null
                && ctx.world() != null
                && (ctx.minecraft().hasSingleplayerServer()
                        || ctx.minecraft().getCurrentServer() != null)) {
            System.out.println("Retrying to load Maestro cache");
            initWorld(ctx.world());
        }
    }
}
