package maestro.pathing.movement

import com.google.common.collect.ImmutableList
import maestro.Agent
import maestro.api.pathing.movement.IMovement
import maestro.api.pathing.movement.MovementStatus
import maestro.api.pathing.movement.MovementStatus.PREPPING
import maestro.api.pathing.movement.MovementStatus.SUCCESS
import maestro.api.player.PlayerContext
import maestro.api.utils.PackedBlockPos
import maestro.pathing.BlockStateInterface
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

/**
 * Base class for all movement primitives in the pathfinding system.
 *
 * Movements are stateless executors that:
 * 1. Compute movement/look intents each tick based on current state
 * 2. Apply intents via InputOverrideHandler
 * 3. Track progress via MovementState
 *
 * Subclasses implement three abstract methods:
 * - calculateCost: Static cost calculation for pathfinding
 * - computeMovement: Dynamic movement intent (called every tick)
 * - computeLook: Dynamic look intent (called every tick)
 */
abstract class Movement(
    @JvmField val agent: Agent,
    @JvmField val src: PackedBlockPos,
    @JvmField val dest: PackedBlockPos,
) : IMovement,
    MovementBehavior {
    @JvmField
    val ctx: PlayerContext = agent.playerContext
    val state: MovementState = MovementState()

    private var cost: Double? = null
    private var calculatedWhileLoaded: Boolean = false

    // Debug context for movement visualization
    val debug: MovementDebugContext by lazy {
        if (Agent
                .getPrimaryAgent()
                .settings.debugEnabled.value
        ) {
            ActiveDebugContext()
        } else {
            DisabledDebugContext
        }
    }

    // Legacy field for unconverted Java movements
    @JvmField
    val toBreakCached: List<BlockPos> = emptyList()

    companion object {
        @JvmField
        val HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP: ImmutableList<Direction> =
            ImmutableList.of(
                Direction.NORTH,
                Direction.SOUTH,
                Direction.EAST,
                Direction.WEST,
                Direction.DOWN,
            )
    }

    /**
     * Main update loop - called once per tick by PathExecutor.
     *
     * Orchestrates: reset check → compute intent → apply intent → return status
     */
    override fun update(): MovementStatus {
        // Clear previous tick's debug data
        debug.clear()

        checkCompletion()

        val intent = computeIntent(ctx)

        // Apply unified intent via delta-based handler
        agent.inputOverrideHandler.applyIntent(intent, agent.lookBehavior, ctx)

        return state.getStatus() ?: PREPPING
    }

    /**
     * Check if movement is complete. Subclasses can override for custom completion logic.
     *
     * Default: Check if player reached destination block.
     */
    protected open fun checkCompletion() {
        if (ctx.player().blockPosition() == dest.toBlockPos()) {
            state.setStatus(SUCCESS)
        }
    }

    /**
     * Get debug information for HUD display.
     *
     * @return compact debug string (e.g., "dist:0.52 drift:Y")
     */
    override fun getDebugInfo(): String = debug.getHudText()

    // Cost calculation
    override fun getCost(): Double = cost ?: throw NullPointerException("Cost not calculated")

    fun getCost(context: CalculationContext): Double {
        if (cost == null) {
            cost = calculateCost(context)
        }
        return cost!!
    }

    abstract fun calculateCost(context: CalculationContext): Double

    fun recalculateCost(context: CalculationContext): Double {
        cost = null
        return getCost(context)
    }

    fun override(cost: Double) {
        this.cost = cost
    }

    // IMovement interface
    override fun reset() {
        state.setStatus(PREPPING)
        agent.inputOverrideHandler.clearIntentTracking()
    }

    override fun getSrc(): PackedBlockPos = src

    override fun getDest(): PackedBlockPos = dest

    override fun safeToCancel(): Boolean = true

    override fun getDirection(): BlockPos = dest.toBlockPos().subtract(src.toBlockPos())

    override fun calculatedWhileLoaded(): Boolean = calculatedWhileLoaded

    override fun resetBlockCache() {}

    // Legacy methods for compatibility during transition
    fun checkLoadedChunk(context: CalculationContext) {
        calculatedWhileLoaded = context.bsi.worldContainsLoadedChunk(dest.x, dest.z)
    }

    open val validPositions: Set<PackedBlockPos>
        get() = emptySet()

    fun toBreakAll(): Array<BlockPos> = emptyArray()

    // Legacy stub methods for unconverted Java movements
    open fun toBreak(bsi: BlockStateInterface): List<BlockPos> = emptyList()

    open fun toPlace(bsi: BlockStateInterface): List<BlockPos> = emptyList()

    open fun toWalkInto(bsi: BlockStateInterface): List<BlockPos> = emptyList()

    // MovementBehavior interface - subclasses must implement
    abstract override fun computeIntent(ctx: PlayerContext): Intent
}
