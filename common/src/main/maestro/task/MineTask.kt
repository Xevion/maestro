package maestro.task

import maestro.Agent
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.goals.GoalBlock
import maestro.api.pathing.goals.GoalComposite
import maestro.api.pathing.goals.GoalRunAway
import maestro.api.pathing.goals.GoalTwoBlocks
import maestro.api.pathing.movement.ActionCosts.COST_INF
import maestro.api.task.PathingCommand
import maestro.api.task.PathingCommandType
import maestro.api.utils.BlockOptionalMeta
import maestro.api.utils.BlockOptionalMetaLookup
import maestro.api.utils.BlockUtils
import maestro.api.utils.Loggers
import maestro.api.utils.PackedBlockPos
import maestro.api.utils.RotationUtils
import maestro.api.utils.SettingsUtil
import maestro.api.utils.format
import maestro.cache.CachedChunk
import maestro.cache.WorldScanner
import maestro.input.Input
import maestro.pathing.BlockStateInterface
import maestro.pathing.movement.CalculationContext
import maestro.pathing.movement.MovementValidation
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FallingBlock
import org.slf4j.Logger

class MineTask(
    maestro: Agent,
) : TaskHelper(maestro) {
    private var filter: BlockOptionalMetaLookup? = null
    private var knownOreLocations: MutableList<BlockPos> = mutableListOf()
    private var blacklist: MutableList<BlockPos> = mutableListOf()
    private var anticipatedDrops: Map<BlockPos, Long> = HashMap()
    private var branchPoint: BlockPos? = null
    private var branchPointRunaway: GoalRunAway? = null
    private var desiredQuantity = 0
    private var tickCount = 0
    private var lastClaimedArea: BlockPos? = null

    override fun isActive(): Boolean = filter != null

    override fun onTick(
        calcFailed: Boolean,
        isSafeToCancel: Boolean,
    ): PathingCommand? {
        if (desiredQuantity > 0) {
            val curr =
                ctx
                    .player()
                    .inventory
                    .items
                    .filter { stack: ItemStack -> filter!!.has(stack) }
                    .sumOf { it.count }
            if (curr >= desiredQuantity) {
                log
                    .atInfo()
                    .addKeyValue("count", curr)
                    .addKeyValue("desired", desiredQuantity)
                    .addKeyValue("filter", filter)
                    .log("Mining complete")
                cancel()
                return null
            }
        }

        // Check coordination goal status
        val client = maestro.coordinationClient
        if (client != null && client.isConnected()) {
            val status = client.checkGoalStatus()
            if (status.component1()) {
                log
                    .atInfo()
                    .addKeyValue("global_total", status.component2())
                    .log("Global mining goal complete")
                cancel()
                return null
            }
        }

        if (calcFailed) {
            if (knownOreLocations.isNotEmpty() && Agent.settings().blacklistClosestOnFailure.value) {
                log
                    .atWarn()
                    .addKeyValue("filter", filter)
                    .addKeyValue("locations_remaining", knownOreLocations.size)
                    .log("Pathfinding failed, blacklisting closest location")
                if (Agent.settings().notificationOnMineFail.value) {
                    logNotification(
                        "Unable to find any path to $filter, blacklisting presumably unreachable closest instance...",
                        true,
                    )
                }
                knownOreLocations
                    .minByOrNull { pos -> ctx.playerFeet().distSqr(PackedBlockPos(pos)) }
                    ?.let { blacklist.add(it) }
                knownOreLocations.removeIf { blacklist.contains(it) }
            } else {
                log
                    .atError()
                    .addKeyValue("filter", filter)
                    .log("Pathfinding failed, canceling mine")
                if (Agent.settings().notificationOnMineFail.value) {
                    logNotification("Unable to find any path to $filter, canceling mine", true)
                }
                cancel()
                return null
            }
        }

        updateLoucaSystem()
        val mineGoalUpdateInterval = Agent.settings().mineGoalUpdateInterval.value
        val curr: List<BlockPos> = ArrayList(knownOreLocations)
        if (mineGoalUpdateInterval != 0 && tickCount++ % mineGoalUpdateInterval == 0) {
            val context = CalculationContext(maestro, true)
            Agent.getExecutor().execute { rescan(curr, context) }
        }
        if (Agent.settings().legitMine.value) {
            if (!addNearby()) {
                cancel()
                return null
            }
        }
        val shaft =
            curr
                .filter { pos ->
                    pos.x == ctx.playerFeet().x && pos.z == ctx.playerFeet().z
                }.filter { pos -> pos.y >= ctx.playerFeet().y }
                .filter { pos ->
                    // after breaking a block, it takes mineGoalUpdateInterval ticks for it to actually update this list
                    BlockStateInterface.get(ctx, pos).block !is AirBlock
                }.minByOrNull { pos ->
                    ctx
                        .playerFeet()
                        .toBlockPos()
                        .above()
                        .distSqr(pos)
                }

        // Don't clear all keys - preserve CLICK_LEFT across path revalidation
        // CLICK_LEFT lifecycle is managed explicitly based on mining state
        if (shaft != null && ctx.player().onGround()) {
            val pos = shaft
            val state = maestro.bsi.get0(pos)
            if (!MovementValidation.avoidBreaking(maestro.bsi, pos.x, pos.y, pos.z, state)) {
                val rot = RotationUtils.reachable(ctx, pos)
                if (rot.isPresent && isSafeToCancel) {
                    maestro.lookBehavior.updateTarget(rot.get(), true)
                    MovementValidation.switchToBestToolFor(ctx, ctx.world().getBlockState(pos))
                    if (ctx.isLookingAt(pos) || ctx.playerRotations().isReallyCloseTo(rot.get())) {
                        maestro.inputOverrideHandler.setInputForceState(Input.CLICK_LEFT, true)
                    } else {
                        // Not looking at target - clear CLICK_LEFT
                        maestro.inputOverrideHandler.setInputForceState(Input.CLICK_LEFT, false)
                    }
                    return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                } else {
                    // Target not reachable - clear CLICK_LEFT
                    maestro.inputOverrideHandler.setInputForceState(Input.CLICK_LEFT, false)
                }
            } else {
                // Target should be avoided - clear CLICK_LEFT
                maestro.inputOverrideHandler.setInputForceState(Input.CLICK_LEFT, false)
            }
        } else {
            // No valid shaft target - clear CLICK_LEFT
            maestro.inputOverrideHandler.setInputForceState(Input.CLICK_LEFT, false)
        }
        val command = updateGoal()
        if (command == null) {
            // none in range
            cancel()
            return null
        }
        return command
    }

    private fun updateLoucaSystem() {
        val copy = HashMap(anticipatedDrops)
        ctx.getSelectedBlock().ifPresent { pos ->
            if (knownOreLocations.contains(pos)) {
                copy[pos] = System.currentTimeMillis() + Agent.settings().mineDropLoiterDurationMSThanksLouca.value
            }
        }
        // elaborate dance to avoid concurrentmodificationexception since rescan thread reads this
        // don't want to slow everything down with a gross lock do we now
        for (pos in anticipatedDrops.keys) {
            if (copy[pos]!! < System.currentTimeMillis()) {
                copy.remove(pos)
            }
        }
        anticipatedDrops = copy
    }

    override fun onLostControl() {
        // Clear CLICK_LEFT when losing control
        maestro.inputOverrideHandler.setInputForceState(Input.CLICK_LEFT, false)

        // Release area claim
        val client = maestro.coordinationClient
        val claimedArea = lastClaimedArea
        if (client != null && claimedArea != null) {
            client.releaseArea(claimedArea)
            lastClaimedArea = null
        }

        mine(0, null as BlockOptionalMetaLookup?)
    }

    override fun displayName0(): String = "Mine $filter"

    private fun updateGoal(): PathingCommand? {
        val filter = filterFilter() ?: return null

        // Area claiming for coordination
        val client = maestro.coordinationClient
        if (client != null && client.isConnected()) {
            val currentPos = ctx.playerFeet().toBlockPos()
            val needsClaim = lastClaimedArea?.let { currentPos.distSqr(it) > 16 * 16 } ?: true

            if (needsClaim) {
                val radius = Agent.settings().coordinationClaimRadius.value
                val claimed = client.claimArea(currentPos, radius)

                if (!claimed) {
                    log
                        .atWarn()
                        .addKeyValue("pos", currentPos.format())
                        .addKeyValue("radius", radius)
                        .log("Area claim denied, pausing mining")
                    return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                }

                log
                    .atInfo()
                    .addKeyValue("pos", currentPos.format())
                    .addKeyValue("radius", radius)
                    .log("Area claimed")

                lastClaimedArea = currentPos
            }
        } else if (Agent.settings().coordinationEnabled.value) {
            log
                .atDebug()
                .addKeyValue("client_null", client == null)
                .addKeyValue("connected", client != null && client.isConnected())
                .log("Coordination enabled but client unavailable")
        }

        val legit = Agent.settings().legitMine.value
        val locs = knownOreLocations
        if (locs.isNotEmpty()) {
            val context = CalculationContext(maestro)
            val locs2 =
                prune(
                    context,
                    ArrayList(locs),
                    filter,
                    Agent.settings().mineMaxOreLocationsCount.value,
                    blacklist,
                    droppedItemsScan(),
                )
            // can't reassign locs, gotta make a new var locs2, because we use it in a lambda right
            // here, and variables you use in a lambda must be effectively final
            val goal =
                GoalComposite(
                    *locs2.map { loc -> coalesce(loc, locs2, context) }.toTypedArray(),
                )
            knownOreLocations = locs2
            return PathingCommand(
                goal,
                if (legit) {
                    PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH
                } else {
                    PathingCommandType.REVALIDATE_GOAL_AND_PATH
                },
            )
        }
        // we don't know any ore locations at the moment
        if (!legit && !Agent.settings().exploreForBlocks.value) {
            return null
        }
        // only when we should explore for blocks or are in legit mode we do this
        val y = Agent.settings().legitMineYLevel.value
        if (branchPoint == null) {
            branchPoint = ctx.playerFeet().toBlockPos()
        }
        // TODO shaft mode, mine 1x1 shafts to either side
        // TODO also, see if the GoalRunAway with maintain Y at 11 works even from the surface
        if (branchPointRunaway == null) {
            branchPointRunaway =
                object : GoalRunAway(1.0, y, branchPoint!!) {
                    override fun isInGoal(
                        x: Int,
                        y: Int,
                        z: Int,
                    ): Boolean = false

                    override fun heuristic(): Double = Double.NEGATIVE_INFINITY
                }
        }
        return PathingCommand(branchPointRunaway, PathingCommandType.REVALIDATE_GOAL_AND_PATH)
    }

    private fun rescan(
        already: List<BlockPos>,
        context: CalculationContext,
    ) {
        val filter = filterFilter() ?: return
        if (Agent.settings().legitMine.value) {
            return
        }
        val dropped = droppedItemsScan()
        val locs =
            searchWorld(
                context,
                filter,
                Agent.settings().mineMaxOreLocationsCount.value,
                already,
                blacklist,
                dropped,
            )
        locs.addAll(dropped)
        if (locs.isEmpty() && !Agent.settings().exploreForBlocks.value) {
            log.atWarn().addKeyValue("filter", filter).log("No known locations, cancelling mine")
            if (Agent.settings().notificationOnMineFail.value) {
                logNotification("No locations for $filter known, cancelling", true)
            }
            cancel()
            return
        }
        knownOreLocations = locs
    }

    private fun internalMiningGoal(
        pos: BlockPos,
        context: CalculationContext,
        locs: List<BlockPos>,
    ): Boolean {
        // Here, BlockStateInterface is used because the position may be in a cached chunk (the
        // targeted block is one that is kept track of)
        if (locs.contains(pos)) {
            return true
        }
        val state = context.bsi.get0(pos)
        if (Agent.settings().internalMiningAirException.value && state.block is AirBlock) {
            return true
        }
        return filter!!.has(state) && plausibleToBreak(context, pos)
    }

    private fun coalesce(
        loc: BlockPos,
        locs: List<BlockPos>,
        context: CalculationContext,
    ): Goal {
        val assumeVerticalShaftMine = maestro.bsi.get0(loc.above()).block !is FallingBlock
        if (!Agent.settings().forceInternalMining.value) {
            return if (assumeVerticalShaftMine) {
                // we can get directly below the block
                GoalThreeBlocks(loc)
            } else {
                // we need to get feet or head into the block
                GoalTwoBlocks(loc)
            }
        }
        val upwardGoal = internalMiningGoal(loc.above(), context, locs)
        val downwardGoal = internalMiningGoal(loc.below(), context, locs)
        val doubleDownwardGoal = internalMiningGoal(loc.below(2), context, locs)
        if (upwardGoal == downwardGoal) { // symmetric
            return if (doubleDownwardGoal && assumeVerticalShaftMine) {
                // we have a checkerboard like pattern
                // this one, and the one two below it
                // therefore it's fine to path to immediately below this one, since your feet will
                // be in the doubleDownwardGoal
                // but only if assumeVerticalShaftMine
                GoalThreeBlocks(loc)
            } else {
                // this block has nothing interesting two below, but is symmetric vertically so we
                // can get either feet or head into it
                GoalTwoBlocks(loc)
            }
        }
        if (upwardGoal) {
            // downwardGoal known to be false
            // ignore the gap then potential doubleDownward, because we want to path feet into this
            // one and head into upwardGoal
            return GoalBlock(loc)
        }
        // upwardGoal known to be false, downwardGoal known to be true
        if (doubleDownwardGoal && assumeVerticalShaftMine) {
            // this block and two below it are goals
            // path into the center of the one below, because that includes directly below this one
            return GoalTwoBlocks(loc.below())
        }
        // upwardGoal false, downwardGoal true, doubleDownwardGoal false
        // just this block and the one immediately below, no others
        return GoalBlock(loc.below())
    }

    private class GoalThreeBlocks(
        pos: BlockPos,
    ) : GoalTwoBlocks(pos) {
        override fun isInGoal(
            x: Int,
            y: Int,
            z: Int,
        ): Boolean = x == this.x && (y == this.y || y == this.y - 1 || y == this.y - 2) && z == this.z

        override fun heuristic(
            x: Int,
            y: Int,
            z: Int,
        ): Double {
            val xDiff = x - this.x
            val yDiff = y - this.y
            val zDiff = z - this.z
            return GoalBlock.calculate(
                xDiff.toDouble(),
                if (yDiff < -1) {
                    yDiff + 2
                } else if (yDiff == -1) {
                    0
                } else {
                    yDiff
                },
                zDiff.toDouble(),
            )
        }

        override fun equals(other: Any?): Boolean = super.equals(other)

        override fun hashCode(): Int = super.hashCode() * 393857768

        override fun toString(): String =
            String.format(
                "GoalThreeBlocks{x=%s,y=%s,z=%s}",
                SettingsUtil.maybeCensor(x),
                SettingsUtil.maybeCensor(y),
                SettingsUtil.maybeCensor(z),
            )
    }

    fun droppedItemsScan(): MutableList<BlockPos> {
        if (!Agent.settings().mineScanDroppedItems.value) {
            return mutableListOf()
        }
        val ret = mutableListOf<BlockPos>()
        for (entity in (ctx.world() as ClientLevel).entitiesForRendering()) {
            if (entity is ItemEntity) {
                if (filter!!.has(entity.item)) {
                    ret.add(entity.blockPosition())
                }
            }
        }
        ret.addAll(anticipatedDrops.keys)
        return ret
    }

    private fun addNearby(): Boolean {
        val dropped = droppedItemsScan()
        knownOreLocations.addAll(dropped)
        val playerFeet = ctx.playerFeet().toBlockPos()
        val bsi = BlockStateInterface(ctx)

        val filter = filterFilter() ?: return false

        val searchDist = 10
        val fakedBlockReachDistance = 20.0
        // at least 10 * sqrt(3) with some extra space to account for positioning within the block
        for (x in playerFeet.x - searchDist..playerFeet.x + searchDist) {
            for (y in playerFeet.y - searchDist..playerFeet.y + searchDist) {
                for (z in playerFeet.z - searchDist..playerFeet.z + searchDist) {
                    // crucial to only add blocks we can see because otherwise this
                    // is an x-ray and it'll get caught
                    if (filter.has(bsi.get0(x, y, z))) {
                        val pos = BlockPos(x, y, z)
                        if ((
                                Agent.settings().legitMineIncludeDiagonals.value &&
                                    knownOreLocations.any { ore ->
                                        ore.distSqr(pos) <= 2 // sq means this is pytha dist <= sqrt(2)
                                    }
                            ) ||
                            RotationUtils.reachable(ctx, pos, fakedBlockReachDistance).isPresent
                        ) {
                            knownOreLocations.add(pos)
                        }
                    }
                }
            }
        }
        knownOreLocations =
            prune(
                CalculationContext(maestro),
                knownOreLocations,
                filter,
                Agent.settings().mineMaxOreLocationsCount.value,
                blacklist,
                dropped,
            )
        return true
    }

    /**
     * Begin to search for and mine the specified blocks until the number of specified items to get
     * from the blocks that are mined.
     *
     * @param quantity The total number of items to get
     * @param blocks The blocks to mine
     */
    fun mineByName(
        quantity: Int,
        vararg blocks: String,
    ) {
        mine(quantity, BlockOptionalMetaLookup(*blocks))
    }

    /**
     * Begin to search for and mine the specified blocks.
     *
     * @param blocks The blocks to mine
     */
    fun mineByName(vararg blocks: String) {
        mineByName(0, *blocks)
    }

    /**
     * Begin to search for and mine the specified blocks until the number of specified items to get
     * from the blocks that are mined. This is based on the first target block to mine.
     *
     * @param quantity The number of items to get from blocks mined
     * @param filter The blocks to mine
     */
    fun mine(
        quantity: Int,
        filter: BlockOptionalMetaLookup?,
    ) {
        this.filter = filter
        if (this.filterFilter() == null) {
            this.filter = null
        }
        this.desiredQuantity = quantity
        this.knownOreLocations = mutableListOf()
        this.blacklist = mutableListOf()
        this.branchPoint = null
        this.branchPointRunaway = null
        this.anticipatedDrops = HashMap()
        if (filter != null) {
            rescan(ArrayList(), CalculationContext(maestro))
        }
    }

    /**
     * Begin to search for and mine the specified blocks.
     *
     * @param filter The blocks to mine
     */
    fun mine(filter: BlockOptionalMetaLookup) {
        mine(0, filter)
    }

    /**
     * Begin to search for and mine the specified blocks.
     *
     * @param boms The blocks to mine
     */
    fun mine(
        quantity: Int,
        vararg boms: BlockOptionalMeta,
    ) {
        mine(quantity, BlockOptionalMetaLookup(*boms))
    }

    /**
     * Begin to search for and mine the specified blocks.
     *
     * @param boms The blocks to mine
     */
    fun mine(vararg boms: BlockOptionalMeta) {
        mine(0, *boms)
    }

    /**
     * Begin to search for and mine the specified blocks.
     *
     * @param quantity The total number of items to get
     * @param blocks The blocks to mine
     */
    fun mine(
        quantity: Int,
        vararg blocks: net.minecraft.world.level.block.Block,
    ) {
        mine(
            quantity,
            BlockOptionalMetaLookup(
                *blocks
                    .map { BlockOptionalMeta(it) }
                    .toTypedArray(),
            ),
        )
    }

    /**
     * Begin to search for and mine the specified blocks.
     *
     * @param blocks The blocks to mine
     */
    fun mine(vararg blocks: net.minecraft.world.level.block.Block) {
        mine(0, *blocks)
    }

    /** Cancels the current mining task */
    fun cancel() {
        onLostControl()
    }

    private fun filterFilter(): BlockOptionalMetaLookup? {
        val currentFilter = this.filter ?: return null
        if (!Agent.settings().allowBreak.value) {
            val f =
                BlockOptionalMetaLookup(
                    *currentFilter
                        .blocks()
                        .filter { e ->
                            Agent
                                .settings()
                                .allowBreakAnyway.value
                                .contains(e.block)
                        }.toTypedArray(),
                )
            if (f.blocks().isEmpty()) {
                log
                    .atError()
                    .addKeyValue("filter", filter)
                    .log("Mining rejected - allow-break disabled and filter not in allow-break-anyway")
                return null
            }
            return f
        }
        return currentFilter
    }

    /**
     * Get current known ore locations for visualization.
     * Returns immutable copy for thread safety.
     *
     * @return List of block positions of known ores
     */
    fun getKnownOreLocations(): List<BlockPos> = knownOreLocations.toList()

    companion object {
        private val log: Logger = Loggers.get("mine")

        fun searchWorld(
            ctx: CalculationContext,
            filter: BlockOptionalMetaLookup,
            max: Int,
            alreadyKnown: List<BlockPos>,
            blacklist: List<BlockPos>,
            dropped: MutableList<BlockPos>,
        ): MutableList<BlockPos> {
            var locs = mutableListOf<BlockPos>()
            val untracked = mutableListOf<net.minecraft.world.level.block.Block>()
            for (bom in filter.blocks()) {
                val block = bom.block
                if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(block)) {
                    val pf = ctx.maestro.playerContext.playerFeet()

                    // maxRegionDistanceSq 2 means adjacent directly or adjacent diagonally; nothing
                    // further than that
                    locs.addAll(
                        ctx.worldData
                            .cachedWorld
                            .getLocationsOf(
                                BlockUtils.blockToString(block),
                                Agent.settings().maxCachedWorldScanCount.value,
                                pf.x,
                                pf.z,
                                2,
                            ),
                    )
                } else {
                    untracked.add(block)
                }
            }

            locs = prune(ctx, locs, filter, max, blacklist, dropped)

            if (untracked.isNotEmpty() ||
                (Agent.settings().extendCacheOnThreshold.value && locs.size < max)
            ) {
                locs.addAll(
                    WorldScanner
                        .scanChunkRadius(
                            ctx.maestro.playerContext,
                            filter,
                            max,
                            10,
                            32,
                        ), // maxSearchRadius is NOT sq
                )
            }

            locs.addAll(alreadyKnown)

            return prune(ctx, locs, filter, max, blacklist, dropped)
        }

        private fun prune(
            ctx: CalculationContext,
            locs2: MutableList<BlockPos>,
            filter: BlockOptionalMetaLookup,
            max: Int,
            blacklist: List<BlockPos>,
            dropped: MutableList<BlockPos>,
        ): MutableList<BlockPos> {
            dropped.removeIf { drop ->
                for (pos in locs2) {
                    if (pos.distSqr(drop) <= 9 &&
                        filter.has(ctx[pos.x, pos.y, pos.z]) &&
                        plausibleToBreak(ctx, pos)
                    ) {
                        // TODO maybe drop also has to be supported? no lava below?
                        return@removeIf true
                    }
                }
                false
            }
            val locs =
                locs2
                    .distinct()
                    // remove any that are within loaded chunks that aren't actually what we want
                    .filter { pos ->
                        !ctx.bsi.worldContainsLoadedChunk(pos.x, pos.z) ||
                            filter.has(ctx[pos.x, pos.y, pos.z]) ||
                            dropped.contains(pos)
                    }
                    // remove any that are implausible to mine (encased in bedrock, or touching lava)
                    .filter { pos -> plausibleToBreak(ctx, pos) }
                    .filter { pos ->
                        if (Agent.settings().allowOnlyExposedOres.value) {
                            isNextToAir(ctx, pos)
                        } else {
                            true
                        }
                    }.filter { pos ->
                        pos.y >= Agent.settings().minYLevelWhileMining.value + ctx.world.dimensionType().minY()
                    }.filter { pos -> pos.y <= Agent.settings().maxYLevelWhileMining.value }
                    .filter { pos -> !blacklist.contains(pos) }
                    .sortedBy {
                        ctx.maestro.playerContext
                            .player()
                            .blockPosition()
                            .distSqr(it)
                    }.toMutableList()

            return if (locs.size > max) {
                locs.subList(0, max)
            } else {
                locs
            }
        }

        fun isNextToAir(
            ctx: CalculationContext,
            pos: BlockPos,
        ): Boolean {
            val radius = Agent.settings().allowOnlyExposedOresDistance.value
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    for (dz in -radius..radius) {
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) + kotlin.math.abs(dz) <= radius &&
                            MovementValidation.isTransparent(
                                ctx.getBlock(pos.x + dx, pos.y + dy, pos.z + dz),
                            )
                        ) {
                            return true
                        }
                    }
                }
            }
            return false
        }

        fun plausibleToBreak(
            ctx: CalculationContext,
            pos: BlockPos,
        ): Boolean {
            val state = ctx.bsi.get0(pos)
            if (MovementValidation.getMiningDurationTicks(ctx, pos.x, pos.y, pos.z, state, true) >= COST_INF) {
                return false
            }
            if (MovementValidation.avoidBreaking(ctx.bsi, pos.x, pos.y, pos.z, state)) {
                return false
            }

            // bedrock above and below makes it implausible, otherwise we're good
            return !(
                ctx.bsi.get0(pos.above()).block === Blocks.BEDROCK &&
                    ctx.bsi.get0(pos.below()).block === Blocks.BEDROCK
            )
        }
    }
}
