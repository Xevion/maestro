package maestro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import maestro.api.IAgent;
import maestro.api.MaestroAPI;
import maestro.api.Settings;
import maestro.api.behavior.IBehavior;
import maestro.api.event.listener.IEventBus;
import maestro.api.process.IElytraProcess;
import maestro.api.process.IMaestroProcess;
import maestro.api.utils.IPlayerContext;
import maestro.behavior.*;
import maestro.cache.WorldProvider;
import maestro.command.manager.CommandManager;
import maestro.event.GameEventHandler;
import maestro.process.*;
import maestro.selection.SelectionManager;
import maestro.utils.BlockStateInterface;
import maestro.utils.GuiClick;
import maestro.utils.InputOverrideHandler;
import maestro.utils.PathingControlManager;
import maestro.utils.player.MaestroPlayerContext;
import net.minecraft.client.Minecraft;

public class Agent implements IAgent {

    private static final ThreadPoolExecutor threadPool;

    static {
        threadPool =
                new ThreadPoolExecutor(
                        4, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
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

    private final IPlayerContext playerContext;
    private final WorldProvider worldProvider;

    public BlockStateInterface bsi;

    // Free-look camera state (independent from player rotation)
    private float freeLookYaw = 0.0f;
    private float freeLookPitch = 0.0f;

    // Swimming active state (tracks when swimming behavior is controlling the bot)
    private boolean swimmingActive = false;

    Agent(Minecraft mc) {
        this.mc = mc;
        this.gameEventHandler = new GameEventHandler(this);

        this.directory = mc.gameDirectory.toPath().resolve("maestro");
        if (!Files.exists(this.directory)) {
            try {
                Files.createDirectories(this.directory);
            } catch (IOException ignored) {
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
     * Gets the free-look camera yaw (horizontal rotation). This is independent from the player's
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
     * Gets the free-look camera pitch (vertical rotation). This is independent from the player's
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
     * independently from the bot's movement direction.
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
        if (active && !swimmingActive && this.mc.player != null) {
            this.freeLookYaw = this.mc.player.getYRot();
            this.freeLookPitch = this.mc.player.getXRot();
        }
        this.swimmingActive = active;
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
