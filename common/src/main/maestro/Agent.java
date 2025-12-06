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
import maestro.behavior.*;
import maestro.behavior.IBehavior;
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
import maestro.player.PlayerContext;
import maestro.selection.SelectionManager;
import maestro.task.*;
import maestro.task.ITask;
import maestro.utils.SettingsUtil;
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
    private final FreecamBehavior freecamBehavior;

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

    // Swimming active state (tracks when swimming behavior is controlling the bot)
    private boolean swimmingActive = false;

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
            this.freecamBehavior = this.registerBehavior(FreecamBehavior::new);
            this.registerBehavior(WaypointBehavior::new);
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

    public FreecamBehavior getFreecamBehavior() {
        return this.freecamBehavior;
    }

    // ===== Freecam delegation methods =====

    public float getFreeLookYaw() {
        return this.freecamBehavior.getYaw();
    }

    public float getFreeLookPitch() {
        return this.freecamBehavior.getPitch();
    }

    public void updateFreeLook(double deltaX, double deltaY) {
        this.freecamBehavior.updateMouseLook(deltaX, deltaY);
    }

    public boolean isFreecamActive() {
        return this.freecamBehavior.isActive();
    }

    public Vec3 getFreecamPosition() {
        return this.freecamBehavior.getPosition();
    }

    public FreecamMode getFreecamMode() {
        return this.freecamBehavior.getMode();
    }

    public void toggleFreecamMode() {
        this.freecamBehavior.toggleMode();
    }

    public void activateFreecam() {
        this.freecamBehavior.activate();
    }

    public void deactivateFreecam() {
        this.freecamBehavior.deactivate();
    }

    public double getFreecamX(float tickDelta) {
        return this.freecamBehavior.getInterpolatedX(tickDelta);
    }

    public double getFreecamY(float tickDelta) {
        return this.freecamBehavior.getInterpolatedY(tickDelta);
    }

    public double getFreecamZ(float tickDelta) {
        return this.freecamBehavior.getInterpolatedZ(tickDelta);
    }

    public int getSavedFov() {
        return this.freecamBehavior.getSavedFov();
    }

    // ===== Swimming state =====

    public boolean isSwimmingActive() {
        return swimmingActive;
    }

    public void setSwimmingActive(boolean active) {
        this.swimmingActive = active;
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
    @NotNull
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
            if (agent.playerContext.player().equals(player)) {
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
