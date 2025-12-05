package maestro.task

import maestro.Agent
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.goals.GoalBlock
import maestro.api.pathing.goals.GoalComposite
import maestro.api.pathing.goals.GoalGetToBlock
import maestro.api.task.ITask
import maestro.api.task.PathingCommand
import maestro.api.task.PathingCommandType
import maestro.api.utils.BlockOptionalMetaLookup
import maestro.api.utils.Loggers
import maestro.api.utils.PackedBlockPos
import maestro.api.utils.RayTraceUtils
import maestro.api.utils.RotationUtils
import maestro.cache.WorldScanner
import maestro.input.Input
import maestro.pathing.movement.MovementValidation
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.BambooStalkBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.BonemealableBlock
import net.minecraft.world.level.block.CactusBlock
import net.minecraft.world.level.block.CocoaBlock
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.NetherWartBlock
import net.minecraft.world.level.block.SugarCaneBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.slf4j.Logger

class FarmTask(
    maestro: Agent,
) : TaskHelper(maestro),
    ITask {
    private var active = false
    private var locations: List<BlockPos>? = null
    private var tickCount = 0
    private var range = 0
    private var center: BlockPos? = null

    private enum class Harvest(
        val block: Block,
        private val readyToHarvestPredicate: ((BlockState) -> Boolean)?,
    ) {
        WHEAT(Blocks.WHEAT as CropBlock, { (Blocks.WHEAT as CropBlock).isMaxAge(it) }),
        CARROTS(Blocks.CARROTS as CropBlock, { (Blocks.CARROTS as CropBlock).isMaxAge(it) }),
        POTATOES(Blocks.POTATOES as CropBlock, { (Blocks.POTATOES as CropBlock).isMaxAge(it) }),
        BEETROOT(Blocks.BEETROOTS as CropBlock, { (Blocks.BEETROOTS as CropBlock).isMaxAge(it) }),
        PUMPKIN(Blocks.PUMPKIN, { true }),
        MELON(Blocks.MELON, { true }),
        NETHERWART(Blocks.NETHER_WART, { it.getValue(NetherWartBlock.AGE) >= 3 }),
        COCOA(Blocks.COCOA, { it.getValue(CocoaBlock.AGE) >= 2 }),
        SUGARCANE(Blocks.SUGAR_CANE, null) {
            override fun readyToHarvest(
                world: Level,
                pos: BlockPos,
                state: BlockState,
            ): Boolean {
                if (Agent.settings().replantCrops.value) {
                    return world.getBlockState(pos.below()).block is SugarCaneBlock
                }
                return true
            }
        },
        BAMBOO(Blocks.BAMBOO, null) {
            override fun readyToHarvest(
                world: Level,
                pos: BlockPos,
                state: BlockState,
            ): Boolean {
                if (Agent.settings().replantCrops.value) {
                    return world.getBlockState(pos.below()).block is BambooStalkBlock
                }
                return true
            }
        },
        CACTUS(Blocks.CACTUS, null) {
            override fun readyToHarvest(
                world: Level,
                pos: BlockPos,
                state: BlockState,
            ): Boolean {
                if (Agent.settings().replantCrops.value) {
                    return world.getBlockState(pos.below()).block is CactusBlock
                }
                return true
            }
        },
        ;

        open fun readyToHarvest(
            world: Level,
            pos: BlockPos,
            state: BlockState,
        ): Boolean = readyToHarvestPredicate?.invoke(state) ?: false
    }

    override fun isActive(): Boolean = active

    /**
     * Begin to search for crops to farm with in specified aria from specified location.
     *
     * @param range The distance from center to farm from
     * @param pos The center position to base the range from
     */
    fun farm(
        range: Int,
        pos: BlockPos?,
    ) {
        center =
            if (pos == null) {
                maestro.playerContext.playerFeet().toBlockPos()
            } else {
                pos
            }
        this.range = range
        active = true
        locations = null
    }

    /** Begin to search for nearby crops to farm. */
    fun farm() {
        farm(0, null)
    }

    /**
     * Begin to search for crops to farm with in specified aria from the position the command was
     * executed.
     *
     * @param range The distance to search for crops to farm
     */
    fun farm(range: Int) {
        farm(range, null)
    }

    private fun readyForHarvest(
        world: Level,
        pos: BlockPos,
        state: BlockState,
    ): Boolean {
        for (harvest in Harvest.entries) {
            if (harvest.block == state.block) {
                return harvest.readyToHarvest(world, pos, state)
            }
        }
        return false
    }

    private fun isPlantable(stack: ItemStack): Boolean = FARMLAND_PLANTABLE.contains(stack.item)

    private fun isBoneMeal(stack: ItemStack): Boolean = !stack.isEmpty && stack.item == Items.BONE_MEAL

    private fun isNetherWart(stack: ItemStack): Boolean = !stack.isEmpty && stack.item == Items.NETHER_WART

    private fun isCocoa(stack: ItemStack): Boolean = !stack.isEmpty && stack.item == Items.COCOA_BEANS

    override fun onTick(
        calcFailed: Boolean,
        isSafeToCancel: Boolean,
    ): PathingCommand {
        if (Agent.settings().mineGoalUpdateInterval.value != 0 &&
            tickCount++ % Agent.settings().mineGoalUpdateInterval.value == 0
        ) {
            val scan = mutableListOf<Block>()
            for (harvest in Harvest.entries) {
                scan.add(harvest.block)
            }
            if (Agent.settings().replantCrops.value) {
                scan.add(Blocks.FARMLAND)
                scan.add(Blocks.JUNGLE_LOG)
                if (Agent.settings().replantNetherWart.value) {
                    scan.add(Blocks.SOUL_SAND)
                }
            }

            Agent.getExecutor().execute {
                locations =
                    WorldScanner
                        .scanChunkRadius(
                            ctx,
                            BlockOptionalMetaLookup(scan),
                            Agent.settings().farmMaxScanSize.value,
                            10,
                            10,
                        )
            }
        }

        val currentLocations = locations ?: return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)

        val toBreak = mutableListOf<BlockPos>()
        val openFarmland = mutableListOf<BlockPos>()
        val bonemealable = mutableListOf<BlockPos>()
        val openSoulsand = mutableListOf<BlockPos>()
        val openLog = mutableListOf<BlockPos>()

        for (pos in currentLocations) {
            // check if the target block is out of range.
            if (range != 0 && pos.distSqr(center ?: continue) > range * range) {
                continue
            }

            val state = ctx.world().getBlockState(pos)
            val airAbove = ctx.world().getBlockState(pos.above()).block is AirBlock

            when (state.block) {
                Blocks.FARMLAND -> {
                    if (airAbove) {
                        openFarmland.add(pos)
                    }
                    continue
                }
                Blocks.SOUL_SAND -> {
                    if (airAbove) {
                        openSoulsand.add(pos)
                    }
                    continue
                }
                Blocks.JUNGLE_LOG -> {
                    for (direction in Direction.Plane.HORIZONTAL) {
                        if (ctx.world().getBlockState(pos.relative(direction)).block is AirBlock) {
                            openLog.add(pos)
                            break
                        }
                    }
                    continue
                }
            }

            if (readyForHarvest(ctx.world(), pos, state)) {
                toBreak.add(pos)
                continue
            }

            val block = state.block
            if (block is BonemealableBlock) {
                if (block.isValidBonemealTarget(ctx.world(), pos, state) &&
                    block.isBonemealSuccess(ctx.world(), ctx.world().random, pos, state)
                ) {
                    bonemealable.add(pos)
                }
            }
        }

        // Don't clear all keys - preserve CLICK_LEFT across path revalidation
        // CLICK_LEFT lifecycle is managed explicitly based on farming state
        val playerPos = ctx.playerFeet()
        val blockReachDistance = ctx.playerController().blockReachDistance

        // Try to break ready crops within reach
        for (pos in toBreak) {
            if (playerPos.distSqr(PackedBlockPos(pos)) > blockReachDistance * blockReachDistance) {
                continue
            }
            val rot = RotationUtils.reachable(ctx, pos)
            if (rot.isPresent && isSafeToCancel) {
                maestro.lookBehavior.updateTarget(rot.get(), true)
                MovementValidation.switchToBestToolFor(ctx, ctx.world().getBlockState(pos))
                if (ctx.isLookingAt(pos)) {
                    maestro.inputOverrideHandler.setInputForceState(Input.CLICK_LEFT, true)
                }
                return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
            }
        }

        // Try to plant on open farmland or soulsand
        val both = mutableListOf<BlockPos>()
        both.addAll(openFarmland)
        both.addAll(openSoulsand)

        for (pos in both) {
            if (playerPos.distSqr(PackedBlockPos(pos)) > blockReachDistance * blockReachDistance) {
                continue
            }
            val soulsand = openSoulsand.contains(pos)
            val rot =
                RotationUtils.reachableOffset(
                    ctx,
                    pos,
                    Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5),
                    blockReachDistance,
                    false,
                )
            if (rot.isPresent &&
                isSafeToCancel &&
                maestro.inventoryBehavior.throwaway(
                    true,
                    if (soulsand) this::isNetherWart else this::isPlantable,
                )
            ) {
                val result = RayTraceUtils.rayTraceTowards(ctx.player(), rot.get(), blockReachDistance)
                if (result is BlockHitResult && result.direction == Direction.UP) {
                    maestro.lookBehavior.updateTarget(rot.get(), true)
                    if (ctx.isLookingAt(pos)) {
                        maestro.inputOverrideHandler.setInputForceState(Input.CLICK_RIGHT, true)
                    }
                    return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                }
            }
        }

        // Try to plant cocoa on logs
        for (pos in openLog) {
            if (playerPos.distSqr(PackedBlockPos(pos)) > blockReachDistance * blockReachDistance) {
                continue
            }
            for (dir in Direction.Plane.HORIZONTAL) {
                if (ctx.world().getBlockState(pos.relative(dir)).block !is AirBlock) {
                    continue
                }
                val faceCenter =
                    Vec3
                        .atCenterOf(pos)
                        .add(Vec3.atLowerCornerOf(dir.unitVec3i).scale(0.5))
                val rot =
                    RotationUtils.reachableOffset(
                        ctx,
                        pos,
                        faceCenter,
                        blockReachDistance,
                        false,
                    )
                if (rot.isPresent &&
                    isSafeToCancel &&
                    maestro.inventoryBehavior.throwaway(true, this::isCocoa)
                ) {
                    val result = RayTraceUtils.rayTraceTowards(ctx.player(), rot.get(), blockReachDistance)
                    if (result is BlockHitResult && result.direction == dir) {
                        maestro.lookBehavior.updateTarget(rot.get(), true)
                        if (ctx.isLookingAt(pos)) {
                            maestro.inputOverrideHandler.setInputForceState(Input.CLICK_RIGHT, true)
                        }
                        return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                    }
                }
            }
        }

        // Try to use bone meal
        for (pos in bonemealable) {
            if (playerPos.distSqr(PackedBlockPos(pos)) > blockReachDistance * blockReachDistance) {
                continue
            }
            val rot = RotationUtils.reachable(ctx, pos)
            if (rot.isPresent &&
                isSafeToCancel &&
                maestro.inventoryBehavior.throwaway(true, this::isBoneMeal)
            ) {
                maestro.lookBehavior.updateTarget(rot.get(), true)
                if (ctx.isLookingAt(pos)) {
                    maestro.inputOverrideHandler.setInputForceState(Input.CLICK_RIGHT, true)
                }
                return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
            }
        }

        if (calcFailed) {
            log.atError().log("Farming failed - pathfinding failed")
            if (Agent.settings().notificationOnFarmFail.value) {
                logNotification("Farm failed", true)
            }
            onLostControl()
            return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
        }

        val goals = mutableListOf<Goal>()

        for (pos in toBreak) {
            goals.add(BuilderTask.GoalBreak(pos))
        }

        if (maestro.inventoryBehavior.throwaway(false, this::isPlantable)) {
            for (pos in openFarmland) {
                goals.add(GoalBlock(pos.above()))
            }
        }

        if (maestro.inventoryBehavior.throwaway(false, this::isNetherWart)) {
            for (pos in openSoulsand) {
                goals.add(GoalBlock(pos.above()))
            }
        }

        if (maestro.inventoryBehavior.throwaway(false, this::isCocoa)) {
            for (pos in openLog) {
                for (direction in Direction.Plane.HORIZONTAL) {
                    if (ctx.world().getBlockState(pos.relative(direction)).block is AirBlock) {
                        goals.add(GoalGetToBlock(pos.relative(direction)))
                    }
                }
            }
        }

        if (maestro.inventoryBehavior.throwaway(false, this::isBoneMeal)) {
            for (pos in bonemealable) {
                goals.add(GoalBlock(pos))
            }
        }

        for (entity in ctx.entities()) {
            if (entity is ItemEntity && entity.onGround()) {
                if (PICKUP_DROPPED.contains(entity.item.item)) {
                    // +0.1 because of farmland's 0.9375 dummy height lol
                    goals.add(
                        GoalBlock(
                            PackedBlockPos(
                                entity.position().x.toInt(),
                                (entity.position().y + 0.1).toInt(),
                                entity.position().z.toInt(),
                            ).toBlockPos(),
                        ),
                    )
                }
            }
        }

        if (goals.isEmpty()) {
            log.atError().log("Farming failed - no valid goals")
            if (Agent.settings().notificationOnFarmFail.value) {
                logNotification("Farm failed", true)
            }
            onLostControl()
            return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
        }

        return PathingCommand(
            GoalComposite(*goals.toTypedArray()),
            PathingCommandType.SET_GOAL_AND_PATH,
        )
    }

    override fun onLostControl() {
        // Clear interaction keys when losing control
        maestro.inputOverrideHandler.clearInteractionKeys()
        active = false
    }

    override fun displayName0(): String = "Farming"

    companion object {
        private val log: Logger = Loggers.Farm.get()

        private val FARMLAND_PLANTABLE: List<Item> =
            listOf(
                Items.BEETROOT_SEEDS,
                Items.MELON_SEEDS,
                Items.WHEAT_SEEDS,
                Items.PUMPKIN_SEEDS,
                Items.POTATO,
                Items.CARROT,
            )

        private val PICKUP_DROPPED: List<Item> =
            listOf(
                Items.BEETROOT_SEEDS,
                Items.BEETROOT,
                Items.MELON_SEEDS,
                Items.MELON_SLICE,
                Blocks.MELON.asItem(),
                Items.WHEAT_SEEDS,
                Items.WHEAT,
                Items.PUMPKIN_SEEDS,
                Blocks.PUMPKIN.asItem(),
                Items.POTATO,
                Items.CARROT,
                Items.NETHER_WART,
                Items.COCOA_BEANS,
                Blocks.SUGAR_CANE.asItem(),
                Blocks.BAMBOO.asItem(),
                Blocks.CACTUS.asItem(),
            )
    }
}
