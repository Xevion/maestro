package maestro.debug

import maestro.Agent
import maestro.api.pathing.goals.Goal
import maestro.api.pathing.goals.GoalComposite
import maestro.api.player.PlayerContext
import maestro.pathing.path.PathExecutor
import maestro.rendering.IGoalRenderPos
import maestro.task.MineTask
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.PI
import kotlin.math.sin

/**
 * Represents a block with assigned rendering priority, color, and opacity.
 *
 * @property pos Block position
 * @property priority Priority tier (0 = highest, 6 = lowest)
 * @property color Render color
 * @property opacity Transparency (0.0-1.0)
 * @property sides Which sides to render
 */
data class PriorityBlock(
    val pos: BlockPos,
    val priority: Int,
    val color: Color,
    val opacity: Float,
    val sides: SideHighlights,
)

/**
 * Classifies blocks into priority tiers for highlight rendering.
 *
 * Priority System (highest to lowest):
 * 0. Active Mining Targets - Goal blocks in toBreak() (cyan, pulsing)
 * 1. Immediate Path - pathPosition+1 (yellow, bright)
 * 2. Active Actions - toBreak/toPlace/toWalkInto non-goals (red/green/magenta)
 * 3. Current Path - Future path positions (cyan, desaturated)
 * 4. Pending Goals - Goal blocks NOT in toBreak() (lime green, desaturated)
 * 5. Discovered Ores - knownOreLocations NOT in goals (ore-specific, desaturated)
 * 6. Historical Path - Past path positions (gray, very faint)
 */
