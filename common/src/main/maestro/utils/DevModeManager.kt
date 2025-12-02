package maestro.utils

import maestro.Agent
import maestro.api.event.events.TickEvent
import maestro.api.utils.MaestroLogger
import maestro.coordination.CoordinationServer
import net.minecraft.world.level.GameType
import org.slf4j.Logger

/**
 * Manages development mode features: auto-open LAN, auto-start coordinator server.
 *
 * Configuration via environment variables:
 * - AUTOSTART_COORDINATOR - Auto-open LAN and start coordinator server
 * - COORDINATOR_GOAL - Goal value for coordinator server (default: 100)
 *
 * Note: World autoloading is handled via --quickPlaySingleplayer command-line argument
 */
class DevModeManager(
    private val maestro: Agent,
) {
    private var lanOpened = false
    private var lanRequested = false
    private var coordinatorStarted = false
    private var coordinatorRequested = false

    /** Called when a world has finished loading. Marks LAN and coordinator for startup. */
    fun onWorldLoad() {
        if (shouldAutoStartCoordinator()) {
            log.atDebug().log("Auto-start enabled")

            if (!lanOpened) {
                lanRequested = true
                log.atDebug().log("LAN opening requested")
            }

            if (!coordinatorStarted) {
                coordinatorRequested = true
                log.atDebug().log("Coordinator startup requested")
            }
        } else {
            log
                .atDebug()
                .addKeyValue("env_var", System.getenv("AUTOSTART_COORDINATOR"))
                .log("Auto-start disabled")
        }
    }

    /**
     * Called on post-tick to open LAN and start coordinator server.
     *
     * @param event The tick event
     */
    fun onPostTick(event: TickEvent) {
        // Only process on IN (post-tick) events
        if (event.type != TickEvent.Type.IN) {
            return
        }

        val mc = maestro.playerContext.minecraft()

        // Try to open LAN (if requested and not already open)
        if (lanRequested && !lanOpened) {
            val player = mc.player
            val playerReady = player != null
            val connectionReady = player != null && player.connection != null

            if (playerReady && connectionReady) {
                log
                    .atDebug()
                    .addKeyValue("player", playerReady)
                    .addKeyValue("connection", connectionReady)
                    .log("Attempting LAN opening")

                openWorldToLAN()
                lanRequested = false
            } else {
                log
                    .atDebug()
                    .addKeyValue("player", playerReady)
                    .addKeyValue("connection", connectionReady)
                    .log("Waiting for player to be ready for LAN opening")
            }
        }

        // Try to start coordinator (if requested and player is ready) - independent of LAN
        if (coordinatorRequested && !coordinatorStarted) {
            val playerReady = mc.player != null

            if (playerReady) {
                log
                    .atDebug()
                    .addKeyValue("player", playerReady)
                    .log("Attempting coordinator startup")

                startCoordinatorServer()
                coordinatorRequested = false
                coordinatorStarted = true
            } else {
                log
                    .atDebug()
                    .addKeyValue("player", playerReady)
                    .log("Waiting for player to be ready for coordinator startup")
            }
        }
    }

    private fun openWorldToLAN() {
        val mc = maestro.playerContext.minecraft()

        val server = mc.singleplayerServer
        if (server == null) {
            log.atWarn().log("Cannot open LAN - singleplayer server is null")
            return
        }

        if (server.port > 0) {
            log.atDebug().log("LAN already open")
            lanOpened = true
            return
        }

        log.atDebug().log("Publishing server to LAN")
        val success = server.publishServer(GameType.SURVIVAL, false, 25565)

        if (success) {
            val port = server.port
            log.atInfo().addKeyValue("lan_port", port).log("World opened to LAN")
            lanOpened = true
        } else {
            log.atWarn().log("Failed to open world to LAN (publish-server returned false)")
        }
    }

    private fun startCoordinatorServer() {
        var server = maestro.coordinationServer

        // Check if already running
        if (server != null && server.isRunning()) {
            log.atDebug().log("Coordinator already running")
            return
        }

        // Parse goal from environment or use default
        val goalStr = System.getenv("COORDINATOR_GOAL")
        val goal =
            if (!goalStr.isNullOrEmpty()) {
                goalStr.toIntOrNull()?.also {
                    log.atDebug().addKeyValue("goal", it).log("Parsed coordinator goal")
                } ?: run {
                    log
                        .atWarn()
                        .addKeyValue("value", goalStr)
                        .log("Invalid COORDINATOR_GOAL, using default")
                    100
                }
            } else {
                log.atDebug().addKeyValue("goal", 100).log("Using default goal")
                100
            }

        // Create and start server
        if (server == null) {
            server = CoordinationServer()
            maestro.coordinationServer = server
            log.atDebug().log("Created new coordination-server instance")
        }

        log
            .atDebug()
            .addKeyValue("port", 9090)
            .addKeyValue("goal", goal)
            .log("Starting coordinator server")

        server.start(9090, goal)

        log
            .atInfo()
            .addKeyValue("port", 9090)
            .addKeyValue("goal", goal)
            .log("Coordinator auto-started")
    }

    private fun shouldAutoStartCoordinator(): Boolean = "true".equals(System.getenv("AUTOSTART_COORDINATOR"), ignoreCase = true)

    companion object {
        private val log: Logger = MaestroLogger.get("dev")
    }
}
