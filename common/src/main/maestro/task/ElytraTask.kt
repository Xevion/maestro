package maestro.task

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import maestro.Agent
import maestro.api.event.events.BlockChangeEvent
import maestro.api.event.events.ChunkEvent
import maestro.api.event.events.PacketEvent
import maestro.api.event.events.RenderEvent
import maestro.api.event.events.TickEvent
import maestro.api.event.events.WorldEvent
import maestro.api.event.events.type.EventState
import maestro.api.event.listener.AbstractGameEventListener
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.goals.GoalBlock
import maestro.api.pathing.goals.GoalXZ
import maestro.api.pathing.goals.GoalYLevel
import maestro.api.pathing.movement.ActionCosts.COST_INF
import maestro.api.task.ITask
import maestro.api.task.PathingCommand
import maestro.api.task.PathingCommandType
import maestro.api.utils.Loggers
import maestro.api.utils.PackedBlockPos
import maestro.api.utils.Rotation
import maestro.api.utils.RotationUtils
import maestro.input.Input
import maestro.pathing.PathingCommandContext
import maestro.pathing.movement.CalculationContext
import maestro.task.elytra.ElytraBehavior
import maestro.task.elytra.NetherPathfinderContext
import maestro.task.elytra.NullElytraTask
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import org.slf4j.Logger
import java.util.PriorityQueue

