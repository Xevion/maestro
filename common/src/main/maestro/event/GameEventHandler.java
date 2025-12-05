package maestro.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import maestro.Agent;
import maestro.api.event.events.*;
import maestro.api.event.events.type.EventState;
import maestro.api.event.listener.IEventBus;
import maestro.api.event.listener.IGameEventListener;
import maestro.api.utils.MaestroLogger;
import maestro.cache.CachedChunk;
import maestro.cache.WorldProvider;
import maestro.pathing.BlockStateInterface;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

public final class GameEventHandler implements IEventBus {

    private static final Logger log = MaestroLogger.get("event");

    private final Agent maestro;

    private final List<IGameEventListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Tracks whether coordination auto-connect has been attempted.
     *
     * <p>This is a one-time startup flag. Once set to true, auto-connect will not retry even if the
     * coordinationEnabled setting is toggled at runtime. This prevents repeated connection attempts
     * on every tick.
     *
     * <p>To reconnect after toggling the setting, manually use the coordination client API or
     * restart the game.
     */
    private boolean coordinationAutoConnected = false;

    public GameEventHandler(Agent maestro) {
        this.maestro = maestro;
    }

    @Override
    public void onTick(TickEvent event) {
        // Auto-connect to coordinator if enabled (but not if this bot IS the coordinator)
        boolean isCoordinator = "true".equalsIgnoreCase(System.getenv("AUTOSTART_COORDINATOR"));
        if (!coordinationAutoConnected
                && !isCoordinator
                && Agent.settings().coordinationEnabled.value) {
            maestro.coordination.CoordinationClient client = maestro.getCoordinationClient();

            // Create client lazily if it doesn't exist
            if (client == null && maestro.getPlayerContext().player() != null) {
                String workerId = maestro.getPlayerContext().player().getStringUUID();
                String workerName = maestro.getPlayerContext().player().getName().getString();
                client = new maestro.coordination.CoordinationClient(workerId, workerName);
                maestro.setCoordinationClient(client);
            }

            if (client != null) {
                String host = Agent.settings().coordinationHost.value;
                int port = Agent.settings().coordinationPort.value;
                boolean connected = client.connect(host, port);
                if (connected) {
                    log.atInfo()
                            .addKeyValue("host", host)
                            .addKeyValue("port", port)
                            .log("Auto-connected to coordinator");
                }
                coordinationAutoConnected = true;
            }
        }

        if (event.type == TickEvent.Type.IN) {
            try {
                maestro.bsi = new BlockStateInterface(maestro.getPlayerContext(), true);
            } catch (Exception ex) {
                log.atError().setCause(ex).log("Failed to create BlockStateInterface");
                maestro.bsi = null;
            }
        } else {
            maestro.bsi = null;
        }
        listeners.forEach(l -> l.onTick(event));
    }

    @Override
    public void onPostTick(TickEvent event) {
        // DevMode: Execute queued commands
        maestro.getDevModeManager().onPostTick(event);

        listeners.forEach(l -> l.onPostTick(event));
    }

    @Override
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        listeners.forEach(l -> l.onPlayerUpdate(event));
    }

    @Override
    public void onSendChatMessage(ChatEvent event) {
        listeners.forEach(l -> l.onSendChatMessage(event));
    }

    @Override
    public void onPreTabComplete(TabCompleteEvent event) {
        listeners.forEach(l -> l.onPreTabComplete(event));
    }

    @Override
    public void onChunkEvent(ChunkEvent event) {
        EventState state = event.state;
        ChunkEvent.Type type = event.type;

        Level world = maestro.getPlayerContext().world();

        // Whenever the server sends us to another dimension, chunks are unloaded
        // technically after the new world has been loaded, so we perform a check
        // to make sure the chunk being unloaded is already loaded.
        boolean isPreUnload =
                state == EventState.PRE
                        && type == ChunkEvent.Type.UNLOAD
                        && world.getChunkSource().getChunk(event.x, event.z, null, false) != null;

        if (event.isPostPopulate() || isPreUnload) {
            maestro.getWorldProvider()
                    .ifWorldLoaded(
                            worldData -> {
                                LevelChunk chunk = world.getChunk(event.x, event.z);
                                worldData.getCachedWorld().queueForPacking(chunk);
                            });
        }

        listeners.forEach(l -> l.onChunkEvent(event));
    }

    @Override
    public void onBlockChange(BlockChangeEvent event) {
        if (Agent.settings().repackOnAnyBlockChange.value) {
            final boolean keepingTrackOf =
                    event.blocks.stream()
                            .map(pair -> pair.component2())
                            .map(BlockState::getBlock)
                            .anyMatch(CachedChunk.BLOCKS_TO_KEEP_TRACK_OF::contains);

            if (keepingTrackOf) {
                maestro.getWorldProvider()
                        .ifWorldLoaded(
                                worldData -> {
                                    final Level world = maestro.getPlayerContext().world();
                                    ChunkPos pos = event.getChunkPos();
                                    worldData
                                            .getCachedWorld()
                                            .queueForPacking(world.getChunk(pos.x, pos.z));
                                });
            }
        }

        listeners.forEach(l -> l.onBlockChange(event));
    }

    @Override
    public void onRenderPass(RenderEvent event) {
        listeners.forEach(l -> l.onRenderPass(event));
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        WorldProvider cache = maestro.getWorldProvider();

        if (event.state == EventState.POST) {
            cache.closeWorld();
            if (event.world != null) {
                cache.initWorld(event.world);

                // DevMode: Open LAN and queue commands after world load
                maestro.getDevModeManager().onWorldLoad();
            }
        }

        listeners.forEach(l -> l.onWorldEvent(event));
    }

    @Override
    public void onSendPacket(PacketEvent event) {
        listeners.forEach(l -> l.onSendPacket(event));
    }

    @Override
    public void onReceivePacket(PacketEvent event) {
        listeners.forEach(l -> l.onReceivePacket(event));
    }

    @Override
    public void onPlayerRotationMove(RotationMoveEvent event) {
        listeners.forEach(l -> l.onPlayerRotationMove(event));
    }

    @Override
    public void onPlayerSprintState(SprintStateEvent event) {
        listeners.forEach(l -> l.onPlayerSprintState(event));
    }

    @Override
    public void onPlayerDeath() {
        listeners.forEach(IGameEventListener::onPlayerDeath);
    }

    @Override
    public void onPathEvent(PathEvent event) {
        listeners.forEach(l -> l.onPathEvent(event));
    }

    @Override
    public void onChunkOcclusion(ChunkOcclusionEvent event) {
        listeners.forEach(l -> l.onChunkOcclusion(event));
    }

    @Override
    public void registerEventListener(IGameEventListener listener) {
        this.listeners.add(listener);
    }
}