object BlockPriorityClassifier {
    /**
     * Classify all blocks into priority tiers.
     *
     * @param goal Current pathfinding goal
     * @param executor Current path executor
     * @param mineTask Mining process (for ore locations)
     * @param ctx Player context
     * @return Map of block positions to priority blocks
     */
    fun classifyBlocks(
        goal: Goal?,
        executor: PathExecutor?,
        mineTask: MineTask?,
        ctx: PlayerContext,
    ): Map<BlockPos, PriorityBlock> {
        val settings = Agent.settings()
        val classified = mutableMapOf<BlockPos, PriorityBlock>()

        // Extract goal positions once for reuse
        val goalPositions =
            if (goal != null) extractGoalPositions(goal) else emptySet()

        // Priority 0: Active mining targets (goal ∩ toBreak)
        if (goal != null && executor != null && settings.highlightOpacityActiveMining.value > 0.0f) {
            val toBreak = executor.toBreak().toSet()
            val cyanColor = Color(0, 255, 255) // Bright cyan
            val allSides = SideHighlights.all()
            val opacity = settings.highlightOpacityActiveMining.value

            goalPositions.filter { it in toBreak }.forEach { pos ->
                classified[pos] =
                    PriorityBlock(
                        pos,
                        0,
                        cyanColor,
                        opacity,
                        allSides,
                    )
            }
        }

        // Priority 1: Immediate path (pathPosition+1)
        if (executor != null && settings.highlightOpacityImmediatePath.value > 0.0f) {
            val position = executor.position
            if (position < executor.path.length() - 1) {
                val nextPos =
                    executor.path
                        .positions()[position + 1]
                        .toBlockPos()
                        .below()
                if (nextPos !in classified) {
                    classified[nextPos] =
                        PriorityBlock(
                            nextPos,
                            1,
                            Color(255, 255, 0), // Bright yellow
                            settings.highlightOpacityImmediatePath.value,
                            SideHighlights.top(),
                        )
                }
            }
        }

        // Priority 2: Active actions (toBreak/toPlace/toWalkInto, excluding goals)
        if (executor != null && settings.highlightAlpha.value > 0.0f) {
            val reducedOpacity = 0.2f // Lower opacity to reduce brightness
            val allSides = SideHighlights.all()

            // Compute desaturated colors once to ensure grouping
            val breakColor = desaturateColor(settings.colorBlocksToBreak.value, 0.7f)
            val placeColor = desaturateColor(settings.colorBlocksToPlace.value, 0.7f)
            val walkIntoColor = desaturateColor(settings.colorBlocksToWalkInto.value, 0.7f)

            // toBreak (excluding goal blocks)
            executor.toBreak().filter { it !in classified }.forEach { pos ->
                classified[pos] =
                    PriorityBlock(
                        pos,
                        2,
                        breakColor,
                        reducedOpacity,
                        allSides,
                    )
            }

            // toPlace
            executor.toPlace().filter { it !in classified }.forEach { pos ->
                classified[pos] =
                    PriorityBlock(
                        pos,
                        2,
                        placeColor,
                        reducedOpacity,
                        allSides,
                    )
            }

            // toWalkInto
            executor.toWalkInto().filter { it !in classified }.forEach { pos ->
                classified[pos] =
                    PriorityBlock(
                        pos,
                        2,
                        walkIntoColor,
                        reducedOpacity,
                        allSides,
                    )
            }
        }

        // Priority 3: Current path (future positions)
        if (executor != null && settings.highlightOpacityCurrentPath.value > 0.0f) {
            val position = executor.position
            if (position + 2 < executor.path.length()) {
                val pathColor = desaturateColor(Color(64, 224, 208), 0.8f) // Turquoise
                val topSides = SideHighlights.top()

                executor.path
                    .positions()
                    .drop(position + 2)
                    .map { it.toBlockPos().below() }
                    .filter { it !in classified }
                    .forEach { pos ->
                        classified[pos] =
                            PriorityBlock(
                                pos,
                                3,
                                pathColor,
                                settings.highlightOpacityCurrentPath.value,
                                topSides,
                            )
                    }
            }
        }

        // Priority 4: Pending goals (goal \ toBreak)
        if (goal != null && goalPositions.isNotEmpty()) {
            val goalColor = desaturateColor(Color(50, 205, 50), 0.7f) // Lime green
            val allSides = SideHighlights.all()

            goalPositions.filter { it !in classified }.forEach { pos ->
                classified[pos] =
                    PriorityBlock(
                        pos,
                        4,
                        goalColor,
                        0.2f,
                        allSides,
                    )
            }
        }

        // Priority 5: Discovered ores (NOT in goals)
        if (mineTask != null && settings.highlightOpacityDiscoveredOres.value > 0.0f) {
            val allSides = SideHighlights.all()

            // Cache desaturated colors for all ore types
            val oreColorCache = mutableMapOf<Block, Color>()

            mineTask
                .getKnownOreLocations()
                .filter { it !in classified && it !in goalPositions }
                .forEach { pos ->
                    val blockState = ctx.world().getBlockState(pos)
                    val block = blockState.block

                    // Get or compute desaturated color for this ore type
                    val desaturatedColor =
                        oreColorCache.getOrPut(block) {
                            val baseColor =
                                if (settings.highlightOreSpecificColors.value) {
                                    getOreColor(block)
                                } else {
                                    Color(255, 218, 185) // Default pale orange
                                }
                            desaturateColor(baseColor, 0.6f)
                        }

                    classified[pos] =
                        PriorityBlock(
                            pos,
                            5,
                            desaturatedColor,
                            settings.highlightOpacityDiscoveredOres.value,
                            allSides,
                        )
                }
        }

        // Priority 6: Historical path (past positions)
        if (executor != null &&
            settings.highlightShowHistoricalPath.value &&
            settings.highlightOpacityHistoricalPath.value > 0.0f
        ) {
            val position = executor.position
            if (position > 0) {
                val historicalColor = desaturateColor(Color(64, 224, 208), 0.7f) // Turquoise with more color
                val topSides = SideHighlights.top()

                executor.path
                    .positions()
                    .take(position)
                    .map { it.toBlockPos().below() }
                    .filter { it !in classified }
                    .forEach { pos ->
                        classified[pos] =
                            PriorityBlock(
                                pos,
                                6,
                                historicalColor,
                                settings.highlightOpacityHistoricalPath.value,
                                topSides,
                            )
                    }
            }
        }

        return classified
    }

