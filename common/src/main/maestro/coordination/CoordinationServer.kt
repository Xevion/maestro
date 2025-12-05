package maestro.coordination

import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import maestro.api.utils.Loggers
import maestro.api.utils.PackedBlockPos
import maestro.api.utils.format
import maestro.coordination.proto.Claim
import maestro.coordination.proto.ClaimAreaRequest
import maestro.coordination.proto.ClaimAreaResponse
import maestro.coordination.proto.ConnectRequest
import maestro.coordination.proto.ConnectResponse
import maestro.coordination.proto.CoordinationGrpc
import maestro.coordination.proto.GoalStatusRequest
import maestro.coordination.proto.GoalStatusResponse
import maestro.coordination.proto.HeartbeatRequest
import maestro.coordination.proto.HeartbeatResponse
import maestro.coordination.proto.Position
import maestro.coordination.proto.ProgressReport
import maestro.coordination.proto.ProgressResponse
import maestro.coordination.proto.ReleaseAreaRequest
import maestro.coordination.proto.ReleaseAreaResponse
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.sqrt

data class AreaClaim(
    val workerId: String,
    val centerX: Int,
    val centerY: Int,
    val centerZ: Int,
    val radius: Double,
    val timestamp: Long,
)

data class WorkerProgress(
    val workerId: String,
    val totalCollected: Int,
    val lastUpdate: Long,
)

class CoordinationServer {
    private val log: Logger = Loggers.get("coord")

    private var server: Server? = null
    private val claims = ConcurrentHashMap<String, AreaClaim>()
    private val workerProgress = ConcurrentHashMap<String, WorkerProgress>()
    private val workerHeartbeats = ConcurrentHashMap<String, Long>()
    private val globalTotal = AtomicInteger(0)
    private var globalGoal: Int = 0
    private var heartbeatMonitorThread: Thread? = null

    @Volatile
    private var running = false

    fun start(
        port: Int = 9090,
        goal: Int = 100,
    ) {
        if (running) {
            log.atWarn().log("Coordinator already running")
            return
        }

        globalGoal = goal
        globalTotal.set(0)
        claims.clear()
        workerProgress.clear()
        workerHeartbeats.clear()

        server =
            ServerBuilder
                .forPort(port)
                .addService(CoordinationServiceImpl())
                .build()
                .start()

        running = true

        log
            .atInfo()
            .addKeyValue("port", port)
            .addKeyValue("goal", goal)
            .log("Coordinator started")

        startHeartbeatMonitor()
    }

    fun stop() {
        if (!running) {
            return
        }

        running = false
        heartbeatMonitorThread?.interrupt()
        heartbeatMonitorThread = null

        server?.shutdown()
        server?.awaitTermination(5, TimeUnit.SECONDS)
        server = null

        claims.clear()
        workerProgress.clear()
        workerHeartbeats.clear()

        log.atInfo().log("Coordinator stopped")
    }

    fun isRunning(): Boolean = running

    fun getGlobalTotal(): Int = globalTotal.get()

    fun getGlobalGoal(): Int = globalGoal

