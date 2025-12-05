package maestro.process

import maestro.Agent
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.goals.GoalBlock
import maestro.api.pathing.goals.GoalComposite
import maestro.api.pathing.goals.GoalGetToBlock
import maestro.api.pathing.goals.GoalRunAway
import maestro.api.pathing.goals.GoalTwoBlocks
import maestro.api.process.IGetToBlockProcess
import maestro.api.process.PathingCommand
import maestro.api.process.PathingCommandType
import maestro.api.utils.BlockOptionalMeta
import maestro.api.utils.BlockOptionalMetaLookup
import maestro.api.utils.MaestroLogger
import maestro.api.utils.PackedBlockPos
import maestro.api.utils.RotationUtils
import maestro.api.utils.input.Input
import maestro.pathing.movement.CalculationContext
import maestro.pathing.movement.MovementHelper
import net.minecraft.core.BlockPos
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.Logger
import kotlin.math.abs

class GetToBlockProcess(
    maestro: Agent,
) : MaestroProcessHelper(maestro),
    IGetToBlockProcess {
    private var gettingTo: BlockOptionalMeta? = null
    private var knownLocations: MutableList<BlockPos>? = null
    private var blacklist: MutableList<BlockPos>? = null // Locations we failed to calc to
    private var start: PackedBlockPos? = null

    private var tickCount = 0
    private var arrivalTickCount = 0

    override fun getToBlock(block: BlockOptionalMeta) {
        onLostControl()
        gettingTo = block
        start = ctx.playerFeet()
        blacklist = mutableListOf()
        arrivalTickCount = 0
        rescan(mutableListOf(), GetToBlockCalculationContext(false))
    }

    override fun isActive(): Boolean = gettingTo != null

    @Synchronized
    override fun onTick(
        calcFailed: Boolean,
        isSafeToCancel: Boolean,
    ): PathingCommand? {
        if (knownLocations == null) {
            rescan(mutableListOf(), GetToBlockCalculationContext(false))
        }

        val locations = knownLocations ?: emptyList()

        if (locations.isEmpty()) {
            if (Agent.settings().exploreForBlocks.value && !calcFailed) {
                val currentStart = start ?: ctx.playerFeet()
                return PathingCommand(
                    object : GoalRunAway(1.0, currentStart.toBlockPos()) {
                        override fun isInGoal(
                            x: Int,
                            y: Int,
                            z: Int,
                        ): Boolean = false

                        override fun heuristic(): Double = Double.NEGATIVE_INFINITY
                    },
                    PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH,
                )
            }

            log
                .atWarn()
                .addKeyValue("target_block", gettingTo)
                .log("No known locations found, canceling GetToBlock")

            if (isSafeToCancel) {
                onLostControl()
            }

            return PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL)
        }

        val goal = GoalComposite(*locations.map { createGoal(it) }.toTypedArray())

        if (calcFailed) {
            return if (Agent.settings().blacklistClosestOnFailure.value) {
                log
                    .atWarn()
                    .addKeyValue("target_block", gettingTo)
                    .addKeyValue("action", "blacklist_closest")
                    .log("Unable to find path, blacklisting closest unreachable instances")
                blacklistClosest()
                onTick(false, isSafeToCancel)
            } else {
                log
                    .atWarn()
                    .addKeyValue("target_block", gettingTo)
                    .log("Unable to find path, canceling GetToBlock")

                if (isSafeToCancel) {
                    onLostControl()
                }

                PathingCommand(goal, PathingCommandType.CANCEL_AND_SET_GOAL)
            }
        }

        val mineGoalUpdateInterval = Agent.settings().mineGoalUpdateInterval.value
        if (mineGoalUpdateInterval != 0 && tickCount++ % mineGoalUpdateInterval == 0) {
            val current = ArrayList(locations)
            val context = GetToBlockCalculationContext(true)
            Agent.getExecutor().execute { rescan(current, context) }
        }

        if (goal.isInGoal(ctx.playerFeet().toBlockPos()) &&
            maestro.pathingBehavior.pathStart()?.let { goal.isInGoal(it.toBlockPos()) } != false &&
            isSafeToCancel
        ) {
            // We're there
            val targetBlock = gettingTo?.block
            if (targetBlock != null && rightClickOnArrival(targetBlock)) {
                if (rightClick()) {
                    onLostControl()
                    return PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL)
                }
            } else {
                onLostControl()
                return PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL)
            }
        }

        return PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH)
    }

    // Blacklist the closest block and its adjacent blocks
    @Synchronized
    override fun blacklistClosest(): Boolean {
        val newBlacklist = mutableListOf<BlockPos>()
        val locations = knownLocations ?: return false

        locations.minByOrNull { ctx.playerFeet().distSqr(PackedBlockPos(it)) }?.let { newBlacklist.add(it) }

        outer@ while (true) {
            for (known in locations.toList()) { // Copy to avoid concurrent modification
                for (blacklisted in newBlacklist) {
                    if (areAdjacent(known, blacklisted)) { // Directly adjacent
                        newBlacklist.add(known)
                        locations.remove(known)
                        continue@outer
                    }
                }
            }
            break
        }

        log
            .atDebug()
            .addKeyValue("blacklist_count", newBlacklist.size)
            .log("Blacklisting unreachable block locations")

        blacklist?.addAll(newBlacklist)
        return newBlacklist.isNotEmpty()
    }

    // This is to signal to MineProcess that we don't care about the allowBreak setting
    // It is NOT to be used to actually calculate a path
    inner class GetToBlockCalculationContext(
        forUseOnAnotherThread: Boolean,
    ) : CalculationContext(maestro, forUseOnAnotherThread) {
        override fun breakCostMultiplierAt(
            x: Int,
            y: Int,
            z: Int,
            current: BlockState,
        ): Double = 1.0
    }

    // Safer than direct double comparison from distanceSq
    private fun areAdjacent(
        posA: BlockPos,
        posB: BlockPos,
    ): Boolean {
        val diffX = abs(posA.x - posB.x)
        val diffY = abs(posA.y - posB.y)
        val diffZ = abs(posA.z - posB.z)
        return (diffX + diffY + diffZ) == 1
    }

    @Synchronized
    override fun onLostControl() {
        gettingTo = null
        knownLocations = null
        start = null
        blacklist = null
        maestro.inputOverrideHandler.clearAllKeys()
    }

    override fun displayName0(): String {
        val locations = knownLocations
        return if (locations.isNullOrEmpty()) {
            "Exploring randomly to find $gettingTo, no known locations"
        } else {
            "Get To $gettingTo, ${locations.size} known locations"
        }
    }

    @Synchronized
    private fun rescan(
        known: List<BlockPos>,
        context: CalculationContext,
    ) {
        val target = gettingTo ?: return
        val currentBlacklist = blacklist ?: emptyList()

        val positions =
            MineProcess.searchWorld(
                context,
                BlockOptionalMetaLookup(target),
                64,
                known,
                currentBlacklist,
                mutableListOf(),
            )

        positions.removeIf { currentBlacklist.contains(it) }
        knownLocations = positions
    }

    private fun createGoal(pos: BlockPos): Goal {
        val block = gettingTo?.block ?: return GoalGetToBlock(pos)

        if (walkIntoInsteadOfAdjacent(block)) {
            return GoalTwoBlocks(pos)
        }

        if (blockOnTopMustBeRemoved(block) &&
            MovementHelper.isBlockNormalCube(maestro.bsi.get0(pos.above()))
        ) {
            // TODO this should be the check for chest openability
            return GoalBlock(pos.above())
        }

        return GoalGetToBlock(pos)
    }

    private fun rightClick(): Boolean {
        val locations = knownLocations ?: return true

        for (pos in locations) {
            val reachable =
                RotationUtils.reachable(
                    ctx,
                    pos,
                    ctx.playerController().blockReachDistance,
                )

            if (reachable.isPresent) {
                maestro.lookBehavior.updateTarget(reachable.get(), true)

                if (locations.contains(ctx.getSelectedBlock().orElse(null))) {
                    maestro.inputOverrideHandler.setInputForceState(
                        Input.CLICK_RIGHT,
                        true,
                    ) // TODO find some way to right click even if we're in an ESC menu

                    if (ctx.player().containerMenu !is InventoryMenu) {
                        return true
                    }
                }

                if (arrivalTickCount++ > 20) {
                    log
                        .atWarn()
                        .addKeyValue("timeout_ticks", arrivalTickCount)
                        .log("Right-click timeout")
                    return true
                }

                return false // Trying to right-click, will do it next tick or so
            }
        }

        log.atWarn().log("Arrived at target but failed to right-click open")
        return true
    }

    private fun walkIntoInsteadOfAdjacent(block: Block): Boolean {
        if (!Agent.settings().enterPortal.value) {
            return false
        }
        return block == Blocks.NETHER_PORTAL
    }

    private fun rightClickOnArrival(block: Block): Boolean {
        if (!Agent.settings().rightClickContainerOnArrival.value) {
            return false
        }

        return block == Blocks.CRAFTING_TABLE ||
            block == Blocks.FURNACE ||
            block == Blocks.BLAST_FURNACE ||
            block == Blocks.ENDER_CHEST ||
            block == Blocks.CHEST ||
            block == Blocks.TRAPPED_CHEST
    }

    private fun blockOnTopMustBeRemoved(block: Block): Boolean {
        if (!rightClickOnArrival(block)) { // Only if we plan to actually open it on arrival
            return false
        }

        // Only these chests; you can open a crafting table or furnace even with a block on top
        return block == Blocks.ENDER_CHEST ||
            block == Blocks.CHEST ||
            block == Blocks.TRAPPED_CHEST
    }

    companion object {
        private val log: Logger = MaestroLogger.get("path")
    }
}