    /**
     * Apply distance-based opacity fading to blocks.
     *
     * @param classified Map of classified blocks
     * @param playerPos Player position
     * @param maxDist Maximum distance for full fade (default 64 blocks)
     * @return Map with updated opacities based on distance
     */
    fun applyDistanceFading(
        classified: Map<BlockPos, PriorityBlock>,
        playerPos: Vec3,
        maxDist: Double = 64.0,
    ): Map<BlockPos, PriorityBlock> =
        classified.mapValues { (_, block) ->
            // Only fade priorities 3-6 (path and low-priority blocks)
            if (block.priority >= 3) {
                val distance = playerPos.distanceTo(Vec3.atCenterOf(block.pos))
                val fadeFactor = (1.0 - (distance / maxDist).coerceIn(0.0, 1.0)).toFloat()
                val adjustedOpacity = block.opacity * (0.5f + 0.5f * fadeFactor)
                block.copy(opacity = adjustedOpacity)
            } else {
                block
            }
        }

    /**
     * Extract all block positions from a goal.
     *
     * Handles:
     * - GoalComposite: recursively extract from all sub-goals
     * - IGoalRenderPos: extract single position
     * - Other: empty set
     *
     * @param goal Goal to extract positions from
     * @return Set of all block positions in the goal
     */
    private fun extractGoalPositions(goal: Goal): Set<BlockPos> =
        when (goal) {
            is GoalComposite ->
                goal.goals().flatMap { extractGoalPositions(it) }.toSet()
            is IGoalRenderPos -> setOf(goal.goalPos)
            else -> emptySet()
        }

    /**
     * Get ore-specific color based on block type.
     *
     * Supports all vanilla ores (normal + deepslate variants):
     * - Diamond → Light Blue
     * - Gold → Gold
     * - Iron → Light Gray
     * - Redstone → Light Coral
     * - Coal → Dark Gray
     * - Emerald → Pale Green
     * - Lapis → Deep Sky Blue
     * - Default → Pale Orange
     *
     * @param block Block type
     * @return Color for the ore
     */
    private fun getOreColor(block: Block): Color =
        when (block) {
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE -> Color(173, 216, 230)
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE -> Color(255, 215, 0)
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE -> Color(211, 211, 211)
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE -> Color(240, 128, 128)
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE -> Color(105, 105, 105)
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE -> Color(152, 251, 152)
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE -> Color(0, 191, 255)
            else -> Color(255, 218, 185) // Pale orange default
        }

    /**
     * Desaturate a color by reducing its saturation component.
     *
     * Uses HSB color space to reduce saturation while preserving hue and brightness.
     *
     * @param color Original color
     * @param saturationMultiplier Multiplier for saturation (0.0-1.0)
     * @return Desaturated color
     */
    private fun desaturateColor(
        color: Color,
        saturationMultiplier: Float,
    ): Color {
        val hsb = FloatArray(3)
        Color.RGBtoHSB(color.red, color.green, color.blue, hsb)
        hsb[1] *= saturationMultiplier // Reduce saturation
        return Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]))
    }

    /**
     * Calculate pulsing opacity for active mining targets.
     *
     * Pulses between 0.4 and 0.6 opacity on a 2-second cycle (40 ticks).
     *
     * @param baseOpacity Base opacity value (unused, always pulses 0.4-0.6)
     * @param tickCount Current game tick count
     * @return Pulsing opacity value
     */
    @Suppress("UNUSED_PARAMETER")
    private fun getPulseOpacity(
        baseOpacity: Float,
        tickCount: Long,
    ): Float {
        val cycle = (tickCount % 40) / 40.0 // 2 seconds at 20 TPS
        val wave = sin(cycle * 2 * PI) // Range: -1.0 to 1.0
        // Map sine wave from [-1, 1] to [0.4, 0.6]
        return (0.5f + wave.toFloat() * 0.1f)
    }
}
