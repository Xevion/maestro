package maestro.behavior

import maestro.Agent
import maestro.api.Settings
import maestro.api.behavior.ILookBehavior
import maestro.api.behavior.look.IAimProcessor
import maestro.api.behavior.look.ITickableAimProcessor
import maestro.api.event.events.PacketEvent
import maestro.api.event.events.PlayerUpdateEvent
import maestro.api.event.events.RotationMoveEvent
import maestro.api.event.events.TickEvent
import maestro.api.event.events.WorldEvent
import maestro.api.event.events.type.EventState
import maestro.api.utils.IPlayerContext
import maestro.api.utils.Rotation
import maestro.behavior.look.ForkableRandom
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import java.util.ArrayDeque
import java.util.Deque
import java.util.Optional
import kotlin.math.abs

/**
 * Manages bot look direction and rotation handling.
 *
 * Supports:
 * - Smooth look transitions
 * - Server/client rotation modes
 * - Free look (independent camera control)
 * - Aim processing with randomization
 */
class LookBehavior(
    maestro: Agent,
) : Behavior(maestro),
    ILookBehavior {
    /** The current look target, may be `null`.  */
    private var target: Target? = null

    /**
     * The rotation known to the server. Returned by [effectiveRotation] for use in
     * [IPlayerContext].
     */
    private var serverRotation: Rotation? = null

    /**
     * The last player rotation. Used to restore the player's angle when using free look.
     *
     * @see Settings.freeLook
     */
    private var prevRotation: Rotation? = null

    private val processor: AimProcessor

    private val smoothYawBuffer: Deque<Float> = ArrayDeque()
    private val smoothPitchBuffer: Deque<Float> = ArrayDeque()

    init {
        this.processor = AimProcessor(maestro.getPlayerContext())
    }

    override fun updateTarget(
        rotation: Rotation,
        blockInteract: Boolean,
    ) {
        this.target =
            Target(
                rotation,
                Target.Mode.resolve(ctx, blockInteract),
            )
    }

    override fun getAimProcessor(): IAimProcessor = processor

    override fun onTick(event: TickEvent) {
        if (event.type() == TickEvent.Type.IN) {
            processor.tick()
        }
    }

    override fun onPlayerUpdate(event: PlayerUpdateEvent) {
        val currentTarget = target ?: return

        when (event.getState()) {
            EventState.PRE -> {
                if (currentTarget.mode == Target.Mode.NONE) {
                    // Just return for PRE, we still want to set target to null on POST
                    return
                }

                prevRotation = Rotation(ctx.player().getYRot(), ctx.player().getXRot())
                val actual = processor.peekRotation(currentTarget.rotation)
                ctx.player().setYRot(actual.yaw)
                ctx.player().setXRot(actual.pitch)
            }

            EventState.POST -> {
                // Reset the player's rotations back to their original values
                prevRotation?.let { prev ->
                    smoothYawBuffer.addLast(currentTarget.rotation.yaw)
                    while (smoothYawBuffer.size > Agent.settings().smoothLookTicks.value) {
                        smoothYawBuffer.removeFirst()
                    }

                    smoothPitchBuffer.addLast(currentTarget.rotation.pitch)
                    while (smoothPitchBuffer.size > Agent.settings().smoothLookTicks.value) {
                        smoothPitchBuffer.removeFirst()
                    }

                    when (currentTarget.mode) {
                        Target.Mode.SERVER -> {
                            ctx.player().setYRot(prev.yaw)
                            ctx.player().setXRot(prev.pitch)
                        }

                        Target.Mode.CLIENT -> {
                            val smoothLookEnabled =
                                if (ctx.player().isFallFlying()) {
                                    Agent.settings().elytraSmoothLook.value
                                } else {
                                    Agent.settings().smoothLook.value
                                }

                            if (smoothLookEnabled) {
                                ctx.player().setYRot(
                                    smoothYawBuffer.mapNotNull { it as? Float }.average().toFloat(),
                                )
                                if (ctx.player().isFallFlying()) {
                                    ctx.player().setXRot(
                                        smoothPitchBuffer.mapNotNull { it as? Float }.average().toFloat(),
                                    )
                                }
                            }
                        }

                        Target.Mode.NONE -> {}
                    }

                    this.prevRotation = null
                }

                // The target is done being used for this game tick, so it can be invalidated
                target = null
            }

            else -> {}
        }
    }

    override fun onSendPacket(event: PacketEvent) {
        val packet = event.getPacket() as? ServerboundMovePlayerPacket ?: return

        if (packet is ServerboundMovePlayerPacket.Rot || packet is ServerboundMovePlayerPacket.PosRot) {
            serverRotation = Rotation(packet.getYRot(0.0f), packet.getXRot(0.0f))
        }
    }

    override fun onWorldEvent(event: WorldEvent?) {
        serverRotation = null
        target = null
    }

    fun pig() {
        target?.let { currentTarget ->
            val actual = processor.peekRotation(currentTarget.rotation)
            ctx.player().setYRot(actual.yaw)
        }
    }

    val effectiveRotation: Optional<Rotation>
        get() =
            if (Agent.settings().freeLook.value) {
                Optional.ofNullable(serverRotation)
            } else {
                // If freeLook isn't on, just defer to the player's actual rotations
                Optional.empty()
            }

    override fun onPlayerRotationMove(event: RotationMoveEvent) {
        target?.let { currentTarget ->
            val actual = processor.peekRotation(currentTarget.rotation)
            event.yaw = actual.yaw
            event.pitch = actual.pitch
        }
    }

    private class AimProcessor(
        ctx: IPlayerContext,
    ) : AbstractAimProcessor(ctx) {
        override fun getPrevRotation(): Rotation = ctx.playerRotations()
    }

    private abstract class AbstractAimProcessor : ITickableAimProcessor {
        protected val ctx: IPlayerContext
        private val rand: ForkableRandom
        private var randomYawOffset = 0.0
        private var randomPitchOffset = 0.0

        constructor(ctx: IPlayerContext) {
            this.ctx = ctx
            this.rand = ForkableRandom()
        }

        private constructor(source: AbstractAimProcessor) {
            this.ctx = source.ctx
            this.rand = source.rand.fork()
            this.randomYawOffset = source.randomYawOffset
            this.randomPitchOffset = source.randomPitchOffset
        }

        override fun peekRotation(rotation: Rotation): Rotation {
            val prev = getPrevRotation()

            var desiredYaw = rotation.yaw
            var desiredPitch = rotation.pitch

            // In other words, the target doesn't care about the pitch, so it used
            // playerRotations().getPitch()
            // and it's safe to adjust it to a normal level
            if (desiredPitch == prev.pitch) {
                desiredPitch = nudgeToLevel(desiredPitch)
            }

            desiredYaw += randomYawOffset.toFloat()
            desiredPitch += randomPitchOffset.toFloat()

            return Rotation(
                calculateMouseMove(prev.yaw, desiredYaw),
                calculateMouseMove(prev.pitch, desiredPitch),
            ).clamp()
        }

        override fun tick() {
            // randomLooking
            randomYawOffset = (rand.nextDouble() - 0.5) * Agent.settings().randomLooking.value
            randomPitchOffset = (rand.nextDouble() - 0.5) * Agent.settings().randomLooking.value

            // randomLooking113
            var random = rand.nextDouble() - 0.5
            if (abs(random) < 0.1) {
                random *= 4.0
            }
            randomYawOffset += random * Agent.settings().randomLooking113.value
        }

        override fun advance(ticks: Int) {
            repeat(ticks) { tick() }
        }

        override fun nextRotation(rotation: Rotation): Rotation {
            val actual = peekRotation(rotation)
            tick()
            return actual
        }

        override fun fork(): ITickableAimProcessor =
            object : AbstractAimProcessor(this@AbstractAimProcessor) {
                private var prev: Rotation = this@AbstractAimProcessor.getPrevRotation()

                override fun nextRotation(rotation: Rotation): Rotation = super.nextRotation(rotation).also { prev = it }

                override fun getPrevRotation(): Rotation = prev
            }

        protected abstract fun getPrevRotation(): Rotation

        /**
         * Nudges the player's pitch to a regular level. (Between `-20` and `10`,
         * increments are by `1`)
         */
        private fun nudgeToLevel(pitch: Float): Float =
            when {
                pitch < -20 -> pitch + 1
                pitch > 10 -> pitch - 1
                else -> pitch
            }

        private fun calculateMouseMove(
            current: Float,
            target: Float,
        ): Float {
            val delta = target - current
            val deltaPx = angleToMouse(delta)
            return current + mouseToAngle(deltaPx)
        }

        private fun angleToMouse(angleDelta: Float): Double {
            val minAngleChange = mouseToAngle(1.0)
            return Math.round(angleDelta / minAngleChange).toDouble()
        }

        private fun mouseToAngle(mouseDelta: Double): Float {
            // casting float literals to double gets us the precise values used by mc
            val f =
                ctx
                    .minecraft()
                    .options
                    .sensitivity()
                    .get() * 0.6 + 0.2
            return (mouseDelta * f * f * f * 8.0).toFloat() * 0.15f
        }
    }

    private class Target(
        val rotation: Rotation,
        val mode: Mode,
    ) {
        enum class Mode {
            /** Rotation will be set client-side and is visual to the player  */
            CLIENT,

            /** Rotation will be set server-side and is silent to the player  */
            SERVER,

            /** Rotation will remain unaffected on both the client and server  */
            NONE,

            ;

            companion object {
                fun resolve(
                    ctx: IPlayerContext,
                    blockInteract: Boolean,
                ): Mode {
                    val settings = Agent.settings()
                    val antiCheat = settings.antiCheatCompatibility.value
                    val blockFreeLook = settings.blockFreeLook.value

                    return when {
                        ctx.player().isFallFlying() ->
                            // always need to set angles while flying
                            if (settings.elytraFreeLook.value) SERVER else CLIENT

                        settings.freeLook.value -> {
                            // Regardless of if antiCheatCompatibility is enabled, if a blockInteract is
                            // requested then the player rotation needs to be set somehow
                            if (blockInteract) {
                                if (blockFreeLook) SERVER else CLIENT
                            } else {
                                if (antiCheat) SERVER else NONE
                            }
                        }

                        else ->
                            // all freeLook settings are disabled so set the angles
                            CLIENT
                    }
                }
            }
        }
    }
}
