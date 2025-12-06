package maestro.coordination

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import maestro.coordination.proto.ClaimAreaRequest
import maestro.coordination.proto.ConnectRequest
import maestro.coordination.proto.CoordinationGrpc
import maestro.coordination.proto.GoalStatusRequest
import maestro.coordination.proto.HeartbeatRequest
import maestro.coordination.proto.Position
import maestro.coordination.proto.ProgressReport
import maestro.coordination.proto.ReleaseAreaRequest
import maestro.utils.Loggers
import maestro.utils.format
import net.minecraft.core.BlockPos
import org.slf4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class CoordinationClient(
    private val workerId: String,
    private val workerName: String,
) {
    private val log: Logger = Loggers.Coord.get()

    private var channel: ManagedChannel? = null
    private var stub: CoordinationGrpc.CoordinationBlockingStub? = null
    private var heartbeatThread: Thread? = null

    @Volatile
    var connected = false
        private set

    var currentClaim: BlockPos? = null
        private set

    fun isConnected(): Boolean = connected

    fun connect(
        host: String = "localhost",
        port: Int = 9090,
    ): Boolean {
        if (connected) {
            log.atWarn().log("Already connected")
            return true
        }

        try {
            channel =
                ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()

            stub = CoordinationGrpc.newBlockingStub(channel)

            val request =
                ConnectRequest
                    .newBuilder()
                    .setWorkerId(workerId)
                    .setWorkerName(workerName)
                    .build()

            val response = stub!!.connect(request)

            if (response.accepted) {
                connected = true
                startHeartbeat()

                log
                    .atInfo()
                    .addKeyValue("host", host)
                    .addKeyValue("port", port)
                    .addKeyValue("global_goal", response.globalGoal)
                    .addKeyValue("current_total", response.currentTotal)
                    .log("Connected to coordinator")

                return true
            } else {
                log
                    .atWarn()
                    .addKeyValue("message", response.message)
                    .log("Connection rejected")
                disconnect()
                return false
            }
        } catch (e: StatusRuntimeException) {
            log
                .atError()
                .setCause(e)
                .addKeyValue("host", host)
                .addKeyValue("port", port)
                .log("Connection failed")
            disconnect()
            return false
        }
    }

    fun disconnect() {
        if (!connected) {
            return
        }

        connected = false
        heartbeatThread?.interrupt()
        heartbeatThread = null

        if (currentClaim != null) {
            try {
                releaseArea(currentClaim!!)
            } catch (e: Exception) {
                log.atWarn().setCause(e).log("Failed to release area on disconnect")
            }
        }

        channel?.shutdown()
        try {
            channel?.awaitTermination(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            channel?.shutdownNow()
        }

        channel = null
        stub = null

        log.atInfo().log("Disconnected from coordinator")
    }

    fun claimArea(
        center: BlockPos,
        radius: Double,
    ): Boolean {
        if (!connected) {
            log.atWarn().log("Cannot claim area - not connected")
            return false
        }

        try {
            val request =
                ClaimAreaRequest
                    .newBuilder()
                    .setWorkerId(workerId)
                    .setCenter(
                        Position
                            .newBuilder()
                            .setX(center.x)
                            .setY(center.y)
                            .setZ(center.z)
                            .build(),
                    ).setRadius(radius)
                    .build()

            val response = stub!!.claimArea(request)

            if (response.granted) {
                currentClaim = center

                log
                    .atInfo()
                    .addKeyValue("center", center.format())
                    .addKeyValue("radius", radius)
                    .log("Area claim granted")

                return true
            } else {
                log
                    .atWarn()
                    .addKeyValue("reason", response.reason)
                    .addKeyValue("conflicts", response.conflictingClaimsList.size)
                    .log("Area claim denied")

                return false
            }
        } catch (e: StatusRuntimeException) {
            handleDisconnect(e)
            return false
        }
    }

    fun releaseArea(center: BlockPos): Boolean {
        if (!connected) {
            return false
        }

        try {
            val request =
                ReleaseAreaRequest
                    .newBuilder()
                    .setWorkerId(workerId)
                    .setCenter(
                        Position
                            .newBuilder()
                            .setX(center.x)
                            .setY(center.y)
                            .setZ(center.z)
                            .build(),
                    ).build()

            val response = stub!!.releaseArea(request)

            if (response.success) {
                currentClaim = null

                log
                    .atDebug()
                    .addKeyValue("center", center.format())
                    .log("Area released")
            }

            return response.success
        } catch (e: StatusRuntimeException) {
            handleDisconnect(e)
            return false
        }
    }

    fun reportProgress(
        resourceType: String,
        quantity: Int,
        totalCollected: Int,
    ): Boolean {
        if (!connected) {
            return false
        }

        try {
            val request =
                ProgressReport
                    .newBuilder()
                    .setWorkerId(workerId)
                    .setResourceType(resourceType)
                    .setQuantity(quantity)
                    .setTotalCollected(totalCollected)
                    .build()

            val response = stub!!.reportProgress(request)

            log
                .atDebug()
                .addKeyValue("resource", resourceType)
                .addKeyValue("quantity", quantity)
                .addKeyValue("total", totalCollected)
                .addKeyValue("global_total", response.globalTotal)
                .addKeyValue("global_goal", response.globalGoal)
                .log("Progress reported")

            return response.goalComplete
        } catch (e: StatusRuntimeException) {
            handleDisconnect(e)
            return false
        }
    }

    fun checkGoalStatus(): Pair<Boolean, Int> {
        if (!connected) {
            return Pair(false, 0)
        }

        try {
            val request = GoalStatusRequest.newBuilder().setWorkerId(workerId).build()

            val response = stub!!.checkGoalStatus(request)

            return Pair(response.goalComplete, response.globalTotal)
        } catch (e: StatusRuntimeException) {
            handleDisconnect(e)
            return Pair(false, 0)
        }
    }

    private fun startHeartbeat() {
        heartbeatThread =
            thread(isDaemon = true, name = "CoordinationHeartbeat-$workerId") {
                while (connected) {
                    try {
                        Thread.sleep(5000) // Send heartbeat every 5 seconds

                        if (!connected) break

                        val request =
                            HeartbeatRequest
                                .newBuilder()
                                .setWorkerId(workerId)
                                .setTimestamp(System.currentTimeMillis())
                                .build()

                        stub?.heartbeat(request)
                    } catch (e: InterruptedException) {
                        break
                    } catch (e: StatusRuntimeException) {
                        handleDisconnect(e)
                        break
                    }
                }
            }
    }

    private fun handleDisconnect(e: StatusRuntimeException) {
        if (connected) {
            log
                .atWarn()
                .setCause(e)
                .addKeyValue("status", e.status.code.name)
                .log("Coordinator connection lost")

            connected = false
            currentClaim = null
        }
    }
}
