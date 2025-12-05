package maestro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import maestro.api.IAgent;
import maestro.api.MaestroAPI;
import maestro.api.Settings;
import maestro.api.behavior.IBehavior;
import maestro.api.event.listener.IEventBus;
import maestro.api.player.MaestroPlayerContext;
import maestro.api.process.IElytraProcess;
import maestro.api.process.IMaestroProcess;
import maestro.api.utils.IPlayerContext;
import maestro.api.utils.MaestroLogger;
import maestro.behavior.*;
import maestro.cache.WorldProvider;
import maestro.command.manager.CommandManager;
import maestro.debug.DevModeManager;
import maestro.debug.MaestroDebugRenderer;
import maestro.event.GameEventHandler;
import maestro.gui.GuiClick;
import maestro.input.InputOverrideHandler;
import maestro.pathing.BlockStateInterface;
import maestro.pathing.PathingControlManager;
import maestro.process.*;
import maestro.selection.SelectionManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class Agent implements IAgent {

    private static final ThreadPoolExecutor threadPool;

    static {
        threadPool =
                new ThreadPoolExecutor(
                        4,
                        8,
                        60L,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(100),
                        new ThreadPoolExecutor.CallerRunsPolicy());

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    threadPool.shutdown();
                                    try {
                                        if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                                            threadPool.shutdownNow();
                                        }
                                    } catch (InterruptedException e) {
                                        threadPool.shutdownNow();
                                        Thread.currentThread().interrupt();
                                    }
                                },
                                "maestro-threadpool-shutdown"));
    }

    private final Minecraft mc;
    private final Path directory;

    private final GameEventHandler gameEventHandler;

    private final PathingBehavior pathingBehavior;
    private final LookBehavior lookBehavior;
    private final InventoryBehavior inventoryBehavior;
    private final InputOverrideHandler inputOverrideHandler;
    private final SwimmingBehavior swimmingBehavior;
    private final RotationManager rotationManager;

    private final FollowProcess followProcess;
    private final MineProcess mineProcess;
    private final GetToBlockProcess getToBlockProcess;
    private final CustomGoalProcess customGoalProcess;
    private final BuilderProcess builderProcess;
    private final ExploreProcess exploreProcess;
    private final FarmProcess farmProcess;
    private final InventoryPauserProcess inventoryPauserProcess;
    private final IElytraProcess elytraProcess;
    private final AttackProcess attackProcess;
    private final RangedCombatProcess rangedCombatProcess;

    private final PathingControlManager pathingControlManager;
    private final SelectionManager selectionManager;
    private final CommandManager commandManager;
    private final MaestroDebugRenderer debugRenderer;

    private final IPlayerContext playerContext;
    private final WorldProvider worldProvider;

    public BlockStateInterface bsi;

    // Multi-agent coordination
    private maestro.coordination.CoordinationServer coordinationServer;
    private maestro.coordination.CoordinationClient coordinationClient;

    // Development mode manager
    private final DevModeManager devModeManager;

    // Free-look camera state (independent of player rotation)
    private float freeLookYaw = 0.0f;
    private float freeLookPitch = 0.0f;

    // Swimming active state (tracks when swimming behavior is controlling the bot)
    private boolean swimmingActive = false;

    // Freecam state (independent camera position and activation)
    private Vec3 freecamPosition = null;
    private Vec3 prevFreecamPosition = null;
    private boolean freecamActive = false;
    private int savedFov = -1;
    private boolean savedSmoothCamera = false;
    private FreecamMode freecamMode = FreecamMode.STATIC;
    private Vec3 lastPlayerPosition = null;
    private Vec3 freecamFollowOffset = null;
    private Vec3 prevFreecamFollowOffset = null;

    // Follow mode position tracking - we track our own snapshots to avoid physics artifacts
    // from Minecraft's built-in xOld/x interpolation
    private Vec3 followTargetPrev = null;
    private Vec3 followTargetCurrent = null;

    Agent(Minecraft mc) {
        this.mc = mc;
        this.gameEventHandler = new GameEventHandler(this);
        this.devModeManager = new DevModeManager(this);

        this.directory = mc.gameDirectory.toPath().resolve("maestro");
        if (!Files.exists(this.directory)) {
            try {
                Files.createDirectories(this.directory);
            } catch (IOException ignored) {
                // expected
            }
        }

        // Define this before behaviors try and get it, or else it will be null and the builds will
        // fail!
        this.playerContext = new MaestroPlayerContext(this, mc);

        {
            this.lookBehavior = this.registerBehavior(LookBehavior::new);
            this.pathingBehavior = this.registerBehavior(PathingBehavior::new);
            this.inventoryBehavior = this.registerBehavior(InventoryBehavior::new);
            this.inputOverrideHandler = this.registerBehavior(InputOverrideHandler::new);
            this.swimmingBehavior = this.registerBehavior(SwimmingBehavior::new);
            this.rotationManager = this.registerBehavior(RotationManager::new);
            this.registerBehavior(WaypointBehavior::new);
            this.registerBehavior(FreecamBehavior::new);
        }

        this.pathingControlManager = new PathingControlManager(this);
        {
            this.followProcess = this.registerProcess(FollowProcess::new);
            this.mineProcess = this.registerProcess(MineProcess::new);
            this.customGoalProcess = this.registerProcess(CustomGoalProcess::new); // very high iq
            this.getToBlockProcess = this.registerProcess(GetToBlockProcess::new);
            this.builderProcess = this.registerProcess(BuilderProcess::new);
            this.exploreProcess = this.registerProcess(ExploreProcess::new);
            this.farmProcess = this.registerProcess(FarmProcess::new);
            this.inventoryPauserProcess = this.registerProcess(InventoryPauserProcess::new);
            this.elytraProcess = this.registerProcess(ElytraProcess::create);
            this.attackProcess = this.registerProcess(AttackProcess::new);
            this.rangedCombatProcess = this.registerProcess(RangedCombatProcess::new);
            // Register ranged combat process for render events
            this.gameEventHandler.registerEventListener(this.rangedCombatProcess);
            this.registerProcess(BackfillProcess::new);
        }

        this.worldProvider = new WorldProvider(this);
        this.selectionManager = new SelectionManager(this);
        this.commandManager = new CommandManager(this);

        // Register debug renderer
        this.debugRenderer = new MaestroDebugRenderer(this);
        this.gameEventHandler.registerEventListener(this.debugRenderer);
    }

    public void registerBehavior(IBehavior behavior) {
        this.gameEventHandler.registerEventListener(behavior);
    }

    public <T extends IBehavior> T registerBehavior(Function<Agent, T> constructor) {
        final T behavior = constructor.apply(this);
        this.registerBehavior(behavior);
        return behavior;
    }

    public <T extends IMaestroProcess> T registerProcess(Function<Agent, T> constructor) {
        final T behavior = constructor.apply(this);
        this.pathingControlManager.registerProcess(behavior);
        return behavior;
    }

    @Override
    public PathingControlManager getPathingControlManager() {
        return this.pathingControlManager;
    }

    @Override
    public InputOverrideHandler getInputOverrideHandler() {
        return this.inputOverrideHandler;
    }

    @Override
    public CustomGoalProcess getCustomGoalProcess() {
        return this.customGoalProcess;
    }

    @Override
    public GetToBlockProcess getGetToBlockProcess() {
        return this.getToBlockProcess;
    }

    @Override
    public IPlayerContext getPlayerContext() {
        return this.playerContext;
    }

    @Override
    public FollowProcess getFollowProcess() {
        return this.followProcess;
    }

    @Override
    public BuilderProcess getBuilderProcess() {
        return this.builderProcess;
    }

    public InventoryBehavior getInventoryBehavior() {
        return this.inventoryBehavior;
    }

    @Override
    public LookBehavior getLookBehavior() {
        return this.lookBehavior;
    }

    public SwimmingBehavior getSwimmingBehavior() {
        return this.swimmingBehavior;
    }

    public RotationManager getRotationManager() {
        return this.rotationManager;
    }

    /**
     * Gets the free-look camera yaw (horizontal rotation). This is independent of the player's
     * actual rotation when free-look is enabled.
     */
    public float getFreeLookYaw() {
        return freeLookYaw;
    }

    /**
     * Sets the free-look camera yaw. Called by mouse movement handling when free-look is enabled.
     */
    public void setFreeLookYaw(float yaw) {
        this.freeLookYaw = yaw;
    }

    /**
     * Gets the free-look camera pitch (vertical rotation). This is independent of the player's
     * actual rotation when free-look is enabled.
     */
    public float getFreeLookPitch() {
        return freeLookPitch;
    }

    /**
     * Sets the free-look camera pitch. Called by mouse movement handling when free-look is enabled.
     */
    public void setFreeLookPitch(float pitch) {
        this.freeLookPitch = pitch;
    }

    /**
     * Updates free-look camera angles based on mouse delta. This allows the user to look around
     * independently of the bot's movement direction.
     */
    public void updateFreeLook(double deltaX, double deltaY) {
        // Similar to Minecraft's camera handling
        float sensitivity = 0.6f + 0.2f; // Default sensitivity

        freeLookYaw += (float) deltaX * 0.15f * sensitivity;
        freeLookPitch += (float) deltaY * 0.15f * sensitivity;

        // Clamp pitch to -90 to +90 degrees
        freeLookPitch = Math.max(-90.0f, Math.min(90.0f, freeLookPitch));
    }

    /**
     * Returns true if swimming behavior is actively controlling the bot. This is used to
     * conditionally activate free-look camera mode only during autonomous swimming.
     */
    public boolean isSwimmingActive() {
        return swimmingActive;
    }

    /**
     * Sets whether swimming behavior is actively controlling the bot. When activating swimming,
     * this initializes the free-look angles to the current player rotation for smooth transition.
     */
    public void setSwimmingActive(boolean active) {
        // When activating swimming, initialize free-look to current player rotation
        // Don't override rotation if freecam is already active
        if (active && !swimmingActive && !freecamActive && this.mc.player != null) {
            this.freeLookYaw = this.mc.player.getYRot();
            this.freeLookPitch = this.mc.player.getXRot();
        }
        this.swimmingActive = active;
    }

    /**
     * Returns true if freecam is currently active. When active, the camera detaches from the player
     * entity and can be controlled independently.
     */
    public boolean isFreecamActive() {
        return freecamActive;
    }

    /** Gets the current freecam position. Returns null if freecam is not active. */
    public Vec3 getFreecamPosition() {
        return freecamPosition;
    }

    /** Sets the freecam position. Updates the previous position for interpolation. */
    public void setFreecamPosition(Vec3 position) {
        this.prevFreecamPosition = this.freecamPosition;
        this.freecamPosition = position;
    }

    public FreecamMode getFreecamMode() {
        return freecamMode;
    }

    public void setFreecamMode(FreecamMode mode) {
        this.freecamMode = mode;
    }

    /** Gets the saved FOV value from when freecam was activated. */
    public int getSavedFov() {
        return this.savedFov;
    }

    public void toggleFreecamMode() {
        FreecamMode newMode =
                (freecamMode == FreecamMode.STATIC) ? FreecamMode.FOLLOW : FreecamMode.STATIC;
        this.freecamMode = newMode;

        // When switching to FOLLOW mode, calculate offset from current static position to player
        if (newMode == FreecamMode.FOLLOW && this.mc.player != null && freecamPosition != null) {
            Vec3 playerPos =
                    new Vec3(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ());
            Vec3 offset = freecamPosition.subtract(playerPos);

            // Initialize both current and prev to the same value for stable start
            this.freecamFollowOffset = offset;
            this.prevFreecamFollowOffset = offset;

            // Initialize follow target positions for stable interpolation
            this.followTargetPrev = playerPos;
            this.followTargetCurrent = playerPos;

            MaestroLogger.get("dev")
                    .atDebug()
                    .addKeyValue("mode", "FOLLOW")
                    .log("Freecam mode switched");
        } else if (newMode == FreecamMode.STATIC
                && this.mc.player != null
                && freecamFollowOffset != null) {
            // When switching to STATIC mode, set position from current follow position
            Vec3 playerPos =
                    new Vec3(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ());
            Vec3 staticPos = playerPos.add(freecamFollowOffset);

            // Initialize both current and prev to the same value for stable start
            this.freecamPosition = staticPos;
            this.prevFreecamPosition = staticPos;

            MaestroLogger.get("dev")
                    .atDebug()
                    .addKeyValue("mode", "STATIC")
                    .log("Freecam mode switched");
        } else {
            MaestroLogger.get("dev")
                    .atDebug()
                    .addKeyValue("mode", newMode)
                    .log("Freecam mode switched");
        }
    }

    public Vec3 getLastPlayerPosition() {
        return lastPlayerPosition;
    }

    public void setLastPlayerPosition(Vec3 pos) {
        this.lastPlayerPosition = pos;
    }

    public Vec3 getFreecamFollowOffset() {
        return freecamFollowOffset;
    }

    public void setFreecamFollowOffset(Vec3 offset) {
        this.prevFreecamFollowOffset = this.freecamFollowOffset;
        this.freecamFollowOffset = offset;

        // Initialize prev if this is the first offset set
        if (this.prevFreecamFollowOffset == null) {
            this.prevFreecamFollowOffset = offset;
        }
    }

    /**
     * Updates follow target position snapshots. Call this once per tick to capture consistent
     * positions for smooth interpolation. This avoids jitter from Minecraft's physics artifacts in
     * the entity's xOld/x interpolation.
     */
    public void updateFollowTarget() {
        if (this.mc.player == null) return;

        Vec3 currentPos = this.mc.player.position();

        // Shift current to prev, update current
        this.followTargetPrev = this.followTargetCurrent;
        this.followTargetCurrent = currentPos;

        // Initialize prev if this is the first update
        if (this.followTargetPrev == null) {
            this.followTargetPrev = currentPos;
        }
    }

    /** Gets interpolated freecam X coordinate for smooth rendering between ticks. */
    public double getFreecamX(float tickDelta) {
        // In FOLLOW mode, interpolate both player position AND offset for smooth 60 FPS
        if (freecamMode == FreecamMode.FOLLOW
                && freecamFollowOffset != null
                && prevFreecamFollowOffset != null) {
            // Interpolate player position
            double playerX;
            if (followTargetPrev != null && followTargetCurrent != null) {
                playerX =
                        followTargetPrev.x
                                + (followTargetCurrent.x - followTargetPrev.x) * tickDelta;
            } else if (this.mc.player != null) {
                playerX = this.mc.player.getX();
            } else {
                playerX = 0;
            }

            // Interpolate offset
            double offsetX =
                    prevFreecamFollowOffset.x
                            + (freecamFollowOffset.x - prevFreecamFollowOffset.x) * tickDelta;

            return playerX + offsetX;
        }

        // STATIC mode: standard interpolation
        if (freecamPosition == null || prevFreecamPosition == null) {
            return freecamPosition != null ? freecamPosition.x : 0;
        }
        return prevFreecamPosition.x + (freecamPosition.x - prevFreecamPosition.x) * tickDelta;
    }

    /** Gets interpolated freecam Y coordinate for smooth rendering between ticks. */
    public double getFreecamY(float tickDelta) {
        // In FOLLOW mode, interpolate both player position AND offset for smooth 60 FPS
        if (freecamMode == FreecamMode.FOLLOW
                && freecamFollowOffset != null
                && prevFreecamFollowOffset != null) {
            // Interpolate player position
            double playerY;
            if (followTargetPrev != null && followTargetCurrent != null) {
                playerY =
                        followTargetPrev.y
                                + (followTargetCurrent.y - followTargetPrev.y) * tickDelta;
            } else if (this.mc.player != null) {
                playerY = this.mc.player.getY();
            } else {
                playerY = 0;
            }

            // Interpolate offset
            double offsetY =
                    prevFreecamFollowOffset.y
                            + (freecamFollowOffset.y - prevFreecamFollowOffset.y) * tickDelta;

            return playerY + offsetY;
        }

        // STATIC mode: standard interpolation
        if (freecamPosition == null || prevFreecamPosition == null) {
            return freecamPosition != null ? freecamPosition.y : 0;
        }
        return prevFreecamPosition.y + (freecamPosition.y - prevFreecamPosition.y) * tickDelta;
    }

    /** Gets interpolated freecam Z coordinate for smooth rendering between ticks. */
    public double getFreecamZ(float tickDelta) {
        // In FOLLOW mode, interpolate both player position AND offset for smooth 60 FPS
        if (freecamMode == FreecamMode.FOLLOW
                && freecamFollowOffset != null
                && prevFreecamFollowOffset != null) {
            // Interpolate player position
            double playerZ;
            if (followTargetPrev != null && followTargetCurrent != null) {
                playerZ =
                        followTargetPrev.z
                                + (followTargetCurrent.z - followTargetPrev.z) * tickDelta;
            } else if (this.mc.player != null) {
                playerZ = this.mc.player.getZ();
            } else {
                playerZ = 0;
            }

            // Interpolate offset
            double offsetZ =
                    prevFreecamFollowOffset.z
                            + (freecamFollowOffset.z - prevFreecamFollowOffset.z) * tickDelta;

            return playerZ + offsetZ;
        }

        // STATIC mode: standard interpolation
        if (freecamPosition == null || prevFreecamPosition == null) {
            return freecamPosition != null ? freecamPosition.z : 0;
        }
        return prevFreecamPosition.z + (freecamPosition.z - prevFreecamPosition.z) * tickDelta;
    }

    /**
     * Activates freecam mode. Captures the current camera position and rotation as the initial
     * freecam state, and saves FOV settings.
     */
    public void activateFreecam() {
        if (!freecamActive && this.mc.player != null && this.mc.gameRenderer != null) {
            Camera camera = this.mc.gameRenderer.getMainCamera();
            this.freecamPosition = camera.getPosition();
            this.prevFreecamPosition = this.freecamPosition;
            this.freeLookYaw = camera.getYRot();
            this.freeLookPitch = camera.getXRot();

            // Save FOV settings
            this.savedFov = this.mc.options.fov().get();
            this.savedSmoothCamera = this.mc.options.smoothCamera;

            // Initialize freecam mode and player tracking
            this.freecamMode = Agent.settings().freecamDefaultMode.value;
            if (this.mc.player != null) {
                this.lastPlayerPosition = this.mc.player.position();
            }

            this.freecamActive = true;

            // Reload chunks if configured (queue for next tick)
            if (Agent.settings().freecamReloadChunks.value && this.mc.levelRenderer != null) {
                this.mc.execute(() -> this.mc.levelRenderer.allChanged());
            }
        }
    }

    /** Deactivates freecam mode. Returns camera to normal player-following behavior. */
    public void deactivateFreecam() {
        this.freecamActive = false;
        this.freecamPosition = null;
        this.prevFreecamPosition = null;
        this.lastPlayerPosition = null;
        this.followTargetPrev = null;
        this.followTargetCurrent = null;
        this.freecamFollowOffset = null;
        this.prevFreecamFollowOffset = null;

        // Restore FOV settings
        if (this.savedFov >= 0) {
            this.mc.options.fov().set(this.savedFov);
            this.savedFov = -1;
        }
        this.mc.options.smoothCamera = this.savedSmoothCamera;

        // Reload chunks if configured (queue for next tick)
        if (Agent.settings().freecamReloadChunks.value && this.mc.levelRenderer != null) {
            this.mc.execute(() -> this.mc.levelRenderer.allChanged());
        }
    }

    @Override
    public ExploreProcess getExploreProcess() {
        return this.exploreProcess;
    }

    @Override
    public MineProcess getMineProcess() {
        return this.mineProcess;
    }

    @Override
    public FarmProcess getFarmProcess() {
        return this.farmProcess;
    }

    @Override
    public AttackProcess getAttackProcess() {
        return this.attackProcess;
    }

    @Override
    public RangedCombatProcess getRangedCombatProcess() {
        return this.rangedCombatProcess;
    }

    public InventoryPauserProcess getInventoryPauserProcess() {
        return this.inventoryPauserProcess;
    }

    public maestro.coordination.CoordinationServer getCoordinationServer() {
        return this.coordinationServer;
    }

    public void setCoordinationServer(maestro.coordination.CoordinationServer server) {
        this.coordinationServer = server;
    }

    public maestro.coordination.CoordinationClient getCoordinationClient() {
        return this.coordinationClient;
    }

    public void setCoordinationClient(maestro.coordination.CoordinationClient client) {
        this.coordinationClient = client;
    }

    public DevModeManager getDevModeManager() {
        return this.devModeManager;
    }

    @Override
    public PathingBehavior getPathingBehavior() {
        return this.pathingBehavior;
    }

    @Override
    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    @Override
    public WorldProvider getWorldProvider() {
        return this.worldProvider;
    }

    @Override
    public IEventBus getGameEventHandler() {
        return this.gameEventHandler;
    }

    @Override
    public CommandManager getCommandManager() {
        return this.commandManager;
    }

    @Override
    public MaestroDebugRenderer getDebugRenderer() {
        return this.debugRenderer;
    }

    @Override
    public IElytraProcess getElytraProcess() {
        return this.elytraProcess;
    }

    @Override
    public void openClick() {
        new Thread(
                        () -> {
                            try {
                                Thread.sleep(100);
                                mc.execute(() -> mc.setScreen(new GuiClick()));
                            } catch (Exception ignored) {
                                // expected
                            }
                        })
                .start();
    }

    public Path getDirectory() {
        return this.directory;
    }

    public static Settings settings() {
        return MaestroAPI.getSettings();
    }

    public static Executor getExecutor() {
        return threadPool;
    }
}
