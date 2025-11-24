package maestro.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import maestro.Maestro;
import maestro.api.event.events.*;
import maestro.api.event.events.type.EventState;
import maestro.api.event.listener.IEventBus;
import maestro.api.event.listener.IGameEventListener;
import maestro.api.utils.Helper;
import maestro.api.utils.Pair;
import maestro.cache.CachedChunk;
import maestro.cache.WorldProvider;
import maestro.utils.BlockStateInterface;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public final class GameEventHandler implements IEventBus, Helper {

    private final Maestro maestro;

    private final List<IGameEventListener> listeners = new CopyOnWriteArrayList<>();

    public GameEventHandler(Maestro maestro) {
        this.maestro = maestro;
    }

    @Override
    public final void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.IN) {
            try {
                maestro.bsi = new BlockStateInterface(maestro.getPlayerContext(), true);
            } catch (Exception ex) {
                ex.printStackTrace();
                maestro.bsi = null;
            }
        } else {
            maestro.bsi = null;
        }
        listeners.forEach(l -> l.onTick(event));
    }

    @Override
    public void onPostTick(TickEvent event) {
        listeners.forEach(l -> l.onPostTick(event));
    }

    @Override
    public final void onPlayerUpdate(PlayerUpdateEvent event) {
        listeners.forEach(l -> l.onPlayerUpdate(event));
    }

    @Override
    public final void onSendChatMessage(ChatEvent event) {
        listeners.forEach(l -> l.onSendChatMessage(event));
    }

    @Override
    public void onPreTabComplete(TabCompleteEvent event) {
        listeners.forEach(l -> l.onPreTabComplete(event));
    }

    @Override
    public void onChunkEvent(ChunkEvent event) {
        EventState state = event.getState();
        ChunkEvent.Type type = event.getType();

        Level world = maestro.getPlayerContext().world();

        // Whenever the server sends us to another dimension, chunks are unloaded
        // technically after the new world has been loaded, so we perform a check
        // to make sure the chunk being unloaded is already loaded.
        boolean isPreUnload =
                state == EventState.PRE
                        && type == ChunkEvent.Type.UNLOAD
                        && world.getChunkSource().getChunk(event.getX(), event.getZ(), null, false)
                                != null;

        if (event.isPostPopulate() || isPreUnload) {
            maestro.getWorldProvider()
                    .ifWorldLoaded(
                            worldData -> {
                                LevelChunk chunk = world.getChunk(event.getX(), event.getZ());
                                worldData.getCachedWorld().queueForPacking(chunk);
                            });
        }

        listeners.forEach(l -> l.onChunkEvent(event));
    }

    @Override
    public void onBlockChange(BlockChangeEvent event) {
        if (Maestro.settings().repackOnAnyBlockChange.value) {
            final boolean keepingTrackOf =
                    event.getBlocks().stream()
                            .map(Pair::second)
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
    public final void onRenderPass(RenderEvent event) {
        listeners.forEach(l -> l.onRenderPass(event));
    }

    @Override
    public final void onWorldEvent(WorldEvent event) {
        WorldProvider cache = maestro.getWorldProvider();

        if (event.getState() == EventState.POST) {
            cache.closeWorld();
            if (event.getWorld() != null) {
                cache.initWorld(event.getWorld());
            }
        }

        listeners.forEach(l -> l.onWorldEvent(event));
    }

    @Override
    public final void onSendPacket(PacketEvent event) {
        listeners.forEach(l -> l.onSendPacket(event));
    }

    @Override
    public final void onReceivePacket(PacketEvent event) {
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
    public void onBlockInteract(BlockInteractEvent event) {
        listeners.forEach(l -> l.onBlockInteract(event));
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
    public final void registerEventListener(IGameEventListener listener) {
        this.listeners.add(listener);
    }
}
