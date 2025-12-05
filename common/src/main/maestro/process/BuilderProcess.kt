package maestro.process

import com.google.common.collect.ImmutableSet
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import maestro.Agent
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.goals.GoalBlock
import maestro.api.pathing.goals.GoalComposite
import maestro.api.pathing.goals.GoalGetToBlock
import maestro.api.pathing.movement.ActionCosts.COST_INF
import maestro.api.process.IBuilderProcess
import maestro.api.process.PathingCommand
import maestro.api.process.PathingCommandType
import maestro.api.schematic.FillSchematic
import maestro.api.schematic.ISchematic
import maestro.api.schematic.IStaticSchematic
import maestro.api.schematic.MaskSchematic
import maestro.api.schematic.MirroredSchematic
import maestro.api.schematic.RotatedSchematic
import maestro.api.schematic.SubstituteSchematic
import maestro.api.utils.MaestroLogger
import maestro.api.utils.PackedBlockPos
import maestro.api.utils.RayTraceUtils
import maestro.api.utils.Rotation
import maestro.api.utils.RotationUtils
import maestro.api.utils.SettingsUtil
import maestro.api.utils.input.Input
import maestro.pathing.BlockStateInterface
import maestro.pathing.PathingCommandContext
import maestro.pathing.movement.CalculationContext
import maestro.pathing.movement.Movement
import maestro.pathing.movement.MovementHelper
import maestro.process.schematic.MapArtSchematic
import maestro.process.schematic.SchematicSystem
import maestro.process.schematic.SelectionSchematic
import maestro.process.schematic.litematica.LitematicaHelper
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.PipeBlock
import net.minecraft.world.level.block.RotatedPillarBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.slf4j.Logger
import java.io.File
import java.io.FileInputStream
import java.util.Optional
import java.util.OptionalInt
import java.util.stream.Stream

