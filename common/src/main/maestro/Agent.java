package maestro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import maestro.api.Settings;
import maestro.api.behavior.IBehavior;
import maestro.api.player.PlayerContext;
import maestro.api.task.ITask;
import maestro.api.utils.Loggers;
import maestro.api.utils.SettingsUtil;
import maestro.behavior.*;
import maestro.cache.WorldProvider;
import maestro.command.manager.CommandManager;
import maestro.coordination.CoordinationClient;
import maestro.coordination.CoordinationServer;
import maestro.debug.DebugRenderer;
import maestro.debug.DevModeManager;
import maestro.event.GameEventHandler;
import maestro.gui.GuiClick;
import maestro.input.InputController;
import maestro.pathing.BlockStateInterface;
import maestro.pathing.TaskCoordinator;
import maestro.selection.SelectionManager;
import maestro.task.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class Agent {

    // Multi-agent registry (replaces AgentProvider)
    private static final CopyOnWriteArrayList<Agent> allAgents = new CopyOnWriteArrayList<>();

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
    private final Settings settings;

    private final GameEventHandler gameEventHandler;

    private final PathingBehavior pathingBehavior;
    private final LookBehavior lookBehavior;
    private final InventoryBehavior inventoryBehavior;
    private final InputController inputController;
    private final SwimmingBehavior swimmingBehavior;
    private final RotationManager rotationManager;

    private final FollowTask followProcess;
    private final MineTask mineTask;
    private final GetToBlockTask getToBlockTask;
    private final CustomGoalTask customGoalTask;
    private final BuilderTask builderTask;
    private final ExploreTask exploreTask;
    private final FarmTask farmProcess;
    private final InventoryPauserTask inventoryPauserTask;
    private final ITask elytraTask;
    private final AttackTask attackTask;
    private final RangedCombatTask rangedCombatTask;

    private final TaskCoordinator taskCoordinator;
    private final SelectionManager selectionManager;
    private final CommandManager commandManager;
    private final DebugRenderer debugRenderer;

    private final PlayerContext playerContext;
    private final WorldProvider worldProvider;

    public BlockStateInterface bsi;

    // Multi-agent coordination
    private CoordinationServer coordinationServer;
    private CoordinationClient coordinationClient;

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

        // Load settings for this agent instance
        this.settings = new Settings();
        SettingsUtil.readAndApply(this.settings, SettingsUtil.SETTINGS_DEFAULT_NAME);

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
        this.playerContext = new PlayerContext(this, mc);

        // Register this agent in the global registry
        allAgents.add(this);

        {
            this.lookBehavior = this.registerBehavior(LookBehavior::new);
            this.pathingBehavior = this.registerBehavior(PathingBehavior::new);
            this.inventoryBehavior = this.registerBehavior(InventoryBehavior::new);
            this.inputController = this.registerBehavior(InputController::new);
            this.swimmingBehavior = this.registerBehavior(SwimmingBehavior::new);
            this.rotationManager = this.registerBehavior(RotationManager::new);
            this.registerBehavior(WaypointBehavior::new);
            this.registerBehavior(FreecamBehavior::new);
        }

        this.taskCoordinator = new TaskCoordinator(this);
        {
            this.followProcess = this.registerTask(FollowTask::new);
            this.mineTask = this.registerTask(MineTask::new);
            this.customGoalTask = this.registerTask(CustomGoalTask::new); // very high iq
            this.getToBlockTask = this.registerTask(GetToBlockTask::new);
            this.builderTask = this.registerTask(BuilderTask::new);
            this.exploreTask = this.registerTask(ExploreTask::new);
            this.farmProcess = this.registerTask(FarmTask::new);
            this.inventoryPauserTask = this.registerTask(InventoryPauserTask::new);
            this.elytraTask = this.registerTask(ElytraTask::create);
            this.attackTask = this.registerTask(AttackTask::new);
            this.rangedCombatTask = this.registerTask(RangedCombatTask::new);
            // Register ranged combat process for render events
            this.gameEventHandler.registerEventListener(this.rangedCombatTask);
            this.registerTask(BackfillTask::new);
        }

        this.worldProvider = new WorldProvider(this);
        this.selectionManager = new SelectionManager(this);
        this.commandManager = new CommandManager(this);

        // Register debug renderer
        this.debugRenderer = new DebugRenderer(this);
        this.gameEventHandler.registerEventListener(this.debugRenderer);

        // Register SDF demo renderer
        //        this.gameEventHandler.registerEventListener(new GfxDemo(this));
    }

    public void registerBehavior(IBehavior behavior) {
        this.gameEventHandler.registerEventListener(behavior);
    }

    public <T extends IBehavior> T registerBehavior(Function<Agent, T> constructor) {
        final T behavior = constructor.apply(this);
        this.registerBehavior(behavior);
        return behavior;
    }

    public <T extends ITask> T registerTask(Function<Agent, T> constructor) {
        final T behavior = constructor.apply(this);
        this.taskCoordinator.registerTask(behavior);
        return behavior;
    }

    public TaskCoordinator getPathingControlManager() {
        return this.taskCoordinator;
    }

    public InputController getInputOverrideHandler() {
        return this.inputController;
    }

    public CustomGoalTask getCustomGoalTask() {
        return this.customGoalTask;
    }

    public GetToBlockTask getGetToBlockTask() {
        return this.getToBlockTask;
    }

    public PlayerContext getPlayerContext() {
        return this.playerContext;
    }

    public FollowTask getFollowTask() {
        return this.followProcess;
    }

    public BuilderTask getBuilderTask() {
        return this.builderTask;
    }

    public InventoryBehavior getInventoryBehavior() {
        return this.inventoryBehavior;
    }

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
            Vec3 playerPos = this.mc.player.position();
            Vec3 offset = freecamPosition.subtract(playerPos);

            // Initialize both current and prev to the same value for stable start
            this.freecamFollowOffset = offset;
            this.prevFreecamFollowOffset = offset;

            // Initialize follow target positions for stable interpolation
            this.followTargetPrev = playerPos;
            this.followTargetCurrent = playerPos;

            Loggers.Dev.get().atDebug().addKeyValue("mode", "FOLLOW").log("Freecam mode switched");
        } else if (newMode == FreecamMode.STATIC
                && this.mc.player != null
                && freecamFollowOffset != null) {
            // When switching to STATIC mode, set position from current follow position
            Vec3 playerPos = this.mc.player.position();
            Vec3 staticPos = playerPos.add(freecamFollowOffset);

            // Initialize both current and prev to the same value for stable start
            this.freecamPosition = staticPos;
            this.prevFreecamPosition = staticPos;

            Loggers.Dev.get().atDebug().addKeyValue("mode", "STATIC").log("Freecam mode switched");
        } else {
            Loggers.Dev.get().atDebug().addKeyValue("mode", newMode).log("Freecam mode switched");
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
            this.freecamMode = Agent.getPrimaryAgent().getSettings().freecamDefaultMode.value;
            if (this.mc.player != null) {
                this.lastPlayerPosition = this.mc.player.position();
            }

            this.freecamActive = true;

            // Reload chunks if configured (queue for next tick)
            if (Agent.getPrimaryAgent().getSettings().freecamReloadChunks.value
                    && this.mc.levelRenderer != null) {
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
        if (Agent.getPrimaryAgent().getSettings().freecamReloadChunks.value
                && this.mc.levelRenderer != null) {
            this.mc.execute(() -> this.mc.levelRenderer.allChanged());
        }
    }

    public ExploreTask getExploreTask() {
        return this.exploreTask;
    }

    public MineTask getMineTask() {
        return this.mineTask;
    }

    public FarmTask getFarmTask() {
        return this.farmProcess;
    }

    public AttackTask getAttackTask() {
        return this.attackTask;
    }

    public RangedCombatTask getRangedCombatTask() {
        return this.rangedCombatTask;
    }

    public InventoryPauserTask getInventoryPauserTask() {
        return this.inventoryPauserTask;
    }

    public CoordinationServer getCoordinationServer() {
        return this.coordinationServer;
    }

    public void setCoordinationServer(CoordinationServer server) {
        this.coordinationServer = server;
    }

    public CoordinationClient getCoordinationClient() {
        return this.coordinationClient;
    }

    public void setCoordinationClient(CoordinationClient client) {
        this.coordinationClient = client;
    }

    public DevModeManager getDevModeManager() {
        return this.devModeManager;
    }

    public PathingBehavior getPathingBehavior() {
        return this.pathingBehavior;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public WorldProvider getWorldProvider() {
        return this.worldProvider;
    }

    public GameEventHandler getGameEventHandler() {
        return this.gameEventHandler;
    }

    public CommandManager getCommandManager() {
        return this.commandManager;
    }

    public DebugRenderer getDebugRenderer() {
        return this.debugRenderer;
    }

    public ElytraTask getElytraTask() {
        return (ElytraTask) this.elytraTask;
    }

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

    @NotNull
    public Settings getSettings() {
        return this.settings;
    }

    /**
     * Execute a command directly without going through the chat system. This is the preferred way
     * for clickable UI components to trigger commands.
     *
     * @param command The command string (without prefix)
     * @return true if command was executed successfully
     */
    public boolean executeCommand(String command) {
        return this.commandManager.execute(command);
    }

    // Static accessors for multi-agent scenarios

    /**
     * Get or create the primary agent instance. Lazily creates the agent on first access using the
     * Minecraft singleton.
     */
    public static Agent getPrimaryAgent() {
        if (allAgents.isEmpty()) {
            new Agent(Minecraft.getInstance());
        }
        return allAgents.getFirst();
    }

    /** Get all agent instances for multi-agent scenarios. */
    public static List<Agent> getAllAgents() {
        return List.copyOf(allAgents);
    }

    /** Find the agent associated with a specific player. */
    public static Agent getAgentForPlayer(LocalPlayer player) {
        for (Agent agent : allAgents) {
            if (agent.playerContext.player() == player) {
                return agent;
            }
        }
        return null;
    }

    /** Find the agent associated with a specific connection. */
    public static Agent getAgentForConnection(ClientPacketListener connection) {
        for (Agent agent : allAgents) {
            LocalPlayer player = agent.playerContext.player();
            if (player != null && player.connection == connection) {
                return agent;
            }
        }
        return null;
    }

    public static Executor getExecutor() {
        return threadPool;
    }
}