class ElytraTask private constructor(
    agent: Agent,
) : TaskHelper(agent),
    ITask,
    AbstractGameEventListener {
    @JvmField
    var state: State? = null
    private var goingToLandingSpot = false
    private var landingSpot: PackedBlockPos? = null
    private var reachedGoal = false
    private var goal: Goal? = null
    private var behavior: ElytraBehavior? = null
    private var predictingTerrain = false

    init {
        agent.gameEventHandler.registerEventListener(this)
    }

    override fun onLostControl() {
        this.state = State.START_FLYING
        this.goingToLandingSpot = false
        this.landingSpot = null
        this.reachedGoal = false
        this.goal = null
        destroyBehaviorAsync()
    }

    override fun isActive(): Boolean = this.behavior != null

    fun resetState() {
        val destination = this.currentDestination()
        this.onLostControl()
        if (destination != null) {
            this.pathTo(destination)
            this.repackChunks()
        }
    }

    override fun onTick(
        calcFailed: Boolean,
        isSafeToCancel: Boolean,
    ): PathingCommand {
        val currentBehavior = behavior ?: return PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL)

        val seedSetting =
            Agent
                .getPrimaryAgent()
                .settings.elytraNetherSeed.value
        if (seedSetting != currentBehavior.context.seed) {
            log
                .atInfo()
                .addKeyValue("previous_seed", currentBehavior.context.seed)
                .addKeyValue("new_seed", seedSetting)
                .log("Nether seed changed, recalculating path")
            this.resetState()
        }
        if (predictingTerrain !=
            Agent
                .getPrimaryAgent()
                .settings.elytraPredictTerrain.value
        ) {
            log
                .atInfo()
                .addKeyValue("setting", "elytraPredictTerrain")
                .addKeyValue(
                    "new_value",
                    Agent
                        .getPrimaryAgent()
                        .settings.elytraPredictTerrain.value,
                ).log("Setting changed, recalculating path")
            predictingTerrain =
                Agent
                    .getPrimaryAgent()
                    .settings.elytraPredictTerrain.value
            this.resetState()
        }

        currentBehavior.onTick()

        if (calcFailed) {
            onLostControl()
            log.atWarn().log("Failed to compute path for elytra auto-jump")
            return PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL)
        }

        var safetyLanding = false
        if (ctx.player().isFallFlying && shouldLandForSafety()) {
            if (Agent
                    .getPrimaryAgent()
                    .settings.elytraAllowEmergencyLand.value
            ) {
                log
                    .atInfo()
                    .addKeyValue("reason", "low_resources")
                    .log("Emergency landing initiated")
                safetyLanding = true
            } else {
                log
                    .atInfo()
                    .addKeyValue("setting", "elytraAllowEmergencyLand")
                    .addKeyValue("value", false)
                    .log("Low resources but continuing due to setting")
            }
        }
        if (ctx.player().isFallFlying &&
            this.state != State.LANDING &&
            (currentBehavior.pathManager.isComplete || safetyLanding)
        ) {
            val last = currentBehavior.pathManager.path.last()
            if (last != null &&
                (ctx.player().position().distanceToSqr(last.toBlockPos().center) < (48 * 48) || safetyLanding) &&
                (!goingToLandingSpot || (safetyLanding && this.landingSpot == null))
            ) {
                log.atInfo().log("Path complete, picking landing spot")
                val landingSpotFound = findSafeLandingSpot(ctx.playerFeet())
                if (landingSpotFound != null) {
                    this.pathTo0(landingSpotFound.toBlockPos(), true)
                    this.landingSpot = landingSpotFound
                }
                this.goingToLandingSpot = true
            }

            if (last != null && ctx.player().position().distanceToSqr(last.toBlockPos().center) < 1) {
                if (Agent
                        .getPrimaryAgent()
                        .settings.notificationOnPathComplete.value &&
                    !reachedGoal
                ) {
                    logNotification("Pathing complete", false)
                }
                if (Agent
                        .getPrimaryAgent()
                        .settings.disconnectOnArrival.value &&
                    !reachedGoal
                ) {
                    this.onLostControl()
                    ctx.world().disconnect()
                    return PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL)
                }
                reachedGoal = true

                if (this.goingToLandingSpot) {
                    this.state = State.LANDING
                    log
                        .atInfo()
                        .addKeyValue("landing_spot", landingSpot)
                        .log("Above landing spot, initiating landing")
                }
            }
        }

        if (this.state == State.LANDING) {
            val endPos = this.landingSpot ?: currentBehavior.pathManager.path.last()
            if (ctx.player().isFallFlying && endPos != null) {
                val from = ctx.player().position()
                val to = Vec3(endPos.x + 0.5, from.y, endPos.z + 0.5)
                val rotation = RotationUtils.calcRotationFromVec3d(from, to, ctx.playerRotations())
                this@ElytraTask.agent.lookBehavior.updateTarget(Rotation(rotation.yaw, 0f), false)

                if (ctx.player().position().y < endPos.y - LANDING_COLUMN_HEIGHT) {
                    log
                        .atWarn()
                        .addKeyValue("landing_spot", endPos)
                        .log("Landing spot too low, selecting new spot")
                    landingSpotIsBad(endPos)
                }
            }
        }

        if (ctx.player().isFallFlying) {
            currentBehavior.landingMode = this.state == State.LANDING
            this.goal = null
            this@ElytraTask.agent.inputOverrideHandler.clearAllKeys()
            currentBehavior.tick()
            return PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL)
        } else if (this.state == State.LANDING) {
            if (ctx.playerMotion().multiply(1.0, 0.0, 1.0).length() > 0.001) {
                log.atInfo().log("Landed, waiting for velocity to stabilize")
                this@ElytraTask.agent.inputOverrideHandler.setInputForceState(Input.SNEAK, true)
                return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
            }
            log.atInfo().log("Elytra path complete")
            this@ElytraTask.agent.inputOverrideHandler.clearAllKeys()
            this.onLostControl()
            return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
        }

        if (this.state == State.FLYING || this.state == State.START_FLYING) {
            this.state =
                if (ctx.player().onGround() &&
                    Agent
                        .getPrimaryAgent()
                        .settings.elytraAutoJump.value
                ) {
                    State.LOCATE_JUMP
                } else {
                    State.START_FLYING
                }
        }

        if (this.state == State.LOCATE_JUMP) {
            if (shouldLandForSafety()) {
                log
                    .atWarn()
                    .addKeyValue("reason", "insufficient_resources")
                    .log("Not taking off due to low elytra durability or fireworks")
                onLostControl()
                return PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL)
            }
            if (this.goal == null) {
                this.goal = GoalYLevel(31)
            }
            val executor = this@ElytraTask.agent.pathingBehavior.getCurrent()
            if (executor != null && executor.path.goal == this.goal) {
                // TODO: Re-enable after MovementFall is converted to Kotlin
                // val fall =
                //     executor.path
                //         .movements()
                //         .filterIsInstance<MovementFall>()
                //         .firstOrNull()
                //
                // if (fall != null) {
                //     val from =
                //         PackedBlockPos(
                //             (fall.src.x + fall.dest.x) / 2,
                //             (fall.src.y + fall.dest.y) / 2,
                //             (fall.src.z + fall.dest.z) / 2,
                //         )
                //     currentBehavior.pathManager
                //         .pathToDestination(from.toBlockPos())
                //         .whenComplete { _, ex ->
                //             if (ex == null) {
                //                 this.state = State.GET_TO_JUMP
                //                 return@whenComplete
                //             }
                //             onLostControl()
                //         }
                //     this.state = State.PAUSE
                // } else {
                onLostControl()
                log.atWarn().log("Failed to compute walking path to jump point (MovementFall temporarily disabled)")
                return PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL)
                // }
            }
            return PathingCommandContext(
                this.goal!!,
                PathingCommandType.SET_GOAL_AND_PAUSE,
                WalkOffCalculationContext(this@ElytraTask.agent),
            )
        }

        if (this.state == State.PAUSE) {
            return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
        }

        if (this.state == State.GET_TO_JUMP) {
            val executor = this@ElytraTask.agent.pathingBehavior.getCurrent()
            val canStartFlying =
                ctx.player().deltaMovement.y < -0.377 &&
                    !isSafeToCancel &&
                    executor != null
            // TODO: Re-enable after MovementFall is converted to Kotlin
            // && executor.path.movements()[executor.position] is MovementFall

            if (canStartFlying) {
                this.state = State.START_FLYING
            } else {
                return PathingCommand(null, PathingCommandType.SET_GOAL_AND_PATH)
            }
        }

        if (this.state == State.START_FLYING) {
            if (!isSafeToCancel) {
                this@ElytraTask.agent.pathingBehavior.secretInternalSegmentCancel()
            }
            this@ElytraTask.agent.inputOverrideHandler.clearAllKeys()
            if (ctx.player().deltaMovement.y < -0.377) {
                this@ElytraTask.agent.inputOverrideHandler.setInputForceState(Input.JUMP, true)
            }
        }
        return PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL)
    }

    fun landingSpotIsBad(endPos: PackedBlockPos) {
        badLandingSpots.add(endPos)
        goingToLandingSpot = false
        this.landingSpot = null
        this.state = State.FLYING
    }

    private fun destroyBehaviorAsync() {
        val behaviorToDestroy = this.behavior
        if (behaviorToDestroy != null) {
            this.behavior = null
            Agent.getExecutor().execute { behaviorToDestroy.destroy() }
        }
    }

    override fun priority(): Double = 0.0

    override fun displayName0(): String = "Elytra - ${this.state?.description ?: "Unknown"}"

    fun repackChunks() {
        this.behavior?.repackChunks()
    }

    fun currentDestination(): BlockPos? = this.behavior?.destination?.toBlockPos()

    fun pathTo(destination: BlockPos) {
        this.pathTo0(destination, false)
    }

    private fun pathTo0(
        destination: BlockPos,
        appendDestination: Boolean,
    ) {
        if (ctx.player() == null || ctx.player().level().dimension() != Level.NETHER) {
            return
        }
        this.onLostControl()
        this.predictingTerrain =
            Agent
                .getPrimaryAgent()
                .settings.elytraPredictTerrain.value
        this.behavior = ElytraBehavior(this.agent, this, destination, appendDestination)
        if (ctx.world() != null) {
            this.behavior?.repackChunks()
        }
        this.behavior?.pathTo()
    }

    fun pathTo(iGoal: Goal) {
        val x: Int
        val y: Int
        val z: Int
        when (iGoal) {
            is GoalXZ -> {
                x = iGoal.x
                y = 64
                z = iGoal.z
            }

            is GoalBlock -> {
                x = iGoal.x
                y = iGoal.y
                z = iGoal.z
            }

            else -> throw IllegalArgumentException("The goal must be a GoalXZ or GoalBlock")
        }
        if (y !in 1..<128) {
            throw IllegalArgumentException("The y of the goal is not between 0 and 128")
        }
        this.pathTo(BlockPos(x, y, z))
    }

    private fun shouldLandForSafety(): Boolean {
        val chest = ctx.player().getItemBySlot(EquipmentSlot.CHEST)
        if (chest.item != Items.ELYTRA ||
            chest.maxDamage - chest.damageValue <
            Agent
                .getPrimaryAgent()
                .settings.elytraMinimumDurability.value
        ) {
            return true
        }

        val inv = ctx.player().inventory.items
        var qty = 0
        for (i in 0 until 36) {
            if (ElytraBehavior.isFireworks(inv[i])) {
                qty += inv[i].count
            }
        }
        return qty <=
            Agent
                .getPrimaryAgent()
                .settings.elytraMinFireworksBeforeLanding.value
    }

    fun isLoaded(): Boolean = true

    fun isSafeToCancel(): Boolean = !this.isActive || !(this.state == State.FLYING || this.state == State.START_FLYING)

    enum class State(
        val description: String,
    ) {
        LOCATE_JUMP("Finding spot to jump off"),
        PAUSE("Waiting for elytra path"),
        GET_TO_JUMP("Walking to takeoff"),
        START_FLYING("Begin flying"),
        FLYING("Flying"),
        LANDING("Landing"),
    }

    override fun onRenderPass(event: RenderEvent) {
        this.behavior?.onRenderPass(event)
    }

    override fun onWorldEvent(event: WorldEvent) {
        if (event.world != null && event.state == EventState.POST) {
            destroyBehaviorAsync()
        }
    }

    override fun onChunkEvent(event: ChunkEvent) {
        this.behavior?.onChunkEvent(event)
    }

    override fun onBlockChange(event: BlockChangeEvent) {
        this.behavior?.onBlockChange(event)
    }

    override fun onReceivePacket(event: PacketEvent) {
        this.behavior?.onReceivePacket(event)
    }

    override fun onPostTick(event: TickEvent) {
        val procThisTick =
            this@ElytraTask
                .agent.pathingControlManager
                .mostRecentInControl()
                .orElse(null)
        if (this.behavior != null && procThisTick === this) {
            this.behavior?.onPostTick(event)
        }
    }

    /** Custom calculation context which makes the player fall into lava */
    class WalkOffCalculationContext(
        agent: Agent,
    ) : CalculationContext(agent, true) {
        init {
            this.allowFallIntoLava = true
            this.minFallHeight = 8
            this.maxFallHeightNoWater = 10000
        }

        override fun costOfPlacingAt(
            x: Int,
            y: Int,
            z: Int,
            current: BlockState,
        ): Double = COST_INF

        override fun breakCostMultiplierAt(
            x: Int,
            y: Int,
            z: Int,
            current: BlockState,
        ): Double = COST_INF

        override fun placeBucketCost(): Double = COST_INF
    }

    private fun isSafeBlock(block: Block): Boolean =
        block == Blocks.NETHERRACK ||
            block == Blocks.GRAVEL ||
            (
                block == Blocks.NETHER_BRICKS &&
                    Agent
                        .getPrimaryAgent()
                        .settings.elytraAllowLandOnNetherFortress.value
            )

    private fun isSafeBlock(pos: BlockPos): Boolean = isSafeBlock(ctx.world().getBlockState(pos).block)

    private fun isAtEdge(pos: BlockPos): Boolean =
        !isSafeBlock(pos.north()) ||
            !isSafeBlock(pos.south()) ||
            !isSafeBlock(pos.east()) ||
            !isSafeBlock(pos.west()) ||
            !isSafeBlock(pos.north().west()) ||
            !isSafeBlock(pos.north().east()) ||
            !isSafeBlock(pos.south().west()) ||
            !isSafeBlock(pos.south().east())

    private fun isColumnAir(
        landingSpot: BlockPos,
        minHeight: Int,
    ): Boolean {
        val mut = BlockPos.MutableBlockPos(landingSpot.x, landingSpot.y, landingSpot.z)
        val maxY = mut.y + minHeight
        for (y in mut.y + 1..maxY) {
            mut.set(mut.x, y, mut.z)
            if (ctx.world().getBlockState(mut).block !is AirBlock) {
                return false
            }
        }
        return true
    }

    private fun hasAirBubble(pos: BlockPos): Boolean {
        val radius = 4
        val mut = BlockPos.MutableBlockPos()
        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    mut.set(pos.x + x, pos.y + y, pos.z + z)
                    if (ctx.world().getBlockState(mut).block !is AirBlock) {
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun checkLandingSpot(
        pos: BlockPos,
        checkedSpots: LongOpenHashSet,
    ): PackedBlockPos? {
        val mut = BlockPos.MutableBlockPos(pos.x, pos.y, pos.z)
        while (mut.y >= 0) {
            if (checkedSpots.contains(mut.asLong())) {
                return null
            }
            checkedSpots.add(mut.asLong())
            val block = ctx.world().getBlockState(mut).block

            if (isSafeBlock(block)) {
                if (!isAtEdge(mut)) {
                    return PackedBlockPos(mut)
                }
                return null
            } else if (block != Blocks.AIR) {
                return null
            }
            mut.set(mut.x, mut.y - 1, mut.z)
        }
        return null
    }

    private fun findSafeLandingSpot(start: PackedBlockPos): PackedBlockPos? {
        val queue =
            PriorityQueue(
                Comparator
                    .comparingInt<PackedBlockPos> { pos ->
                        (pos.x - start.x) * (pos.x - start.x) +
                            (pos.z - start.z) * (pos.z - start.z)
                    }.thenComparingInt { pos -> -pos.y },
            )
        val visited = mutableSetOf<PackedBlockPos>()
        val checkedPositions = LongOpenHashSet()
        queue.add(start)

        while (queue.isNotEmpty()) {
            val pos = queue.poll()
            if (ctx.world().isLoaded(pos.toBlockPos()) &&
                isInBounds(pos.toBlockPos()) &&
                ctx.world().getBlockState(pos.toBlockPos()).block == Blocks.AIR
            ) {
                val actualLandingSpot = checkLandingSpot(pos.toBlockPos(), checkedPositions)
                if (actualLandingSpot != null &&
                    isColumnAir(actualLandingSpot.toBlockPos(), LANDING_COLUMN_HEIGHT) &&
                    hasAirBubble(actualLandingSpot.above(LANDING_COLUMN_HEIGHT).toBlockPos()) &&
                    !badLandingSpots.contains(actualLandingSpot.above(LANDING_COLUMN_HEIGHT))
                ) {
                    return actualLandingSpot.above(LANDING_COLUMN_HEIGHT)
                }
                if (visited.add(pos.north())) queue.add(pos.north())
                if (visited.add(pos.east())) queue.add(pos.east())
                if (visited.add(pos.south())) queue.add(pos.south())
                if (visited.add(pos.west())) queue.add(pos.west())
                if (visited.add(pos.above())) queue.add(pos.above())
                if (visited.add(pos.below())) queue.add(pos.below())
            }
        }
        return null
    }

    companion object {
        private val log: Logger = Loggers.Path.get()

        private const val LANDING_COLUMN_HEIGHT = 15

        @JvmStatic
        fun create(agent: Agent): TaskHelper =
            if (NetherPathfinderContext.isSupported()) {
                ElytraTask(agent)
            } else {
                NullElytraTask(agent)
            }

        private fun isInBounds(pos: BlockPos): Boolean = pos.y in 0..<128
    }

    private val badLandingSpots: MutableSet<PackedBlockPos> = mutableSetOf()
}