    private fun startHeartbeatMonitor() {
        heartbeatMonitorThread =
            thread(isDaemon = true, name = "CoordinationHeartbeatMonitor") {
                while (running) {
                    try {
                        Thread.sleep(5000) // Check every 5 seconds
                        val now = System.currentTimeMillis()
                        val staleWorkers = mutableListOf<String>()

                        workerHeartbeats.forEach { (workerId, lastHeartbeat) ->
                            if (now - lastHeartbeat > 10000) { // 10 second timeout
                                staleWorkers.add(workerId)
                            }
                        }

                        staleWorkers.forEach { workerId ->
                            log.atWarn().addKeyValue("worker_id", workerId).log("Worker timeout")
                            workerHeartbeats.remove(workerId)
                            claims.remove(workerId)
                        }
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
    }

    private fun claimsOverlap(
        c1x: Int,
        c1y: Int,
        c1z: Int,
        r1: Double,
        c2x: Int,
        c2y: Int,
        c2z: Int,
        r2: Double,
    ): Boolean {
        val dx = c1x - c2x
        val dy = c1y - c2y
        val dz = c1z - c2z
        val distance = sqrt((dx * dx + dy * dy + dz * dz).toDouble())
        return distance < (r1 + r2)
    }

    private inner class CoordinationServiceImpl : CoordinationGrpc.CoordinationImplBase() {
        override fun connect(
            request: ConnectRequest,
            responseObserver: StreamObserver<ConnectResponse>,
        ) {
            val workerId = request.workerId
            val workerName = request.workerName

            workerHeartbeats[workerId] = System.currentTimeMillis()

            log
                .atInfo()
                .addKeyValue("worker_id", workerId)
                .addKeyValue("worker_name", workerName)
                .log("Worker connected")

            val response =
                ConnectResponse
                    .newBuilder()
                    .setAccepted(true)
                    .setMessage("Connected to coordinator")
                    .setGlobalGoal(globalGoal)
                    .setCurrentTotal(globalTotal.get())
                    .build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()
        }

        override fun claimArea(
            request: ClaimAreaRequest,
            responseObserver: StreamObserver<ClaimAreaResponse>,
        ) {
            val workerId = request.workerId
            val center = request.center
            val radius = request.radius

            val conflicts = mutableListOf<Claim>()

            claims.values.forEach { existing ->
                if (existing.workerId != workerId &&
                    claimsOverlap(
                        center.x,
                        center.y,
                        center.z,
                        radius,
                        existing.centerX,
                        existing.centerY,
                        existing.centerZ,
                        existing.radius,
                    )
                ) {
                    conflicts.add(
                        Claim
                            .newBuilder()
                            .setWorkerId(existing.workerId)
                            .setCenter(
                                Position
                                    .newBuilder()
                                    .setX(existing.centerX)
                                    .setY(existing.centerY)
                                    .setZ(existing.centerZ)
                                    .build(),
                            ).setRadius(existing.radius)
                            .setTimestamp(existing.timestamp)
                            .build(),
                    )
                }
            }

            val granted = conflicts.isEmpty()
            val reason =
                if (granted) {
                    "Claim granted"
                } else {
                    "Area overlaps with ${conflicts.size} existing claim(s)"
                }

            if (granted) {
                claims[workerId] =
                    AreaClaim(
                        workerId,
                        center.x,
                        center.y,
                        center.z,
                        radius,
                        System.currentTimeMillis(),
                    )

                log
                    .atDebug()
                    .addKeyValue("worker_id", workerId)
                    .addKeyValue("center", PackedBlockPos(center.x, center.y, center.z).format())
                    .addKeyValue("radius", radius)
                    .log("Area claimed")
            } else {
                log
                    .atDebug()
                    .addKeyValue("worker_id", workerId)
                    .addKeyValue("conflicts", conflicts.size)
                    .log("Claim denied")
            }

            val response =
                ClaimAreaResponse
                    .newBuilder()
                    .setGranted(granted)
                    .setReason(reason)
                    .addAllConflictingClaims(conflicts)
                    .build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()
        }

        override fun releaseArea(
            request: ReleaseAreaRequest,
            responseObserver: StreamObserver<ReleaseAreaResponse>,
        ) {
            val workerId = request.workerId
            val removed = claims.remove(workerId) != null

            if (removed) {
                log.atDebug().addKeyValue("worker_id", workerId).log("Area released")
            }

            val response = ReleaseAreaResponse.newBuilder().setSuccess(removed).build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()
        }

        override fun reportProgress(
            request: ProgressReport,
            responseObserver: StreamObserver<ProgressResponse>,
        ) {
            val workerId = request.workerId
            val resourceType = request.resourceType
            val quantity = request.quantity
            val totalCollected = request.totalCollected

            workerProgress[workerId] =
                WorkerProgress(workerId, totalCollected, System.currentTimeMillis())

            val newTotal = workerProgress.values.sumOf { it.totalCollected }
            globalTotal.set(newTotal)

            val goalComplete = newTotal >= globalGoal

            log
                .atDebug()
                .addKeyValue("worker_id", workerId)
                .addKeyValue("resource", resourceType)
                .addKeyValue("quantity", quantity)
                .addKeyValue("worker_total", totalCollected)
                .addKeyValue("global_total", newTotal)
                .addKeyValue("global_goal", globalGoal)
                .log("Progress reported")

            if (goalComplete) {
                log
                    .atInfo()
                    .addKeyValue("total", newTotal)
                    .addKeyValue("goal", globalGoal)
                    .log("Global goal complete")
            }

            val response =
                ProgressResponse
                    .newBuilder()
                    .setGoalComplete(goalComplete)
                    .setGlobalTotal(newTotal)
                    .setGlobalGoal(globalGoal)
                    .build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()
        }

        override fun checkGoalStatus(
            request: GoalStatusRequest,
            responseObserver: StreamObserver<GoalStatusResponse>,
        ) {
            val total = globalTotal.get()
            val complete = total >= globalGoal

            val response =
                GoalStatusResponse
                    .newBuilder()
                    .setGoalComplete(complete)
                    .setGlobalTotal(total)
                    .setGlobalGoal(globalGoal)
                    .build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()
        }

        override fun heartbeat(
            request: HeartbeatRequest,
            responseObserver: StreamObserver<HeartbeatResponse>,
        ) {
            val workerId = request.workerId
            workerHeartbeats[workerId] = System.currentTimeMillis()

            val response =
                HeartbeatResponse
                    .newBuilder()
                    .setAcknowledged(true)
                    .setServerTime(System.currentTimeMillis())
                    .build()

            responseObserver.onNext(response)
            responseObserver.onCompleted()
        }
    }
}