class BuilderProcess(
    maestro: Agent,
) : MaestroProcessHelper(maestro),
    IBuilderProcess {
    private var incorrectPositions: HashSet<PackedBlockPos>? = null
    private var observedCompleted: LongOpenHashSet? = null
    private var name: String? = null
    private var realSchematic: ISchematic? = null
    private var schematic: ISchematic? = null
    private var origin: Vec3i? = null
    private var ticks = 0
    private var paused = false
    private var layer = 0
    private var numRepeats = 0
    private var approxPlaceable: List<BlockState> = emptyList()
    var stopAtHeight = 0

    override fun build(
        name: String,
        schematic: ISchematic,
        origin: Vec3i,
    ) {
        this.name = name
        var currentSchematic: ISchematic = schematic
        this.realSchematic = null
        val buildingSelectionSchematic = schematic is SelectionSchematic
        if (Agent
                .settings()
                .buildSubstitutes.value
                .isNotEmpty()
        ) {
            @Suppress("UNCHECKED_CAST")
            val substitutes =
                Agent.settings().buildSubstitutes.value as
                    MutableMap<
                        net.minecraft.world.level.block.Block,
                        MutableList<net.minecraft.world.level.block.Block>,
                    >
            currentSchematic = SubstituteSchematic(currentSchematic, substitutes)
        }
        val mirrorValue = Agent.settings().buildSchematicMirror.value
        if (mirrorValue != net.minecraft.world.level.block.Mirror.NONE) {
            currentSchematic = MirroredSchematic(currentSchematic, mirrorValue)
        }
        if (Agent.settings().buildSchematicRotation.value != net.minecraft.world.level.block.Rotation.NONE) {
            currentSchematic = RotatedSchematic(currentSchematic, Agent.settings().buildSchematicRotation.value)
        }
        currentSchematic =
            object : MaskSchematic(currentSchematic) {
                override fun partOfMask(
                    x: Int,
                    y: Int,
                    z: Int,
                    current: BlockState,
                ): Boolean =
                    !Agent
                        .settings()
                        .buildSkipBlocks
                        .value
                        .contains(this.desiredState(x, y, z, current, emptyList()).block)
            }
        this.schematic = currentSchematic

        var x = origin.x
        var y = origin.y
        var z = origin.z
        if (Agent.settings().schematicOrientationX.value) {
            x += schematic.widthX()
        }
        if (Agent.settings().schematicOrientationY.value) {
            y += schematic.heightY()
        }
        if (Agent.settings().schematicOrientationZ.value) {
            z += schematic.lengthZ()
        }
        this.origin = Vec3i(x, y, z)
        this.paused = false
        this.layer = Agent.settings().startAtLayer.value
        this.stopAtHeight = schematic.heightY()
        if (Agent.settings().buildOnlySelection.value && buildingSelectionSchematic) {
            if (maestro.selectionManager.selections.isEmpty()) {
                log.atWarn().log("No selection set while build-only-selection enabled")
                this.stopAtHeight = 0
            } else if (Agent.settings().buildInLayers.value) {
                val minim =
                    Stream
                        .of(*maestro.selectionManager.selections)
                        .mapToInt { sel -> sel.min().y }
                        .min()
                val maxim =
                    Stream
                        .of(*maestro.selectionManager.selections)
                        .mapToInt { sel -> sel.max().y }
                        .max()
                if (minim.isPresent && maxim.isPresent) {
                    val startAtHeight =
                        if (Agent.settings().layerOrder.value) {
                            y + schematic.heightY() - maxim.asInt
                        } else {
                            minim.asInt - y
                        }
                    this.stopAtHeight =
                        (
                            if (Agent.settings().layerOrder.value) {
                                y + schematic.heightY() - minim.asInt
                            } else {
                                maxim.asInt - y
                            }
                        ) + 1
                    this.layer =
                        maxOf(
                            this.layer,
                            startAtHeight / Agent.settings().layerHeight.value,
                        )
                    log
                        .atDebug()
                        .addKeyValue("schematic_y", y)
                        .addKeyValue("schematic_height", schematic.heightY())
                        .log("Schematic position info")
                    log
                        .atDebug()
                        .addKeyValue("selection_min_y", minim.asInt)
                        .addKeyValue("selection_max_y", maxim.asInt)
                        .log("Selection bounds")
                    log
                        .atDebug()
                        .addKeyValue("height_start", startAtHeight)
                        .addKeyValue("height_end", this.stopAtHeight)
                        .log("Relevant height range")
                }
            }
        }

        this.numRepeats = 0
        this.observedCompleted = LongOpenHashSet()
        this.incorrectPositions = null
    }

    override fun resume() {
        paused = false
    }

    override fun pause() {
        paused = true
    }

    override fun isPaused(): Boolean = paused

    override fun build(
        name: String,
        schematic: File,
        origin: Vec3i,
    ): Boolean {
        val format = SchematicSystem.INSTANCE.getByFile(schematic)
        if (format.isEmpty) {
            return false
        }
        val parsed: IStaticSchematic
        try {
            parsed = format.get().parse(FileInputStream(schematic))
        } catch (e: Exception) {
            log
                .atError()
                .setCause(e)
                .addKeyValue("schematic", schematic.absolutePath)
                .addKeyValue("format", format.get()::class.java.simpleName)
                .log("Failed to parse schematic")
            return false
        }
        val schem = applyMapArtAndSelection(origin, parsed)
        build(name, schem, origin)
        return true
    }

    private fun applyMapArtAndSelection(
        origin: Vec3i,
        parsed: IStaticSchematic,
    ): ISchematic {
        var schematic: ISchematic = parsed
        if (Agent.settings().mapArtMode.value) {
            schematic = MapArtSchematic(parsed)
        }
        if (Agent.settings().buildOnlySelection.value) {
            schematic = SelectionSchematic(schematic, origin, maestro.selectionManager.selections)
        }
        return schematic
    }

    override fun buildOpenLitematic(i: Int) {
        if (LitematicaHelper.isLitematicaPresent()) {
            if (LitematicaHelper.hasLoadedSchematic(i)) {
                val schematic = LitematicaHelper.getSchematic(i)
                val correctedOrigin = schematic.b
                val schematic2 = applyMapArtAndSelection(correctedOrigin, schematic.a)
                build(schematic.a.toString(), schematic2, correctedOrigin)
            } else {
                log.atWarn().addKeyValue("index", i + 1).log("No placement found at index")
            }
        } else {
            log.atWarn().log("Litematica is not present")
        }
    }

    override fun clearArea(
        corner1: BlockPos,
        corner2: BlockPos,
    ) {
        val origin =
            BlockPos(
                minOf(corner1.x, corner2.x),
                minOf(corner1.y, corner2.y),
                minOf(corner1.z, corner2.z),
            )
        val widthX = kotlin.math.abs(corner1.x - corner2.x) + 1
        val heightY = kotlin.math.abs(corner1.y - corner2.y) + 1
        val lengthZ = kotlin.math.abs(corner1.z - corner2.z) + 1
        build("clear area", FillSchematic(widthX, heightY, lengthZ, Blocks.AIR.defaultBlockState()), origin)
    }

    override fun getApproxPlaceable(): List<BlockState> = ArrayList(approxPlaceable)

    override fun isActive(): Boolean = schematic != null

    fun placeAt(
        x: Int,
        y: Int,
        z: Int,
        current: BlockState,
    ): BlockState? {
        if (!isActive) {
            return null
        }
        val currentOrigin = origin ?: return null
        val currentSchematic = schematic ?: return null
        if (!currentSchematic.inSchematic(x - currentOrigin.x, y - currentOrigin.y, z - currentOrigin.z, current)) {
            return null
        }
        val state =
            currentSchematic.desiredState(
                x - currentOrigin.x,
                y - currentOrigin.y,
                z - currentOrigin.z,
                current,
                this.approxPlaceable,
            )
        if (state.block is AirBlock) {
            return null
        }
        return state
    }

    private fun toBreakNearPlayer(bcc: BuilderCalculationContext): Optional<net.minecraft.util.Tuple<PackedBlockPos, Rotation>> {
        val center = ctx.playerFeet()
        val pathStart = maestro.pathingBehavior.pathStart()
        for (dx in -5..5) {
            for (dy in (if (Agent.settings().breakFromAbove.value) -1 else 0)..5) {
                for (dz in -5..5) {
                    val x = center.x + dx
                    val y = center.y + dy
                    val z = center.z + dz
                    if (dy == -1 && pathStart != null && x == pathStart.x && z == pathStart.z) {
                        continue
                    }
                    val desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z)) ?: continue
                    val curr = bcc.bsi.get0(x, y, z)
                    if (curr.block !is AirBlock &&
                        curr.block != Blocks.WATER &&
                        curr.block != Blocks.LAVA &&
                        !valid(curr, desired, false)
                    ) {
                        val pos = PackedBlockPos(x, y, z)
                        val rot = RotationUtils.reachable(ctx, pos.toBlockPos(), ctx.playerController().blockReachDistance)
                        if (rot.isPresent) {
                            return Optional.of(net.minecraft.util.Tuple(pos, rot.get()))
                        }
                    }
                }
            }
        }
        return Optional.empty()
    }

    class Placement(
        val hotbarSelection: Int,
        val placeAgainst: BlockPos,
        val side: Direction,
        val rot: Rotation,
    )

    private fun searchForPlaceables(
        bcc: BuilderCalculationContext,
        desirableOnHotbar: MutableList<BlockState>,
    ): Optional<Placement> {
        val center = ctx.playerFeet()
        for (dx in -5..5) {
            for (dy in -5..1) {
                for (dz in -5..5) {
                    val x = center.x + dx
                    val y = center.y + dy
                    val z = center.z + dz
                    val desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z)) ?: continue
                    val curr = bcc.bsi.get0(x, y, z)
                    if (MovementHelper.isReplaceable(x, y, z, curr, bcc.bsi) && !valid(curr, desired, false)) {
                        if (dy == 1 && bcc.bsi.get0(x, y + 1, z).block is AirBlock) {
                            continue
                        }
                        desirableOnHotbar.add(desired)
                        val opt = possibleToPlace(desired, x, y, z, bcc.bsi)
                        if (opt.isPresent) {
                            return opt
                        }
                    }
                }
            }
        }
        return Optional.empty()
    }

    fun placementPlausible(
        pos: BlockPos,
        state: BlockState,
    ): Boolean {
        val voxelshape = state.getCollisionShape(ctx.world(), pos)
        return voxelshape.isEmpty || ctx.world().isUnobstructed(null, voxelshape.move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()))
    }

    private fun possibleToPlace(
        toPlace: BlockState,
        x: Int,
        y: Int,
        z: Int,
        bsi: BlockStateInterface,
    ): Optional<Placement> {
        for (against in Direction.entries) {
            val placeAgainstPos = PackedBlockPos(x, y, z).relative(against)
            val placeAgainstState = bsi.get0(placeAgainstPos.x, placeAgainstPos.y, placeAgainstPos.z)
            if (MovementHelper.isReplaceable(placeAgainstPos.x, placeAgainstPos.y, placeAgainstPos.z, placeAgainstState, bsi)) {
                continue
            }
            if (!toPlace.canSurvive(ctx.world(), PackedBlockPos(x, y, z).toBlockPos())) {
                continue
            }
            if (!placementPlausible(PackedBlockPos(x, y, z).toBlockPos(), toPlace)) {
                continue
            }
            val shape = placeAgainstState.getShape(ctx.world(), placeAgainstPos.toBlockPos())
            if (shape.isEmpty) {
                continue
            }
            val aabb = shape.bounds()
            for (placementMultiplier in aabbSideMultipliers(against)) {
                val placeX = placeAgainstPos.x + aabb.minX * placementMultiplier.x + aabb.maxX * (1 - placementMultiplier.x)
                val placeY = placeAgainstPos.y + aabb.minY * placementMultiplier.y + aabb.maxY * (1 - placementMultiplier.y)
                val placeZ = placeAgainstPos.z + aabb.minZ * placementMultiplier.z + aabb.maxZ * (1 - placementMultiplier.z)
                val rot =
                    RotationUtils.calcRotationFromVec3d(
                        RayTraceUtils.inferSneakingEyePosition(ctx.player()),
                        Vec3(placeX, placeY, placeZ),
                        ctx.playerRotations(),
                    )
                val actualRot = maestro.lookBehavior.getAimProcessor().peekRotation(rot)
                val result =
                    RayTraceUtils.rayTraceTowards(
                        ctx.player(),
                        actualRot,
                        ctx.playerController().blockReachDistance,
                        true,
                    )
                if (result != null &&
                    result.type == HitResult.Type.BLOCK &&
                    (result as BlockHitResult).blockPos == placeAgainstPos.toBlockPos() &&
                    result.direction == against.opposite
                ) {
                    val hotbar = hasAnyItemThatWouldPlace(toPlace, result, actualRot)
                    if (hotbar.isPresent) {
                        return Optional.of(
                            Placement(
                                hotbar.asInt,
                                placeAgainstPos.toBlockPos(),
                                against.opposite,
                                rot,
                            ),
                        )
                    }
                }
            }
        }
        return Optional.empty()
    }

    private fun hasAnyItemThatWouldPlace(
        desired: BlockState,
        result: HitResult,
        rot: Rotation,
    ): OptionalInt {
        for (i in 0 until 9) {
            val stack = ctx.player().inventory.items[i]
            if (stack.isEmpty || stack.item !is BlockItem) {
                continue
            }
            val originalYaw = ctx.player().yRot
            val originalPitch = ctx.player().xRot
            ctx.player().yRot = rot.yaw
            ctx.player().xRot = rot.pitch
            val meme =
                BlockPlaceContext(
                    object :
                        UseOnContext(ctx.world(), ctx.player(), InteractionHand.MAIN_HAND, stack, result as BlockHitResult) {},
                )
            val wouldBePlaced = (stack.item as BlockItem).block.getStateForPlacement(meme)
            ctx.player().yRot = originalYaw
            ctx.player().xRot = originalPitch
            if (wouldBePlaced == null) {
                continue
            }
            if (!meme.canPlace()) {
                continue
            }
            if (valid(wouldBePlaced, desired, true)) {
                return OptionalInt.of(i)
            }
        }
        return OptionalInt.empty()
    }

    override fun onTick(
        calcFailed: Boolean,
        isSafeToCancel: Boolean,
    ): PathingCommand? = onTick(calcFailed, isSafeToCancel, 0)

    private fun onTick(
        calcFailed: Boolean,
        isSafeToCancel: Boolean,
        recursions: Int,
    ): PathingCommand? {
        if (recursions > 100) {
            return PathingCommand(null, PathingCommandType.SET_GOAL_AND_PATH)
        }
        approxPlaceable = approxPlaceable(36)
        if (maestro.inputOverrideHandler.isInputForcedDown(Input.CLICK_LEFT)) {
            ticks = 5
        } else {
            ticks--
        }
        // Don't clear all keys - preserve CLICK_LEFT/RIGHT across path revalidation
        // Interaction keys are managed explicitly based on building state
        if (paused) {
            return PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL)
        }
        val currentOrigin = origin ?: return null

        if (Agent.settings().buildInLayers.value) {
            if (realSchematic == null) {
                realSchematic = schematic
            }
            val realSchematicLocal = this.realSchematic ?: return null
            val minYInclusive: Int
            val maxYInclusive: Int
            if (Agent.settings().layerOrder.value) {
                maxYInclusive = realSchematicLocal.heightY() - 1
                minYInclusive = realSchematicLocal.heightY() - layer * Agent.settings().layerHeight.value
            } else {
                maxYInclusive = layer * Agent.settings().layerHeight.value - 1
                minYInclusive = 0
            }
            schematic =
                object : ISchematic {
                    override fun desiredState(
                        x: Int,
                        y: Int,
                        z: Int,
                        current: BlockState,
                        approxPlaceable: List<BlockState>,
                    ): BlockState = realSchematicLocal.desiredState(x, y, z, current, this@BuilderProcess.approxPlaceable)

                    override fun inSchematic(
                        x: Int,
                        y: Int,
                        z: Int,
                        currentState: BlockState,
                    ): Boolean =
                        super.inSchematic(x, y, z, currentState) &&
                            y >= minYInclusive &&
                            y <= maxYInclusive &&
                            realSchematicLocal.inSchematic(x, y, z, currentState)

                    override fun reset() {
                        realSchematicLocal.reset()
                    }

                    override fun widthX(): Int = realSchematicLocal.widthX()

                    override fun heightY(): Int = realSchematicLocal.heightY()

                    override fun lengthZ(): Int = realSchematicLocal.lengthZ()
                }
        }
        val bcc = BuilderCalculationContext()
        if (!recalc(bcc)) {
            if (Agent.settings().buildInLayers.value && layer * Agent.settings().layerHeight.value < stopAtHeight) {
                log.atInfo().addKeyValue("layer_number", layer).log("Starting layer")
                layer++
                return onTick(calcFailed, isSafeToCancel, recursions + 1)
            }
            val repeat = Agent.settings().buildRepeat.value
            val max = Agent.settings().buildRepeatCount.value
            numRepeats++
            if (repeat == Vec3i(0, 0, 0) || (max != -1 && numRepeats >= max)) {
                log.atInfo().log("Building complete")
                if (Agent.settings().notificationOnBuildFinished.value) {
                    logNotification("Done building", false)
                }
                onLostControl()
                return null
            }
            layer = 0
            origin = BlockPos(currentOrigin).offset(repeat)
            if (!Agent.settings().buildRepeatSneaky.value) {
                schematic?.reset()
            }
            log
                .atInfo()
                .addKeyValue("repeat_vector", repeat)
                .addKeyValue("new_origin", origin)
                .log("Repeating build")
            return onTick(calcFailed, isSafeToCancel, recursions + 1)
        }
        if (Agent.settings().distanceTrim.value) {
            trim()
        }

        val toBreak = toBreakNearPlayer(bcc)
        if (toBreak.isPresent && isSafeToCancel && ctx.player().onGround()) {
            val rot = toBreak.get().b
            val pos = toBreak.get().a
            maestro.lookBehavior.updateTarget(rot, true)
            MovementHelper.switchToBestToolFor(ctx, bcc[pos.toBlockPos()])
            if (ctx.player().isCrouching) {
                maestro.inputOverrideHandler.setInputForceState(Input.SNEAK, true)
            }
            if (ctx.isLookingAt(pos.toBlockPos()) || ctx.playerRotations().isReallyCloseTo(rot)) {
                maestro.inputOverrideHandler.setInputForceState(Input.CLICK_LEFT, true)
            }
            return PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL)
        }
        val desirableOnHotbar = mutableListOf<BlockState>()
        val toPlace = searchForPlaceables(bcc, desirableOnHotbar)
        if (toPlace.isPresent && isSafeToCancel && ctx.player().onGround() && ticks <= 0) {
            val rot = toPlace.get().rot
            maestro.lookBehavior.updateTarget(rot, true)
            ctx.player().inventory.selected = toPlace.get().hotbarSelection
            maestro.inputOverrideHandler.setInputForceState(Input.SNEAK, true)
            if ((
                    ctx.isLookingAt(toPlace.get().placeAgainst) &&
                        (ctx.objectMouseOver() as BlockHitResult).direction == toPlace.get().side
                ) ||
                ctx.playerRotations().isReallyCloseTo(rot)
            ) {
                maestro.inputOverrideHandler.setInputForceState(Input.CLICK_RIGHT, true)
            }
            return PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL)
        }

        if (Agent.settings().allowInventory.value) {
            val usefulSlots = mutableListOf<Int>()
            val noValidHotbarOption = mutableListOf<BlockState>()
            outer@ for (desired in desirableOnHotbar) {
                for (i in 0 until 9) {
                    if (valid(approxPlaceable[i], desired, true)) {
                        usefulSlots.add(i)
                        continue@outer
                    }
                }
                noValidHotbarOption.add(desired)
            }

            outer@ for (i in 9 until 36) {
                for (desired in noValidHotbarOption) {
                    if (valid(approxPlaceable[i], desired, true)) {
                        if (!maestro.inventoryBehavior.attemptToPutOnHotbar(i) { slot -> usefulSlots.contains(slot) }) {
                            return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                        }
                        break@outer
                    }
                }
            }
        }

        var goal = assemble(bcc, approxPlaceable.subList(0, 9))
        if (goal == null) {
            goal = assemble(bcc, approxPlaceable, true)
            if (goal == null) {
                val realSchematicLocal = realSchematic
                if (Agent.settings().skipFailedLayers.value &&
                    Agent.settings().buildInLayers.value &&
                    realSchematicLocal != null &&
                    layer * Agent.settings().layerHeight.value < realSchematicLocal.heightY()
                ) {
                    log.atInfo().addKeyValue("layer_number", layer).log("Skipping unconstructable layer")
                    layer++
                    return onTick(calcFailed, isSafeToCancel, recursions + 1)
                }
                log.atInfo().log("Build paused - use 'resume' to continue or 'cancel' to stop")
                paused = true
                return PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
            }
        }
        return PathingCommandContext(goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH, bcc)
    }

    private fun recalc(bcc: BuilderCalculationContext): Boolean {
        if (incorrectPositions == null) {
            incorrectPositions = HashSet()
            fullRecalc(bcc)
            if (incorrectPositions!!.isEmpty()) {
                return false
            }
        }
        recalcNearby(bcc)
        if (incorrectPositions!!.isEmpty()) {
            fullRecalc(bcc)
        }
        return incorrectPositions!!.isNotEmpty()
    }

    private fun trim() {
        val copy = HashSet(incorrectPositions!!)
        copy.removeIf { pos -> pos.distSqr(PackedBlockPos(ctx.player().blockPosition())) > 200 }
        if (copy.isNotEmpty()) {
            incorrectPositions = copy
        }
    }

    private fun recalcNearby(bcc: BuilderCalculationContext) {
        val center = ctx.playerFeet()
        val radius = Agent.settings().builderTickScanRadius.value
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    val x = center.x + dx
                    val y = center.y + dy
                    val z = center.z + dz
                    val desired = bcc.getSchematic(x, y, z, bcc.bsi.get0(x, y, z))
                    if (desired != null) {
                        val pos = PackedBlockPos(x, y, z)
                        if (valid(bcc.bsi.get0(x, y, z), desired, false)) {
                            incorrectPositions!!.remove(pos)
                            observedCompleted!!.add(pos.packed)
                        } else {
                            incorrectPositions!!.add(pos)
                            observedCompleted!!.remove(pos.packed)
                        }
                    }
                }
            }
        }
    }

    private fun fullRecalc(bcc: BuilderCalculationContext) {
        val currentSchematic = schematic ?: return
        val currentOrigin = origin ?: return
        incorrectPositions = HashSet()
        for (y in 0 until currentSchematic.heightY()) {
            for (z in 0 until currentSchematic.lengthZ()) {
                for (x in 0 until currentSchematic.widthX()) {
                    val blockX = x + currentOrigin.x
                    val blockY = y + currentOrigin.y
                    val blockZ = z + currentOrigin.z
                    val current = bcc.bsi.get0(blockX, blockY, blockZ)
                    if (!currentSchematic.inSchematic(x, y, z, current)) {
                        continue
                    }
                    if (bcc.bsi.worldContainsLoadedChunk(blockX, blockZ)) {
                        if (valid(
                                bcc.bsi.get0(blockX, blockY, blockZ),
                                currentSchematic.desiredState(x, y, z, current, this.approxPlaceable),
                                false,
                            )
                        ) {
                            observedCompleted!!.add(PackedBlockPos(blockX, blockY, blockZ).packed)
                        } else {
                            incorrectPositions!!.add(PackedBlockPos(blockX, blockY, blockZ))
                            observedCompleted!!.remove(PackedBlockPos(blockX, blockY, blockZ).packed)
                            if (incorrectPositions!!.size > Agent.settings().incorrectSize.value) {
                                return
                            }
                        }
                        continue
                    }
                    if (!observedCompleted!!.contains(PackedBlockPos(blockX, blockY, blockZ).packed)) {
                        incorrectPositions!!.add(PackedBlockPos(blockX, blockY, blockZ))
                        if (incorrectPositions!!.size > Agent.settings().incorrectSize.value) {
                            return
                        }
                    }
                }
            }
        }
    }

    private fun assemble(
        bcc: BuilderCalculationContext,
        approxPlaceable: List<BlockState>,
    ): Goal? = assemble(bcc, approxPlaceable, false)

    private fun assemble(
        bcc: BuilderCalculationContext,
        approxPlaceable: List<BlockState>,
        logMissing: Boolean,
    ): Goal? {
        val placeable = mutableListOf<PackedBlockPos>()
        val breakable = mutableListOf<PackedBlockPos>()
        val sourceLiquids = mutableListOf<PackedBlockPos>()
        val flowingLiquids = mutableListOf<PackedBlockPos>()
        val missing = mutableMapOf<BlockState, Int>()
        val outOfBounds = mutableListOf<PackedBlockPos>()
        incorrectPositions!!.forEach { pos ->
            val state = bcc.bsi.get0(pos.x, pos.y, pos.z)
            if (state.block is AirBlock) {
                val desired = bcc.getSchematic(pos.x, pos.y, pos.z, state)
                if (desired == null) {
                    outOfBounds.add(pos)
                } else if (containsBlockState(approxPlaceable, desired)) {
                    placeable.add(pos)
                } else {
                    missing[desired] = 1 + missing.getOrDefault(desired, 0)
                }
            } else {
                if (state.block is LiquidBlock) {
                    if (!MovementHelper.possiblyFlowing(state)) {
                        sourceLiquids.add(pos)
                    } else {
                        flowingLiquids.add(pos)
                    }
                } else {
                    breakable.add(pos)
                }
            }
        }
        outOfBounds.forEach { incorrectPositions!!.remove(it) }
        val toBreak = mutableListOf<Goal>()
        breakable.forEach { pos -> toBreak.add(breakGoal(pos.toBlockPos(), bcc)) }
        val toPlace = mutableListOf<Goal>()
        placeable.forEach { pos ->
            if (!placeable.contains(pos.below()) && !placeable.contains(pos.below(2))) {
                toPlace.add(placementGoal(pos.toBlockPos(), bcc))
            }
        }
        sourceLiquids.forEach { pos -> toPlace.add(GoalBlock(pos.above().toBlockPos())) }

        if (toPlace.isNotEmpty()) {
            return JankyGoalComposite(
                GoalComposite(*toPlace.toTypedArray()),
                GoalComposite(*toBreak.toTypedArray()),
            )
        }
        if (toBreak.isEmpty()) {
            if (logMissing && missing.isNotEmpty()) {
                log
                    .atWarn()
                    .addKeyValue("missing_block_types", missing.size)
                    .log("Missing materials for schematic")
                missing.forEach { (blockState, count) ->
                    log
                        .atDebug()
                        .addKeyValue("block", blockState)
                        .addKeyValue("count", count)
                        .log("Missing material details")
                }
            }
            if (logMissing && flowingLiquids.isNotEmpty()) {
                log
                    .atWarn()
                    .addKeyValue("liquid_positions", flowingLiquids.size)
                    .log("Unreplaceable flowing liquids in schematic")
                flowingLiquids.forEach { p ->
                    log
                        .atDebug()
                        .addKeyValue("position_x", p.x)
                        .addKeyValue("position_y", p.y)
                        .addKeyValue("position_z", p.z)
                        .log("Unreplaceable liquid location")
                }
            }
            return null
        }
        return GoalComposite(*toBreak.toTypedArray())
    }

    class JankyGoalComposite(
        private val primary: Goal,
        private val fallback: Goal,
    ) : Goal {
        override fun isInGoal(
            x: Int,
            y: Int,
            z: Int,
        ): Boolean = primary.isInGoal(x, y, z) || fallback.isInGoal(x, y, z)

        override fun heuristic(
            x: Int,
            y: Int,
            z: Int,
        ): Double = primary.heuristic(x, y, z)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val goal = other as JankyGoalComposite
            return primary == goal.primary && fallback == goal.fallback
        }

        override fun hashCode(): Int {
            var hash = -1701079641
            hash = hash * 1196141026 + primary.hashCode()
            hash = hash * -80327868 + fallback.hashCode()
            return hash
        }

        override fun toString(): String = "JankyComposite Primary: $primary Fallback: $fallback"
    }

    class GoalBreak(
        pos: BlockPos,
    ) : GoalGetToBlock(pos) {
        override fun isInGoal(
            x: Int,
            y: Int,
            z: Int,
        ): Boolean {
            if (y > this.y) {
                return false
            }
            return super.isInGoal(x, y, z)
        }

        override fun toString(): String =
            String.format(
                "GoalBreak{x=%s,y=%s,z=%s}",
                SettingsUtil.maybeCensor(this.x),
                SettingsUtil.maybeCensor(this.y),
                SettingsUtil.maybeCensor(this.z),
            )

        override fun hashCode(): Int = super.hashCode() * 1636324008
    }

    private fun placementGoal(
        pos: BlockPos,
        bcc: BuilderCalculationContext,
    ): Goal {
        if (ctx.world().getBlockState(pos).block !is AirBlock) {
            return GoalPlace(pos)
        }
        val allowSameLevel = ctx.world().getBlockState(pos.above()).block !is AirBlock
        val current = ctx.world().getBlockState(pos)
        for (facing in Movement.HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP) {
            val schematicState = bcc.getSchematic(pos.x, pos.y, pos.z, current)
            if (MovementHelper.canPlaceAgainst(ctx, pos.relative(facing)) &&
                schematicState != null &&
                placementPlausible(pos, schematicState)
            ) {
                return GoalAdjacent(pos, pos.relative(facing), allowSameLevel)
            }
        }
        return GoalPlace(pos)
    }

    private fun breakGoal(
        pos: BlockPos,
        bcc: BuilderCalculationContext,
    ): Goal {
        if (Agent.settings().goalBreakFromAbove.value &&
            bcc.bsi.get0(pos.above()).block is AirBlock &&
            bcc.bsi.get0(pos.above(2)).block is AirBlock
        ) {
            return JankyGoalComposite(
                GoalBreak(pos),
                object : GoalGetToBlock(pos.above()) {
                    override fun isInGoal(
                        x: Int,
                        y: Int,
                        z: Int,
                    ): Boolean {
                        if (y > this.y || (x == this.x && y == this.y && z == this.z)) {
                            return false
                        }
                        return super.isInGoal(x, y, z)
                    }
                },
            )
        }
        return GoalBreak(pos)
    }

    class GoalAdjacent(
        pos: BlockPos,
        private val no: BlockPos,
        private val allowSameLevel: Boolean,
    ) : GoalGetToBlock(pos) {
        override fun isInGoal(
            x: Int,
            y: Int,
            z: Int,
        ): Boolean {
            if (x == this.x && y == this.y && z == this.z) {
                return false
            }
            if (x == no.x && y == no.y && z == no.z) {
                return false
            }
            if (!allowSameLevel && y == this.y - 1) {
                return false
            }
            if (y < this.y - 1) {
                return false
            }
            return super.isInGoal(x, y, z)
        }

        override fun heuristic(
            x: Int,
            y: Int,
            z: Int,
        ): Double = this.y * 100.0 + super.heuristic(x, y, z)

        override fun equals(other: Any?): Boolean {
            if (!super.equals(other)) {
                return false
            }
            val goal = other as GoalAdjacent
            return allowSameLevel == goal.allowSameLevel && no == goal.no
        }

        override fun hashCode(): Int {
            var hash = 806368046
            hash = hash * 1412661222 + super.hashCode()
            hash = hash * 1730799370 + PackedBlockPos(no.x, no.y, no.z).packed.toInt()
            hash = hash * 260592149 + (if (allowSameLevel) -1314802005 else 1565710265)
            return hash
        }

        override fun toString(): String =
            String.format(
                "GoalAdjacent{x=%s,y=%s,z=%s}",
                SettingsUtil.maybeCensor(x),
                SettingsUtil.maybeCensor(y),
                SettingsUtil.maybeCensor(z),
            )
    }

    class GoalPlace(
        placeAt: BlockPos,
    ) : GoalBlock(placeAt.above()) {
        override fun heuristic(
            x: Int,
            y: Int,
            z: Int,
        ): Double = this.y * 100.0 + super.heuristic(x, y, z)

        override fun hashCode(): Int = super.hashCode() * 1910811835

        override fun toString(): String =
            String.format(
                "GoalPlace{x=%s,y=%s,z=%s}",
                SettingsUtil.maybeCensor(x),
                SettingsUtil.maybeCensor(y),
                SettingsUtil.maybeCensor(z),
            )
    }

    override fun onLostControl() {
        // Clear interaction keys when losing control
        maestro.inputOverrideHandler.clearInteractionKeys()
        incorrectPositions = null
        name = null
        schematic = null
        realSchematic = null
        layer = Agent.settings().startAtLayer.value
        numRepeats = 0
        paused = false
        observedCompleted = null
    }

    override fun displayName0(): String = if (paused) "Builder Paused" else "Building $name"

    override fun getMinLayer(): Optional<Int> =
        if (Agent.settings().buildInLayers.value) {
            Optional.of(this.layer)
        } else {
            Optional.empty()
        }

    override fun getMaxLayer(): Optional<Int> =
        if (Agent.settings().buildInLayers.value) {
            Optional.of(this.stopAtHeight)
        } else {
            Optional.empty()
        }

    private fun approxPlaceable(size: Int): List<BlockState> {
        val result = mutableListOf<BlockState>()
        for (i in 0 until size) {
            val stack = ctx.player().inventory.items[i]
            if (stack.isEmpty || stack.item !is BlockItem) {
                result.add(Blocks.AIR.defaultBlockState())
                continue
            }
            val itemState =
                (stack.item as BlockItem)
                    .block
                    .getStateForPlacement(
                        BlockPlaceContext(
                            object :
                                UseOnContext(
                                    ctx.world(),
                                    ctx.player(),
                                    InteractionHand.MAIN_HAND,
                                    stack,
                                    BlockHitResult(
                                        Vec3(
                                            ctx.player().position().x,
                                            ctx.player().position().y,
                                            ctx.player().position().z,
                                        ),
                                        Direction.UP,
                                        ctx.playerFeet().toBlockPos(),
                                        false,
                                    ),
                                ) {},
                        ),
                    )
            if (itemState != null) {
                result.add(itemState)
            } else {
                result.add(Blocks.AIR.defaultBlockState())
            }
        }
        return result
    }

    inner class BuilderCalculationContext : CalculationContext(this@BuilderProcess.maestro, true) {
        private val placeable: List<BlockState> = approxPlaceable(9)
        private val schematic: ISchematic? = this@BuilderProcess.schematic
        private val originX: Int = origin?.x ?: 0
        private val originY: Int = origin?.y ?: 0
        private val originZ: Int = origin?.z ?: 0

        init {
            this.jumpPenalty += 10.0
            this.backtrackCostFavoringCoefficient = 1.0
        }

        fun getSchematic(
            x: Int,
            y: Int,
            z: Int,
            current: BlockState,
        ): BlockState? {
            val currentSchematic = schematic ?: return null
            return if (currentSchematic.inSchematic(x - originX, y - originY, z - originZ, current)) {
                currentSchematic.desiredState(
                    x - originX,
                    y - originY,
                    z - originZ,
                    current,
                    this@BuilderProcess.approxPlaceable,
                )
            } else {
                null
            }
        }

        override fun costOfPlacingAt(
            x: Int,
            y: Int,
            z: Int,
            current: BlockState,
        ): Double {
            if (isPossiblyProtected(x, y, z) || !worldBorder.canPlaceAt(x, z)) {
                return COST_INF
            }
            val sch = getSchematic(x, y, z, current)
            if (sch != null) {
                if (sch.block is AirBlock) {
                    return placeBlockCost * Agent.settings().placeIncorrectBlockPenaltyMultiplier.value
                }
                if (placeable.contains(sch)) {
                    return 0.0
                }
                if (!hasThrowaway) {
                    return COST_INF
                }
                return placeBlockCost * 1.5 * Agent.settings().placeIncorrectBlockPenaltyMultiplier.value
            } else {
                return if (hasThrowaway) {
                    placeBlockCost
                } else {
                    COST_INF
                }
            }
        }

        override fun breakCostMultiplierAt(
            x: Int,
            y: Int,
            z: Int,
            current: BlockState,
        ): Double {
            if ((!allowBreak && !allowBreakAnyway.contains(current.block)) || isPossiblyProtected(x, y, z)) {
                return COST_INF
            }
            val sch = getSchematic(x, y, z, current)
            if (sch != null) {
                if (sch.block is AirBlock) {
                    return 1.0
                }
                return if (valid(bsi.get0(x, y, z), sch, false)) {
                    Agent.settings().breakCorrectBlockPenaltyMultiplier.value
                } else {
                    1.0
                }
            } else {
                return 1.0
            }
        }
    }

    companion object {
        private val log: Logger = MaestroLogger.get("build")

        private val ORIENTATION_PROPS: Set<Property<*>> =
            ImmutableSet.of(
                RotatedPillarBlock.AXIS,
                HorizontalDirectionalBlock.FACING,
                StairBlock.FACING,
                StairBlock.HALF,
                StairBlock.SHAPE,
                PipeBlock.NORTH,
                PipeBlock.EAST,
                PipeBlock.SOUTH,
                PipeBlock.WEST,
                PipeBlock.UP,
                TrapDoorBlock.OPEN,
                TrapDoorBlock.HALF,
            )

        private fun aabbSideMultipliers(side: Direction): Array<Vec3> =
            when (side) {
                Direction.UP ->
                    arrayOf(
                        Vec3(0.5, 1.0, 0.5),
                        Vec3(0.1, 1.0, 0.5),
                        Vec3(0.9, 1.0, 0.5),
                        Vec3(0.5, 1.0, 0.1),
                        Vec3(0.5, 1.0, 0.9),
                    )
                Direction.DOWN ->
                    arrayOf(
                        Vec3(0.5, 0.0, 0.5),
                        Vec3(0.1, 0.0, 0.5),
                        Vec3(0.9, 0.0, 0.5),
                        Vec3(0.5, 0.0, 0.1),
                        Vec3(0.5, 0.0, 0.9),
                    )
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST -> {
                    val x = if (side.stepX == 0) 0.5 else (1 + side.stepX) / 2.0
                    val z = if (side.stepZ == 0) 0.5 else (1 + side.stepZ) / 2.0
                    arrayOf(Vec3(x, 0.25, z), Vec3(x, 0.75, z))
                }
            }

        private fun sameBlockstate(
            first: BlockState,
            second: BlockState,
        ): Boolean {
            if (first.block != second.block) {
                return false
            }
            val ignoreDirection = Agent.settings().buildIgnoreDirection.value
            val ignoredProps = Agent.settings().buildIgnoreProperties.value
            if (!ignoreDirection && ignoredProps.isEmpty()) {
                return first == second
            }
            val map1 = first.values
            val map2 = second.values
            for (prop in map1.keys) {
                if (map1[prop] != map2[prop] &&
                    !(ignoreDirection && ORIENTATION_PROPS.contains(prop)) &&
                    !ignoredProps.contains(prop.name)
                ) {
                    return false
                }
            }
            return true
        }

        private fun containsBlockState(
            states: Collection<BlockState>,
            state: BlockState,
        ): Boolean {
            for (testee in states) {
                if (sameBlockstate(testee, state)) {
                    return true
                }
            }
            return false
        }

        private fun valid(
            current: BlockState,
            desired: BlockState?,
            itemVerify: Boolean,
        ): Boolean {
            if (desired == null) {
                return true
            }
            if (current.block is LiquidBlock && Agent.settings().okIfWater.value) {
                return true
            }
            if (current.block is AirBlock && desired.block is AirBlock) {
                return true
            }
            if (current.block is AirBlock &&
                Agent
                    .settings()
                    .okIfAir.value
                    .contains(desired.block)
            ) {
                return true
            }
            if (desired.block is AirBlock &&
                Agent
                    .settings()
                    .buildIgnoreBlocks.value
                    .contains(current.block)
            ) {
                return true
            }
            if (current.block !is AirBlock && Agent.settings().buildIgnoreExisting.value && !itemVerify) {
                return true
            }
            if (Agent
                    .settings()
                    .buildValidSubstitutes
                    .value
                    .getOrDefault(desired.block, emptyList())
                    .contains(current.block) &&
                !itemVerify
            ) {
                return true
            }
            if (current == desired) {
                return true
            }
            return sameBlockstate(current, desired)
        }
    }
}
