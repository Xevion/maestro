package maestro.utils;

import maestro.Agent;
import maestro.api.event.events.TickEvent;
import maestro.api.utils.MaestroLogger;
import maestro.coordination.CoordinationServer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

/**
 * Manages development mode features: auto-open LAN, auto-start coordinator server.
 *
 * <p>Configuration via environment variables:
 *
 * <ul>
 *   <li>AUTOSTART_COORDINATOR - Auto-open LAN and start coordinator server
 *   <li>COORDINATOR_GOAL - Goal value for coordinator server (default: 100)
 * </ul>
 *
 * <p>Note: World autoloading is handled via --quickPlaySingleplayer command-line argument
 */
public final class DevModeManager {
    private static final Logger log = MaestroLogger.get("dev");

    private final Agent maestro;

    private boolean lanOpened = false;
    private boolean lanRequested = false;
    private boolean coordinatorStarted = false;
    private boolean coordinatorRequested = false;

    public DevModeManager(Agent maestro) {
        this.maestro = maestro;
    }

    /** Called when a world has finished loading. Marks LAN and coordinator for startup. */
    public void onWorldLoad() {
        if (shouldAutoStartCoordinator()) {
            log.atDebug().log("Auto-start enabled");

            if (!lanOpened) {
                lanRequested = true;
                log.atDebug().log("LAN opening requested");
            }

            if (!coordinatorStarted) {
                coordinatorRequested = true;
                log.atDebug().log("Coordinator startup requested");
            }
        } else {
            log.atDebug()
                    .addKeyValue("env_var", System.getenv("AUTOSTART_COORDINATOR"))
                    .log("Auto-start disabled");
        }
    }

    /**
     * Called on post-tick to open LAN and start coordinator server.
     *
     * @param event The tick event
     */
    public void onPostTick(TickEvent event) {
        // Only process on IN (post-tick) events
        if (event.type() != TickEvent.Type.IN) {
            return;
        }

        Minecraft mc = this.maestro.getPlayerContext().minecraft();

        // Try to open LAN (if requested and not already open)
        if (lanRequested && !lanOpened) {
            boolean playerReady = mc.player != null;
            boolean connectionReady = mc.player != null && mc.player.connection != null;

            if (playerReady && connectionReady) {
                log.atDebug()
                        .addKeyValue("player", playerReady)
                        .addKeyValue("connection", connectionReady)
                        .log("Attempting LAN opening");

                openWorldToLAN();
                lanRequested = false;
            } else {
                log.atDebug()
                        .addKeyValue("player", playerReady)
                        .addKeyValue("connection", connectionReady)
                        .log("Waiting for player to be ready for LAN opening");
            }
        }

        // Try to start coordinator (if requested and player is ready) - independent of LAN
        if (coordinatorRequested && !coordinatorStarted) {
            boolean playerReady = mc.player != null;

            if (playerReady) {
                log.atDebug()
                        .addKeyValue("player", playerReady)
                        .log("Attempting coordinator startup");

                startCoordinatorServer();
                coordinatorRequested = false;
                coordinatorStarted = true;
            } else {
                log.atDebug()
                        .addKeyValue("player", playerReady)
                        .log("Waiting for player to be ready for coordinator startup");
            }
        }
    }

    private void openWorldToLAN() {
        Minecraft mc = this.maestro.getPlayerContext().minecraft();

        if (mc.getSingleplayerServer() == null) {
            log.atWarn().log("Cannot open LAN - singleplayer server is null");
            return;
        }

        if (mc.getSingleplayerServer().getPort() > 0) {
            log.atDebug().log("LAN already open");
            lanOpened = true;
            return;
        }

        log.atDebug().log("Publishing server to LAN");
        boolean success = mc.getSingleplayerServer().publishServer(GameType.SURVIVAL, false, 25565);

        if (success) {
            int port = mc.getSingleplayerServer().getPort();
            log.atInfo().addKeyValue("lan_port", port).log("World opened to LAN");
            lanOpened = true;
        } else {
            log.atWarn().log("Failed to open world to LAN (publishServer returned false)");
        }
    }

    private void startCoordinatorServer() {
        CoordinationServer server = maestro.getCoordinationServer();

        // Check if already running
        if (server != null && server.isRunning()) {
            log.atDebug().log("Coordinator already running");
            return;
        }

        // Parse goal from environment or use default
        int goal;
        String goalStr = System.getenv("COORDINATOR_GOAL");
        if (goalStr != null && !goalStr.isEmpty()) {
            try {
                goal = Integer.parseInt(goalStr);
                log.atDebug().addKeyValue("goal", goal).log("Parsed coordinator goal");
            } catch (NumberFormatException e) {
                log.atWarn()
                        .addKeyValue("value", goalStr)
                        .log("Invalid COORDINATOR_GOAL, using default");
                goal = 100;
            }
        } else {
            goal = 100;
            log.atDebug().addKeyValue("goal", goal).log("Using default goal");
        }

        // Create and start server
        if (server == null) {
            server = new CoordinationServer();
            maestro.setCoordinationServer(server);
            log.atDebug().log("Created new CoordinationServer instance");
        }

        log.atDebug()
                .addKeyValue("port", 9090)
                .addKeyValue("goal", goal)
                .log("Starting coordinator server");

        server.start(9090, goal);

        log.atInfo()
                .addKeyValue("port", 9090)
                .addKeyValue("goal", goal)
                .log("Coordinator auto-started");
    }

    private boolean shouldAutoStartCoordinator() {
        return "true".equalsIgnoreCase(System.getenv("AUTOSTART_COORDINATOR"));
    }
}
