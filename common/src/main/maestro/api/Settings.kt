package maestro.api

import maestro.api.utils.Helper
import maestro.behavior.FreecamMode
import maestro.gui.Toast
import net.minecraft.client.Minecraft
import net.minecraft.core.Vec3i
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Color
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Collections
import java.util.List
import java.util.Locale
import java.util.Map
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.Consumer

/**
 * Maestro's settings. Settings apply to all Maestro instances.
 */
class Settings {
    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger("Maestro")
    }

    // PATHFINDING SETTINGS

    /** Allow Maestro to break blocks */
    @JvmField
    val allowBreak =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Allow Maestro to break blocks"
        }

    /** Blocks that maestro will be allowed to break even with allowBreak set to false */
    @JvmField
    val allowBreakAnyway =
        Setting(arrayListOf<Block>()) {
            category = SettingCategory.PATHFINDING
            description = "Blocks that maestro will be allowed to break even with allowBreak set to false"
        }

    /** Allow Maestro to place blocks */
    @JvmField
    val allowPlace =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Allow Maestro to place blocks"
        }

    /** Allow Maestro to place blocks in fluid source blocks */
    @JvmField
    val allowPlaceInFluidsSource =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Allow Maestro to place blocks in fluid source blocks"
        }

    /** Allow Maestro to place blocks in flowing fluid */
    @JvmField
    val allowPlaceInFluidsFlow =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Allow Maestro to place blocks in flowing fluid"
        }

    /** Allow Maestro to move items in your inventory to your hotbar */
    @JvmField
    val allowInventory =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Allow Maestro to move items in your inventory to your hotbar"
        }

    /** Wait this many ticks between InventoryBehavior moving inventory items */
    @JvmField
    val ticksBetweenInventoryMoves =
        Setting(1) {
            category = SettingCategory.PATHFINDING
            description = "Wait this many ticks between InventoryBehavior moving inventory items"
        }

    /** Come to a halt before doing any inventory moves. Intended for anticheat such as 2b2t */
    @JvmField
    val inventoryMoveOnlyIfStationary =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Come to a halt before doing any inventory moves. Intended for anticheat such as 2b2t"
        }

    /**
     * Disable maestro's auto-tool at runtime, but still assume that another mod will provide auto
     * tool functionality
     *
     * Specifically, path calculation will still assume that an auto tool will run at execution
     * time, even though Maestro itself will not do that.
     */
    @JvmField
    val assumeExternalAutoTool =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Disable maestro's auto-tool at runtime, but still assume that another mod will provide auto tool functionality"
        }

    /** Automatically select the best available tool */
    @JvmField
    val autoTool =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Automatically select the best available tool"
        }

    /**
     * It doesn't actually take twenty ticks to place a block, this cost is so high because we want
     * to generally conserve blocks which might be limited.
     *
     * Decrease to make Maestro more often consider paths that would require placing blocks
     */
    @JvmField
    val blockPlacementPenalty =
        Setting(20.0) {
            category = SettingCategory.PATHFINDING
            description = "Cost penalty for placing blocks"
        }

    /**
     * This is just a tiebreaker to make it less likely to break blocks if it can avoid it. For
     * example, fire has a break cost of 0, this makes it nonzero, so all else being equal it will
     * take an otherwise equivalent route that doesn't require it to put out fire.
     */
    @JvmField
    val blockBreakAdditionalPenalty =
        Setting(2.0) {
            category = SettingCategory.PATHFINDING
            description = "Additional penalty for breaking blocks as a tiebreaker"
        }

    /**
     * Additional penalty for hitting the space bar (ascend, pillar, or parkour) because it uses
     * hunger
     */
    @JvmField
    val jumpPenalty =
        Setting(2.0) {
            category = SettingCategory.PATHFINDING
            description = "Additional penalty for jumping (uses hunger)"
        }

    /** Walking on water uses up hunger really quick, so penalize it */
    @JvmField
    val walkOnWaterOnePenalty =
        Setting(3.0) {
            category = SettingCategory.PATHFINDING
            description = "Penalty for walking on water (uses hunger)"
        }

    /**
     * Don't allow breaking blocks next to liquids.
     *
     * Enable if you have mods adding custom fluid physics.
     */
    @JvmField
    val strictLiquidCheck =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Don't allow breaking blocks next to liquids"
        }

    /**
     * Allow Maestro to fall arbitrary distances and place a water bucket beneath it. Reliability:
     * questionable.
     */
    @JvmField
    val allowWaterBucketFall =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Allow water bucket falls"
        }

    /**
     * Allow Maestro to assume it can walk on still water just like any other block. This
     * functionality is assumed to be provided by a separate library that might have imported
     * Maestro.
     *
     * Note: This will prevent some usage of the frostwalker enchantment, like pillaring up from
     * water.
     */
    @JvmField
    val assumeWalkOnWater =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Assume ability to walk on still water"
        }

    /** If you have Fire Resistance and Jesus then I guess you could turn this on */
    @JvmField
    val assumeWalkOnLava =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Assume ability to walk on lava"
        }

    /** Assume step functionality; don't jump on an Ascend. */
    @JvmField
    val assumeStep =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Assume step functionality"
        }

    /**
     * Assume safe walk functionality; don't sneak on a backplace traverse.
     *
     * Warning: if you do something janky like sneak-backplace from an ender chest, if this is
     * true it won't sneak right click, it'll just right-click, which means it'll open the chest
     * instead of placing against it. That's why this defaults to off.
     */
    @JvmField
    val assumeSafeWalk =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Assume safe walk functionality"
        }

    /**
     * If true, parkour is allowed to make jumps when standing on blocks at the maximum height, so
     * player feet is y=256
     *
     * Defaults to false because this fails on constantiam. Please let me know if this is ever
     * disabled. Please.
     */
    @JvmField
    val allowJumpAtBuildLimit =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Allow parkour jumps at build limit"
        }

    /**
     * This should be monetized it's so good
     *
     * Defaults to true, but only actually takes effect if allowParkour is also true
     */
    @JvmField
    val allowParkourAscend =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Allow parkour ascending"
        }

    /**
     * Allow descending diagonally
     *
     * Safer than allowParkour yet still slightly unsafe, can make contact with unchecked
     * adjacent blocks, so it's unsafe in the nether.
     *
     * For a generic "take some risks" mode I'd turn on this one, parkour, and parkour place.
     */
    @JvmField
    val allowDiagonalDescend =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Allow diagonal descending"
        }

    /**
     * Allow diagonal ascending
     *
     * Actually pretty safe, much safer than diagonal descend tbh
     */
    @JvmField
    val allowDiagonalAscend =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Allow diagonal ascending"
        }

    /**
     * Allow mining the block directly beneath its feet
     *
     * Turn this off to force it to make more staircases and fewer shafts
     */
    @JvmField
    val allowDownward =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Allow mining block beneath feet"
        }

    /**
     * Blocks that Maestro is allowed to place (as throwaway, for sneak bridging, pillaring, etc.)
     */
    @JvmField
    val acceptableThrowawayItems =
        Setting(
            arrayListOf(
                Blocks.DIRT.asItem(),
                Blocks.COBBLESTONE.asItem(),
                Blocks.NETHERRACK.asItem(),
                Blocks.STONE.asItem(),
            ),
        ) {
            category = SettingCategory.PATHFINDING
            description = "Blocks that Maestro is allowed to place as throwaway"
        }

    /** Blocks that Maestro will attempt to avoid (Used in avoidance) */
    @JvmField
    val blocksToAvoid =
        Setting(arrayListOf<Block>()) {
            category = SettingCategory.PATHFINDING
            description = "Blocks that Maestro will attempt to avoid"
        }

    /** Blocks that Maestro is not allowed to break */
    @JvmField
    val blocksToDisallowBreaking =
        Setting(arrayListOf<Block>()) {
            category = SettingCategory.PATHFINDING
            description = "Blocks that Maestro is not allowed to break"
        }

    /** blocks that maestro shouldn't break, but can if it needs to. */
    @JvmField
    val blocksToAvoidBreaking =
        Setting(
            arrayListOf(
                Blocks.CRAFTING_TABLE,
                Blocks.FURNACE,
                Blocks.CHEST,
                Blocks.TRAPPED_CHEST,
            ),
        ) {
            category = SettingCategory.PATHFINDING
            description = "Blocks that maestro shouldn't break, but can if it needs to"
        }

    /** this multiplies the break speed, if set above 1 it's "encourage breaking" instead */
    @JvmField
    val avoidBreakingMultiplier =
        Setting(0.1) {
            category = SettingCategory.PATHFINDING
            description = "Break speed multiplier for blocks to avoid breaking"
        }

    /**
     * If this setting is true, Maestro will never break a block that is adjacent to an unsupported
     * falling block.
     *
     * I.E. it will never trigger cascading sand / gravel falls
     */
    @JvmField
    val avoidUpdatingFallingBlocks =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Never trigger cascading sand/gravel falls"
        }

    /**
     * Enables some more advanced vine features. They're honestly just gimmicks and won't ever be
     * needed in real pathing scenarios. And they can cause Maestro to get trapped indefinitely in a
     * strange scenario.
     *
     * Almost never turn this on
     */
    @JvmField
    val allowVines =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Enable advanced vine features"
        }

    /**
     * Slab behavior is complicated, disable this for higher path reliability. Leave enabled if you
     * have bottom slabs everywhere in your base.
     */
    @JvmField
    val allowWalkOnBottomSlab =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Allow walking on bottom slabs"
        }

    /**
     * You know what it is
     *
     * But it's very unreliable and falls off when cornering like all the time so.
     *
     * It also overshoots the landing pretty much always (making contact with the next block
     * over), so be careful
     */
    @JvmField
    val allowParkour =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Allow parkour movements"
        }

    /**
     * Actually pretty reliable.
     *
     * Doesn't make it any more dangerous compared to just normal allowParkour th
     */
    @JvmField
    val allowParkourPlace =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Allow parkour with block placement"
        }

    /**
     * Allow packet-based teleportation (exploit).
     *
     * Enables instant teleportation up to 200 blocks by manipulating movement packets. This is a
     * client-side exploit that may be detected by anticheat plugins.
     *
     * Disabled by default. Use at your own risk.
     */
    @JvmField
    val allowTeleport =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Allow packet-based teleportation (exploit)"
        }

    /**
     * How often to generate teleport movements (1/N nodes).
     *
     * Lower values (e.g., 5) generate more teleport options but cost more performance. Higher
     * values (e.g., 20) generate fewer options but are faster.
     *
     * Default: 10 (generate at 10% of nodes)
     */
    @JvmField
    val teleportGenerationSparsity =
        Setting(10) {
            category = SettingCategory.PATHFINDING
            description = "Frequency of teleport movement generation"
        }

    /** Minimum teleport distance in blocks */
    @JvmField
    val teleportMinDistance =
        Setting(10) {
            category = SettingCategory.PATHFINDING
            description = "Minimum teleport distance in blocks"
        }

    /** Maximum teleport distance in blocks (hard limit: 200 due to packet exploit constraints) */
    @JvmField
    val teleportMaxDistance =
        Setting(200) {
            category = SettingCategory.PATHFINDING
            description = "Maximum teleport distance in blocks"
        }

    /**
     * Cost multiplier for teleport movements.
     *
     * Increase to make teleports less attractive to pathfinding, decrease to make them more
     * attractive.
     *
     * Default: 1.0
     */
    @JvmField
    val teleportCostMultiplier =
        Setting(1.0) {
            category = SettingCategory.PATHFINDING
            description = "Cost multiplier for teleport movements"
        }

    /**
     * How long to remember movement failures (in milliseconds).
     *
     * When a movement fails (teleport rejected, world changed, timeout, etc.), the system tracks
     * this failure and applies cost penalties to discourage immediate retries. Failures expire
     * after this duration.
     *
     * Default: 30000ms (30 seconds)
     */
    @JvmField
    val movementFailureMemoryDuration =
        Setting(30000L) {
            category = SettingCategory.PATHFINDING
            description = "How long to remember movement failures (ms)"
        }

    /**
     * Cost multiplier applied per movement failure.
     *
     * Each consecutive failure at a position increases cost by this multiplier raised to the
     * power of attempt count. For example, with multiplier 5.0:
     *
     * - 1st failure: 5x cost penalty
     * - 2nd failure: 25x cost penalty
     * - 3rd failure: 125x cost penalty (capped by movementFailureMaxPenalty)
     *
     * Default: 5.0
     */
    @JvmField
    val movementFailurePenaltyMultiplier =
        Setting(5.0) {
            category = SettingCategory.PATHFINDING
            description = "Cost multiplier per movement failure"
        }

    /**
     * Maximum cost penalty multiplier for failed movements.
     *
     * Caps the exponential penalty to prevent extremely high costs.
     *
     * Default: 50.0 (50x cost penalty max)
     */
    @JvmField
    val movementFailureMaxPenalty =
        Setting(50.0) {
            category = SettingCategory.PATHFINDING
            description = "Maximum cost penalty for failed movements"
        }

    /**
     * Maximum consecutive failures before treating a movement as impossible.
     *
     * After this many failures at the same position with the same movement type, the movement
     * will be filtered out entirely (treated as ActionCosts.COST_INF).
     *
     * Default: 3 attempts
     */
    @JvmField
    val movementFailureMaxAttempts =
        Setting(3) {
            category = SettingCategory.PATHFINDING
            description = "Maximum failures before movement becomes impossible"
        }

    /**
     * Enable smart path reconnection when deviating from corridor.
     *
     * When enabled, the bot will attempt to reconnect to the existing path at an optimal point
     * instead of immediately recalculating the entire path from scratch.
     *
     * Default: true
     */
    @JvmField
    val pathReconnectionEnabled =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Enable smart path reconnection"
        }

    /**
     * How many path segments to search ahead when finding reconnection points.
     *
     * Larger values allow reconnecting further ahead in the path, but increase computational
     * cost.
     *
     * Default: 15 segments
     */
    @JvmField
    val pathReconnectionLookahead =
        Setting(15) {
            category = SettingCategory.PATHFINDING
            description = "Path segments to search ahead for reconnection"
        }

    /**
     * How many path segments to search behind when finding reconnection points.
     *
     * Allows reconnecting to earlier parts of the path if we overshot or were pushed backward.
     *
     * Default: 5 segments
     */
    @JvmField
    val pathReconnectionLookbehind =
        Setting(5) {
            category = SettingCategory.PATHFINDING
            description = "Path segments to search behind for reconnection"
        }

    /**
     * Maximum A* nodes to explore when calculating partial reconnection path.
     *
     * Limits the computational cost of reconnection attempts.
     *
     * Default: 500 nodes
     */
    @JvmField
    val pathReconnectionMaxPartialNodes =
        Setting(500) {
            category = SettingCategory.PATHFINDING
            description = "Max A* nodes for reconnection calculation"
        }

    /**
     * Maximum time to spend calculating a partial reconnection path (in milliseconds).
     *
     * If reconnection takes longer than this, fall back to full recalculation.
     *
     * Default: 200ms
     */
    @JvmField
    val pathReconnectionTimeoutMs =
        Setting(200) {
            category = SettingCategory.PATHFINDING
            description = "Max time for reconnection calculation (ms)"
        }

    /**
     * Cost threshold for preferring reconnection to full recalculation.
     *
     * Reconnection will be used if its total cost is less than this multiplier times the
     * estimated cost of full recalculation. Higher values prefer reconnection more aggressively.
     *
     * For example, 1.5 means reconnection will be used even if it's 50% more expensive than full
     * recalc, to avoid the overhead of recalculation.
     *
     * Default: 1.5
     */
    @JvmField
    val pathReconnectionCostThreshold =
        Setting(1.5) {
            category = SettingCategory.PATHFINDING
            description = "Cost threshold for preferring reconnection"
        }

    /**
     * For example, if you have Mining Fatigue or Haste, adjust the costs of breaking blocks
     * accordingly.
     */
    @JvmField
    val considerPotionEffects =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Consider potion effects in pathfinding costs"
        }

    /**
     * If we overshoot a traverse and end up one block beyond the destination, mark it as successful
     * anyway.
     *
     * This helps with speed exceeding 20m/s
     */
    @JvmField
    val overshootTraverse =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Allow overshooting traverses"
        }

    /**
     * When breaking blocks for a movement, wait until all falling blocks have settled before
     * continuing
     */
    @JvmField
    val pauseMiningForFallingBlocks =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Wait for falling blocks to settle"
        }

    /** How many ticks between right clicks are allowed. Default in game is 4 */
    @JvmField
    val rightClickSpeed =
        Setting(4) {
            category = SettingCategory.PATHFINDING
            description = "Ticks between right clicks"
        }

    /** Block reach distance */
    @JvmField
    val blockReachDistance =
        Setting(4.5f) {
            category = SettingCategory.PATHFINDING
            description = "Block reach distance"
        }

    /**
     * How many ticks between breaking a block and starting to break the next block. Default in game
     * is 6 ticks. Values under 1 will be clamped. The delay only applies to non-instant (1-tick)
     * breaks.
     */
    @JvmField
    val blockBreakSpeed =
        Setting(6) {
            category = SettingCategory.PATHFINDING
            description = "Ticks between block breaks"
        }

    /**
     * This is the big A* setting. As long as your cost heuristic is an *underestimate*, it's
     * guaranteed to find you the best path. 3.5 is always an underestimate, even if you are
     * sprinting. If you're walking only (with allowSprint off) 4.6 is safe. Any value below 3.5 is
     * never worth it. It's just more computation to find the same path, guaranteed. (specifically,
     * it needs to be strictly slightly less than ActionCosts.WALK_ONE_BLOCK_COST, which is about
     * 3.56)
     *
     * Setting it at 3.57 or above with sprinting, or to 4.64 or above without sprinting, will
     * result in faster computation, at the cost of a suboptimal path. Any value above the walk /
     * sprint cost will result in it going straight at its goal, and not investigating alternatives,
     * because the combined cost / heuristic metric gets better and better with each block, instead
     * of slightly worse.
     *
     * Finding the optimal path is worth it, so it's the default.
     */
    @JvmField
    val costHeuristic =
        Setting(3.563) {
            category = SettingCategory.PATHFINDING
            description = "A* cost heuristic"
        }

    /**
     * The maximum number of times it will fetch outside loaded or cached chunks before assuming
     * that pathing has reached the end of the known area, and should therefore stop.
     */
    @JvmField
    val pathingMaxChunkBorderFetch =
        Setting(300) {
            category = SettingCategory.PATHFINDING
            description = "Max chunk border fetches before stopping"
        }

    /**
     * Set to 1.0 to effectively disable this feature
     */
    @JvmField
    val backtrackCostFavoringCoefficient =
        Setting(0.5) {
            category = SettingCategory.PATHFINDING
            description = "Backtrack cost favoring coefficient"
        }

    /**
     * Toggle the following 4 settings
     *
     * They have a noticeable performance impact, so they default off
     *
     * Specifically, building up the avoidance map on the main thread before pathing starts
     * actually takes a noticeable amount of time, especially when there are a lot of mobs around,
     * and your game jitters for like 200ms while doing so
     */
    @JvmField
    val avoidance =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Enable avoidance (performance impact)"
        }

    /**
     * Set to 1.0 to effectively disable this feature
     *
     * Set below 1.0 to go out of your way to walk near mob spawners
     */
    @JvmField
    val mobSpawnerAvoidanceCoefficient =
        Setting(2.0) {
            category = SettingCategory.PATHFINDING
            description = "Mob spawner avoidance coefficient"
        }

    /** Distance to avoid mob spawners. */
    @JvmField
    val mobSpawnerAvoidanceRadius =
        Setting(16) {
            category = SettingCategory.PATHFINDING
            description = "Mob spawner avoidance radius"
        }

    /**
     * Set to 1.0 to effectively disable this feature
     *
     * Set below 1.0 to go out of your way to walk near mobs
     */
    @JvmField
    val mobAvoidanceCoefficient =
        Setting(1.5) {
            category = SettingCategory.PATHFINDING
            description = "Mob avoidance coefficient"
        }

    /** Distance to avoid mobs. */
    @JvmField
    val mobAvoidanceRadius =
        Setting(8) {
            category = SettingCategory.PATHFINDING
            description = "Mob avoidance radius"
        }

    /**
     * When running a goto towards a container block (chest, ender chest, furnace, etc.), right
     * click and open it once you arrive.
     */
    @JvmField
    val rightClickContainerOnArrival =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Right click containers on arrival"
        }

    /**
     * When running a goto towards a nether portal block, walk all the way into the portal instead
     * of stopping one block before.
     */
    @JvmField
    val enterPortal =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Enter portals on arrival"
        }

    /**
     * Don't repropagate cost improvements below 0.01 ticks. They're all just floating point
     * inaccuracies, and there's no point.
     */
    @JvmField
    val minimumImprovementRepropagation =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Skip tiny cost improvements"
        }

    /**
     * After calculating a path (potentially through cached chunks), artificially cut it off to just
     * the part that is entirely within currently loaded chunks. Improves path safety because cached
     * chunks are heavily simplified.
     *
     * This is much safer to leave off now, and makes pathing more efficient. More explanation in
     * the issue.
     */
    @JvmField
    val cutoffAtLoadBoundary =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Cut off path at load boundary"
        }

    /**
     * If a movement's cost increases by more than this amount between calculation and execution
     * (due to changes in the environment / world), cancel and recalculate
     */
    @JvmField
    val maxCostIncrease =
        Setting(10.0) {
            category = SettingCategory.PATHFINDING
            description = "Max cost increase before recalculation"
        }

    /**
     * Stop 5 movements before anything that made the path ActionCosts.COST_INF. For example, if
     * lava has spread across the path, don't walk right up to it then recalculate, it might still
     * be spreading
     */
    @JvmField
    val costVerificationLookahead =
        Setting(5) {
            category = SettingCategory.PATHFINDING
            description = "Stop N movements before infinite cost"
        }

    /**
     * Static cutoff factor. 0.9 means cut off the last 10% of all paths, regardless of chunk load
     * state
     */
    @JvmField
    val pathCutoffFactor =
        Setting(0.9) {
            category = SettingCategory.PATHFINDING
            description = "Static path cutoff factor"
        }

    /**
     * Only apply static cutoff for paths of at least this length (in terms of number of movements)
     */
    @JvmField
    val pathCutoffMinimumLength =
        Setting(30) {
            category = SettingCategory.PATHFINDING
            description = "Minimum path length for cutoff"
        }

    /**
     * Start planning the next path once the remaining movements tick estimates sum up to less than
     * this value
     */
    @JvmField
    val planningTickLookahead =
        Setting(150) {
            category = SettingCategory.PATHFINDING
            description = "Planning tick lookahead"
        }

    /** Default size of the Long2ObjectOpenHashMap used in pathing */
    @JvmField
    val pathingMapDefaultSize =
        Setting(1024) {
            category = SettingCategory.PATHFINDING
            description = "Pathing map default size"
        }

    /**
     * Load factor coefficient for the Long2ObjectOpenHashMap used in pathing
     *
     * Decrease for faster map operations, but higher memory usage
     */
    @JvmField
    val pathingMapLoadFactor =
        Setting(0.75f) {
            category = SettingCategory.PATHFINDING
            description = "Pathing map load factor"
        }

    /**
     * How far are you allowed to fall onto solid ground (without a water bucket)? 3 won't deal any
     * damage. But if you just want to get down the mountain quickly, and you have Feather Falling
     * IV, you might set it a bit higher, like 4 or 5.
     */
    @JvmField
    val maxFallHeightNoWater =
        Setting(3) {
            category = SettingCategory.PATHFINDING
            description = "Max fall height without water bucket"
        }

    /**
     * How far are you allowed to fall onto solid ground (with a water bucket)? It's not that
     * reliable, so I've set it below what would kill an unarmored player (23)
     */
    @JvmField
    val maxFallHeightBucket =
        Setting(20) {
            category = SettingCategory.PATHFINDING
            description = "Max fall height with water bucket"
        }

    /**
     * Is it okay to sprint through a descent followed by a diagonal? The player overshoots the
     * landing, but not enough to fall off. And the diagonal ensures that there isn't lava or
     * anything that's !canWalkInto in that space, so it's technically safe, just a little sketchy.
     *
     * Note: this is *not* related to the allowDiagonalDescend setting, that is a completely
     * different thing.
     */
    @JvmField
    val allowOvershootDiagonalDescend =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Allow overshooting diagonal descends"
        }

    /**
     * If your goal is a GoalBlock in an unloaded chunk, assume it's far enough away that the Y
     * coord doesn't matter yet, and replace it with a GoalXZ to the same place before calculating a
     * path. Once a segment ends within chunk load range of the GoalBlock, it will go back to normal
     * behavior of considering the Y coord. The reasoning is that if your X and Z are 10,000 blocks
     * away, your Y coordinate's accuracy doesn't matter at all until you get much, much closer.
     */
    @JvmField
    val simplifyUnloadedYCoord =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Simplify Y coord for unloaded chunks"
        }

    /** Whenever a block changes, repack the whole chunk that it's in */
    @JvmField
    val repackOnAnyBlockChange =
        Setting(true) {
            category = SettingCategory.ADVANCED
            description = "Repack chunk on any block change"
        }

    /** If a movement takes this many ticks more than its initial cost estimate, cancel it */
    @JvmField
    val movementTimeoutTicks =
        Setting(100) {
            category = SettingCategory.PATHFINDING
            description = "Movement timeout in ticks"
        }

    /**
     * Pathing ends after this amount of time, but only if a path has been found
     *
     * If no valid path (length above the minimum) has been found, pathing continues up until the
     * failure timeout
     */
    @JvmField
    val primaryTimeoutMS =
        Setting(500L) {
            category = SettingCategory.PATHFINDING
            description = "Primary pathing timeout (ms)"
        }

    /**
     * Pathing can never take longer than this, even if that means failing to find any path at all
     */
    @JvmField
    val failureTimeoutMS =
        Setting(2000L) {
            category = SettingCategory.PATHFINDING
            description = "Failure pathing timeout (ms)"
        }

    /**
     * Planning ahead while executing a segment ends after this amount of time, but only if a path
     * has been found
     *
     * If no valid path (length above the minimum) has been found, pathing continues up until the
     * failure timeout
     */
    @JvmField
    val planAheadPrimaryTimeoutMS =
        Setting(4000L) {
            category = SettingCategory.PATHFINDING
            description = "Plan ahead primary timeout (ms)"
        }

    /**
     * Planning ahead while executing a segment can never take longer than this, even if that means
     * failing to find any path at all
     */
    @JvmField
    val planAheadFailureTimeoutMS =
        Setting(5000L) {
            category = SettingCategory.PATHFINDING
            description = "Plan ahead failure timeout (ms)"
        }

    /** For debugging, consider nodes much, much slower */
    @JvmField
    val slowPath =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Slow path for debugging"
        }

    /** Milliseconds between each node */
    @JvmField
    val slowPathTimeDelayMS =
        Setting(100L) {
            category = SettingCategory.ADVANCED
            description = "Slow path time delay (ms)"
        }

    /** The alternative timeout number when slowPath is on */
    @JvmField
    val slowPathTimeoutMS =
        Setting(40000L) {
            category = SettingCategory.ADVANCED
            description = "Slow path timeout (ms)"
        }

    /**
     * When a new segment is calculated that doesn't overlap with the current one, but simply begins
     * where the current segment ends, splice it on and make a longer combined path. If this setting
     * is off, any planned segment will not be spliced and will instead be the "next path" in
     * PathingBehavior, and will only start after this one ends. Turning this off hurts planning
     * ahead, because the next segment will exist even if it's very short.
     */
    @JvmField
    val splicePath =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Splice continuous path segments"
        }

    /**
     * If we are more than 300 movements into the current path, discard the oldest segments, as they
     * are no longer useful
     */
    @JvmField
    val maxPathHistoryLength =
        Setting(300) {
            category = SettingCategory.PATHFINDING
            description = "Max path history length"
        }

    /** If the current path is too long, cut off this many movements from the beginning. */
    @JvmField
    val pathHistoryCutoffAmount =
        Setting(50) {
            category = SettingCategory.PATHFINDING
            description = "Path history cutoff amount"
        }

    /**
     * When GetToBlockTask or MineProcess fails to calculate a path, instead of just giving up,
     * mark the closest instance of that block as "unreachable" and go towards the next closest.
     * GetToBlock expands this search to the whole "vein"; MineProcess does not. This is because
     * MineProcess finds individual impossible blocks (like one block in a vein that has gravel on
     * top then lava, so it can't break) Whereas GetToBlock should blacklist the whole "vein" if it
     * can't get to any of them.
     */
    @JvmField
    val blacklistClosestOnFailure =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Blacklist closest block on path failure"
        }

    /**
     * Cancel the current path if the goal has changed, and the path originally ended in the goal
     * but doesn't anymore.
     *
     * Currently only runs when either MineBehavior or FollowBehavior is active.
     *
     * For example, if Maestro is doing "mine iron_ore", the instant it breaks the ore (and it
     * becomes air), that location is no longer a goal. This means that if this setting is true, it
     * will stop there. If this setting were off, it would continue with its path, and walk into
     * that location. The tradeoff is if this setting is true, it mines ores much faster since it
     * doesn't waste any time getting into locations that no longer contain ores, but on the other
     * hand, it misses some drops, and continues on without ever picking them up.
     *
     * Also on cosmic prisons this should be set to true since you don't actually mine the ore it
     * just gets replaced with stone.
     */
    @JvmField
    val cancelOnGoalInvalidation =
        Setting(true) {
            category = SettingCategory.PATHFINDING
            description = "Cancel path when goal becomes invalid"
        }

    /**
     * Exclusively use cached chunks for pathing
     *
     * Never turn this on
     */
    @JvmField
    val pathThroughCachedOnly =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Only path through cached chunks"
        }

    // MOVEMENT SETTINGS

    /** Allow Maestro to sprint */
    @JvmField
    val allowSprint =
        Setting(true) {
            category = SettingCategory.MOVEMENT
            description = "Allow Maestro to sprint"
        }

    /** Sprint and jump a block early on ascends wherever possible */
    @JvmField
    val sprintAscends =
        Setting(true) {
            category = SettingCategory.MOVEMENT
            description = "Sprint and jump early on ascends"
        }

    /** Continue sprinting while in water */
    @JvmField
    val sprintInWater =
        Setting(true) {
            category = SettingCategory.MOVEMENT
            description = "Continue sprinting while in water"
        }

    /**
     * Enable enhanced swimming behavior that uses target velocity control for smooth water
     * traversal. When disabled, falls back to vanilla-style jumping in water.
     */
    @JvmField
    val enhancedSwimming =
        Setting(true) {
            category = SettingCategory.MOVEMENT
            description = "Enable enhanced swimming behavior"
        }

    /**
     * Swimming speed as percentage of vanilla underwater speed. Range: 1-1000%, default 100%
     * (vanilla speed).
     *
     * Examples: 100% = vanilla, 150% = 1.5x vanilla, 200% = 2x vanilla.
     *
     * Uses clamped value to prevent errors from invalid inputs.
     */
    @JvmField
    val swimSpeedPercent =
        Setting(100) {
            category = SettingCategory.MOVEMENT
            description = "Swimming speed as percentage of vanilla"
        }

    /**
     * Enable free-look camera mode, allowing user to look around independently of bot's movement
     * direction. When enabled, the rendered camera is decoupled from player rotation, so the bot
     * can control look direction for swimming/pathing while the user maintains independent view.
     */
    @JvmField
    val enableFreeLook =
        Setting(true) {
            category = SettingCategory.MOVEMENT
            description = "Enable free-look camera mode"
        }

    /**
     * Allow swimming pathfinding (3D underwater movement). When enabled, the bot can pathfind
     * through water in all directions (horizontal, vertical, diagonal) for true 3D navigation.
     * Requires enhancedSwimming to be enabled.
     */
    @JvmField
    val allowSwimming =
        Setting(true) {
            category = SettingCategory.MOVEMENT
            description = "Allow 3D swimming pathfinding"
        }

    /**
     * Minimum depth of water required to enable swimming movements. Shallow water uses terrestrial
     * movements instead. Default: 2 blocks deep.
     */
    @JvmField
    val minSwimmingDepth =
        Setting(2) {
            category = SettingCategory.MOVEMENT
            description = "Minimum water depth for swimming"
        }

    /**
     * Allow diagonal swimming movements for smoother underwater paths. When enabled, the bot can
     * swim diagonally (NE, NW, SE, SW) and diagonally-vertical (NE_UP, SW_DOWN, etc.) for more
     * direct routes through water.
     */
    @JvmField
    val allowDiagonalSwimming =
        Setting(true) {
            category = SettingCategory.MOVEMENT
            description = "Allow diagonal swimming movements"
        }

    /**
     * Angular precision for horizontal swimming movements.
     *
     * - 4: 4 directions (N, E, S, W) - basic
     * - 8: 8 directions (N, NE, E, SE, S, SW, W, NW) - recommended default
     * - 16: 16 directions (every 22.5°) - very smooth
     * - 32: 32 directions (every 11.25°) - maximum smoothness but higher pathfinding cost
     *
     * Higher precision creates smoother paths but increases pathfinding branching factor.
     */
    @JvmField
    val swimAngularPrecision =
        Setting(8) {
            category = SettingCategory.MOVEMENT
            description = "Swimming angular precision (directions)"
        }

    /**
     * Vertical precision for swimming. Currently, always 3 (pure UP, LEVEL, pure DOWN). With 3D
     * diagonals enabled, also generates UP-diagonal and DOWN-diagonal variants for each horizontal
     * angle (e.g., UP_NE, DOWN_SW).
     *
     * Reserved for future granular vertical precision (e.g., 5 or 7 vertical angles).
     */
    @JvmField
    val swimVerticalPrecision =
        Setting(3) {
            category = SettingCategory.MOVEMENT
            description = "Swimming vertical precision"
        }

    /** How many degrees to randomize the yaw every tick. Set to 0 to disable */
    @JvmField
    val randomLooking113 =
        Setting(2.0) {
            category = SettingCategory.MOVEMENT
            description = "Random yaw per tick (degrees)"
        }

    /** How many degrees to randomize the pitch and yaw every tick. Set to 0 to disable */
    @JvmField
    val randomLooking =
        Setting(0.01) {
            category = SettingCategory.MOVEMENT
            description = "Random pitch/yaw per tick (degrees)"
        }

    /** Move without having to force the client-sided rotations */
    @JvmField
    val freeLook =
        Setting(true) {
            category = SettingCategory.MOVEMENT
            description = "Move without forcing client-sided rotations"
        }

    /**
     * Break and place blocks without having to force the client-sided rotations. Requires freeLook.
     */
    @JvmField
    val blockFreeLook =
        Setting(false) {
            category = SettingCategory.MOVEMENT
            description = "Break/place without forcing rotations"
        }

    /** Automatically elytra fly without having to force the client-sided rotations. */
    @JvmField
    val elytraFreeLook =
        Setting(true) {
            category = SettingCategory.MOVEMENT
            description = "Elytra fly without forcing rotations"
        }

    /**
     * Forces the client-sided yaw rotation to an average of the last smoothLookTicks of
     * server-sided rotations.
     */
    @JvmField
    val smoothLook =
        Setting(false) {
            category = SettingCategory.MOVEMENT
            description = "Average client rotation over multiple ticks"
        }

    /** Same as smoothLook but for elytra flying. */
    @JvmField
    val elytraSmoothLook =
        Setting(false) {
            category = SettingCategory.MOVEMENT
            description = "Smooth look for elytra flying"
        }

    /** The number of ticks to average across for smoothLook */
    @JvmField
    val smoothLookTicks =
        Setting(5) {
            category = SettingCategory.MOVEMENT
            description = "Ticks to average for smooth look"
        }

    /**
     * When true, the player will remain with its existing look direction as often as possible.
     * Although, in some cases this can get it stuck, hence this setting to disable that behavior.
     */
    @JvmField
    val remainWithExistingLookDirection =
        Setting(true) {
            category = SettingCategory.MOVEMENT
            description = "Maintain existing look direction"
        }

    /**
     * Will cause some minor behavioral differences to ensure that Maestro works on anticheats.
     *
     * At the moment this will silently set the player's rotations when using freeLook so you're
     * not sprinting in directions other than forward, which is picked up by more "advanced"
     * anticheats like AAC, but not NCP.
     */
    @JvmField
    val antiCheatCompatibility =
        Setting(true) {
            category = SettingCategory.MOVEMENT
            description = "Anticheat compatibility mode"
        }

    /** Don't stop walking forward when you need to break blocks in your way */
    @JvmField
    val walkWhileBreaking =
        Setting(true) {
            category = SettingCategory.MOVEMENT
            description = "Walk while breaking blocks"
        }

    /** Fill in blocks behind you */
    @JvmField
    val backfill =
        Setting(false) {
            category = SettingCategory.MOVEMENT
            description = "Fill in blocks behind you"
        }

    /** The "axis" command (aka GoalAxis) will go to an axis, or diagonal axis, at this Y level. */
    @JvmField
    val axisHeight =
        Setting(120) {
            category = SettingCategory.PATHFINDING
            description = "Y level for axis command"
        }

    /** Disconnect from the server upon arriving at your goal */
    @JvmField
    val disconnectOnArrival =
        Setting(false) {
            category = SettingCategory.PATHFINDING
            description = "Disconnect on goal arrival"
        }

    /**
     * The actual GoalNear is set this distance away from the entity you're following
     *
     * For example, set followOffsetDistance to 5 and followRadius to 0 to always stay precisely
     * 5 blocks north of your follow target.
     */
    @JvmField
    val followOffsetDistance =
        Setting(0.0) {
            category = SettingCategory.PATHFINDING
            description = "Follow offset distance"
        }

    /**
     * The actual GoalNear is set in this direction from the entity you're following. This value is
     * in degrees.
     */
    @JvmField
    val followOffsetDirection =
        Setting(0f) {
            category = SettingCategory.PATHFINDING
            description = "Follow offset direction (degrees)"
        }

    /**
     * The radius (for the GoalNear) of how close to your target position you actually have to be
     */
    @JvmField
    val followRadius =
        Setting(3) {
            category = SettingCategory.PATHFINDING
            description = "Follow radius"
        }

    /** The maximum distance to the entity you're following */
    @JvmField
    val followTargetMaxDistance =
        Setting(0) {
            category = SettingCategory.PATHFINDING
            description = "Follow max distance"
        }

    // COMBAT SETTINGS

    /** Minimum range for ranged combat (blocks) */
    @JvmField
    val rangedCombatMinRange =
        Setting(8.0) {
            category = SettingCategory.COMBAT
            description = "Minimum range for ranged combat"
        }

    /** Maximum range for ranged combat (blocks) */
    @JvmField
    val rangedCombatMaxRange =
        Setting(40.0) {
            category = SettingCategory.COMBAT
            description = "Maximum range for ranged combat"
        }

    /** Enable moving target prediction for ranged combat */
    @JvmField
    val predictTargetMovement =
        Setting(true) {
            category = SettingCategory.COMBAT
            description = "Predict target movement"
        }

    /** Number of iterations for target prediction convergence */
    @JvmField
    val targetPredictionIterations =
        Setting(3) {
            category = SettingCategory.COMBAT
            description = "Target prediction iterations"
        }

    /**
     * Minimum bow charge required before releasing (0.0 to 1.0). Lower values shoot faster but with
     * less damage and range.
     */
    @JvmField
    val minimumBowCharge =
        Setting(0.8f) {
            category = SettingCategory.COMBAT
            description = "Minimum bow charge before release"
        }

    // RENDERING SETTINGS

    /** Render trajectory paths for debugging ranged combat */
    @JvmField
    val renderTrajectory =
        Setting(false) {
            category = SettingCategory.RENDERING
            description = "Render trajectory paths"
        }

    /** Color for trajectory rendering */
    @JvmField
    val trajectoryColor =
        Setting(Color(255, 100, 100)) {
            category = SettingCategory.RENDERING
            description = "Trajectory rendering color"
        }

    /** Render predicted target positions */
    @JvmField
    val renderPredictedPosition =
        Setting(false) {
            category = SettingCategory.RENDERING
            description = "Render predicted target positions"
        }

    /**
     * Enable gamma-mode fullbright (clears lightmap texture to full white). Provides maximum
     * brightness in dark areas without affecting actual light levels.
     */
    @JvmField
    val fullbright =
        Setting(false) {
            category = SettingCategory.RENDERING
            description = "Enable fullbright"
        }

    /** Render the path */
    @JvmField
    val renderPath =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Render the path"
        }

    /** Render the path as a line instead of a frickin thingy */
    @JvmField
    val renderPathAsLine =
        Setting(false) {
            category = SettingCategory.RENDERING
            description = "Render path as line"
        }

    /** Render the goal */
    @JvmField
    val renderGoal =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Render the goal"
        }

    /**
     * Render the goal as a sick animated thingy instead of just a box (also controls animation of
     * GoalXZ if renderGoalXZBeacon is enabled)
     */
    @JvmField
    val renderGoalAnimated =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Animate goal rendering"
        }

    /** Render selection boxes */
    @JvmField
    val renderSelectionBoxes =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Render selection boxes"
        }

    /** Ignore depth when rendering the goal */
    @JvmField
    val renderGoalIgnoreDepth =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Ignore depth when rendering goal"
        }

    /**
     * Renders X/Z type Goals with the vanilla beacon beam effect. Combining this with
     * renderGoalIgnoreDepth will cause strange render clipping.
     */
    @JvmField
    val renderGoalXZBeacon =
        Setting(false) {
            category = SettingCategory.RENDERING
            description = "Render XZ goals with beacon beam"
        }

    /** Ignore depth when rendering the selection boxes (to break, to place, to walk into) */
    @JvmField
    val renderSelectionBoxesIgnoreDepth =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Ignore depth when rendering selections"
        }

    /** Ignore depth when rendering the path */
    @JvmField
    val renderPathIgnoreDepth =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Ignore depth when rendering path"
        }

    /** Line width of the path when rendered, in pixels */
    @JvmField
    val pathRenderLineWidthPixels =
        Setting(5f) {
            category = SettingCategory.RENDERING
            description = "Path line width (pixels)"
        }

    /** Line width of the goal when rendered, in pixels */
    @JvmField
    val goalRenderLineWidthPixels =
        Setting(3f) {
            category = SettingCategory.RENDERING
            description = "Goal line width (pixels)"
        }

    /**
     * Start fading out the path at 20 movements ahead, and stop rendering it entirely 30 movements
     * ahead. Improves FPS.
     */
    @JvmField
    val fadePath =
        Setting(false) {
            category = SettingCategory.RENDERING
            description = "Fade path ahead"
        }

    /**
     * Render cached chunks as semitransparent. Doesn't work with OptiFine. Rarely randomly
     * crashes.
     *
     * Can be very useful on servers with low render distance. After enabling, you may need to
     * reload the world in order for it to have an effect (e.g. disconnect and reconnect, enter then
     * exit the nether, die and respawn, etc.). This may literally kill your FPS and CPU because
     * every chunk gets recompiled twice as much as normal, since the cached version comes into
     * range, then the normal one comes from the server for real.
     *
     * Note that flowing water is cached as AVOID, which is rendered as lava. As you get closer,
     * you may therefore see lava falls being replaced with water falls.
     *
     * SOLID is rendered as stone in the overworld, netherrack in the nether, and end stone in
     * the end
     */
    @JvmField
    val renderCachedChunks =
        Setting(false) {
            category = SettingCategory.RENDERING
            description = "Render cached chunks"
        }

    /**
     * 0.0f = not visible, fully transparent (instead of setting this to 0, turn off
     * renderCachedChunks) 1.0f = fully opaque
     */
    @JvmField
    val cachedChunksOpacity =
        Setting(0.5f) {
            category = SettingCategory.RENDERING
            description = "Cached chunks opacity"
        }

    /** The size of the box that is rendered when the current goal is a GoalYLevel */
    @JvmField
    val yLevelBoxSize =
        Setting(15.0) {
            category = SettingCategory.RENDERING
            description = "Y level box size"
        }

    /** The color of the current path */
    @JvmField
    val colorCurrentPath =
        Setting(Color.RED) {
            category = SettingCategory.RENDERING
            description = "Current path color"
        }

    /** The color of the next path */
    @JvmField
    val colorNextPath =
        Setting(Color.MAGENTA) {
            category = SettingCategory.RENDERING
            description = "Next path color"
        }

    /** The color of the blocks to break */
    @JvmField
    val colorBlocksToBreak =
        Setting(Color.RED) {
            category = SettingCategory.RENDERING
            description = "Blocks to break color"
        }

    /** The color of the blocks to place */
    @JvmField
    val colorBlocksToPlace =
        Setting(Color.GREEN) {
            category = SettingCategory.RENDERING
            description = "Blocks to place color"
        }

    /** The color of the blocks to walk into */
    @JvmField
    val colorBlocksToWalkInto =
        Setting(Color.MAGENTA) {
            category = SettingCategory.RENDERING
            description = "Blocks to walk into color"
        }

    /** The color of the best path so far */
    @JvmField
    val colorBestPathSoFar =
        Setting(Color.BLUE) {
            category = SettingCategory.RENDERING
            description = "Best path so far color"
        }

    /** The color of the path to the most recent considered node */
    @JvmField
    val colorMostRecentConsidered =
        Setting(Color.CYAN) {
            category = SettingCategory.RENDERING
            description = "Most recent considered color"
        }

    /** The color of the goal box */
    @JvmField
    val colorGoalBox =
        Setting(Color.GREEN) {
            category = SettingCategory.RENDERING
            description = "Goal box color"
        }

    /** The color of the goal box when it's inverted */
    @JvmField
    val colorInvertedGoalBox =
        Setting(Color.RED) {
            category = SettingCategory.RENDERING
            description = "Inverted goal box color"
        }

    /** The color of all selections */
    @JvmField
    val colorSelection =
        Setting(Color.CYAN) {
            category = SettingCategory.RENDERING
            description = "Selection color"
        }

    /** The color of the selection pos 1 */
    @JvmField
    val colorSelectionPos1 =
        Setting(Color.BLACK) {
            category = SettingCategory.RENDERING
            description = "Selection pos 1 color"
        }

    /** The color of the selection pos 2 */
    @JvmField
    val colorSelectionPos2 =
        Setting(Color.ORANGE) {
            category = SettingCategory.RENDERING
            description = "Selection pos 2 color"
        }

    /** The opacity of the selection. 0 is completely transparent, 1 is completely opaque */
    @JvmField
    val selectionOpacity =
        Setting(0.5f) {
            category = SettingCategory.RENDERING
            description = "Selection opacity"
        }

    /** Line width of the goal when rendered, in pixels */
    @JvmField
    val selectionLineWidth =
        Setting(2f) {
            category = SettingCategory.RENDERING
            description = "Selection line width (pixels)"
        }

    /** Render selections */
    @JvmField
    val renderSelection =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Render selections"
        }

    /** Ignore depth when rendering selections */
    @JvmField
    val renderSelectionIgnoreDepth =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Ignore depth when rendering selections"
        }

    /** Render selection corners */
    @JvmField
    val renderSelectionCorners =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Render selection corners"
        }

    /** Enable debug rendering */
    @JvmField
    val debugEnabled =
        Setting(false) {
            category = SettingCategory.RENDERING
            description = "Enable debug rendering"
        }

    // PATHFINDING DEBUG SETTINGS

    /** Enable pathfinding debug visualization (post-hoc inspection of A* search) */
    @JvmField
    val pathfindingDebugEnabled =
        Setting(false) {
            category = SettingCategory.RENDERING
            description = "Enable pathfinding debug visualization"
        }

    /** Capture pathfinding snapshots for post-hoc analysis */
    @JvmField
    val pathfindingDebugCapture =
        Setting(false) {
            category = SettingCategory.RENDERING
            description = "Capture pathfinding snapshots"
        }

    /** Maximum nodes to display in pathfinding debug visualization */
    @JvmField
    val pathfindingDebugMaxNodes =
        Setting(5000) {
            category = SettingCategory.RENDERING
            description = "Max nodes to display in debug visualization"
            range(100.0, 50000.0)
        }

    /** Show edges between nodes in pathfinding debug visualization */
    @JvmField
    val pathfindingDebugShowEdges =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Show edges between nodes"
        }

    /** Show cost labels on nearby nodes in pathfinding debug */
    @JvmField
    val pathfindingDebugShowLabels =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Show cost labels on nearby nodes"
        }

    /** Distance for LOD transitions in pathfinding debug (blocks) */
    @JvmField
    val pathfindingDebugLODDistance =
        Setting(30.0) {
            category = SettingCategory.RENDERING
            description = "LOD transition distance (blocks)"
            range(10.0, 100.0)
        }

    /** Renders the raytraces that are performed by the elytra fly calculation. */
    @JvmField
    val elytraRenderRaytraces =
        Setting(false) {
            category = SettingCategory.RENDERING
            description = "Render elytra raytraces"
        }

    /**
     * Renders the raytraces that are used in the hitbox part of the elytra fly calculation.
     * Requires elytraRenderRaytraces.
     */
    @JvmField
    val elytraRenderHitboxRaytraces =
        Setting(false) {
            category = SettingCategory.RENDERING
            description = "Render elytra hitbox raytraces"
        }

    /** Renders the best elytra flight path that was simulated each tick. */
    @JvmField
    val elytraRenderSimulation =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Render elytra simulation"
        }

    /** Disable chunk occlusion culling in freecam to prevent disappearing chunks */
    @JvmField
    val freecamDisableOcclusion =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Disable chunk occlusion culling in freecam (prevents disappearing chunks)"
        }

    /** Reload chunks when toggling freecam on/off */
    @JvmField
    val freecamReloadChunks =
        Setting(false) {
            category = SettingCategory.RENDERING
            description = "Reload chunks when toggling freecam (fixes culling, causes brief lag)"
        }

    /** Default freecam mode (STATIC: fixed position, FOLLOW: tracks player) */
    @JvmField
    val freecamDefaultMode =
        Setting(FreecamMode.STATIC) {
            category = SettingCategory.RENDERING
            description = "Default freecam mode (STATIC: fixed position, FOLLOW: tracks player)"
        }

    /** Distance to teleport when left-clicking sky/void (no block hit) */
    @JvmField
    val freecamTeleportDistance =
        Setting(25.0) {
            category = SettingCategory.RENDERING
            description = "Distance to teleport when left-clicking sky/void (no block hit)"
            range(1.0, 100.0)
        }

    /** Ticks to hold right-click for pathfinding (10 = 0.5s) */
    @JvmField
    val freecamPathfindHoldDuration =
        Setting(10) {
            category = SettingCategory.RENDERING
            description = "Ticks to hold right-click for pathfinding (10 = 0.5s)"
            range(1.0, 40.0)
        }

    /** Max raytrace distance for pathfinding click */
    @JvmField
    val freecamPathfindDistance =
        Setting(100.0) {
            category = SettingCategory.RENDERING
            description = "Max raytrace distance for pathfinding click"
            range(10.0, 256.0)
        }

    // HIGHLIGHT RENDERING SETTINGS

    /** Use filled highlight rendering for goal blocks */
    @JvmField
    val renderGoalHighlight =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Use filled highlight rendering for goal blocks"
        }

    /** Use top-side highlighting for path rendering */
    @JvmField
    val renderPathHighlight =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Use top-side highlighting for path rendering"
        }

    /** Show blocks being mined with highlighting */
    @JvmField
    val renderMiningBlocks =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Show blocks being mined with highlighting"
        }

    /** Color for mining block visualization */
    @JvmField
    val colorMiningBlocks =
        Setting(Color.ORANGE) {
            category = SettingCategory.RENDERING
            description = "Color for mining block visualization"
        }

    /** Transparency for highlight rendering (0.0-1.0) */
    @JvmField
    val highlightAlpha =
        Setting(0.3f) {
            category = SettingCategory.RENDERING
            description = "Transparency for highlight rendering (0.0-1.0)"
            range(0.0, 1.0)
        }

    /** Use filled highlight rendering for selection boxes (blocks to break/place/walk into) */
    @JvmField
    val renderSelectionBoxesHighlight =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Use filled highlight rendering for selection boxes"
        }

    // PRIORITY-BASED HIGHLIGHT RENDERING

    /** Enable priority-based highlight rendering with visual hierarchy */
    @JvmField
    val renderPriorityBasedHighlight =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Enable priority-based highlight with visual hierarchy"
        }

    /** Opacity for active mining targets (priority 0) */
    @JvmField
    val highlightOpacityActiveMining =
        Setting(0.5f) {
            category = SettingCategory.RENDERING
            description = "Opacity for active mining targets (priority 0)"
            range(0.0, 1.0)
        }

    /** Opacity for immediate path position (priority 1) */
    @JvmField
    val highlightOpacityImmediatePath =
        Setting(0.65f) {
            category = SettingCategory.RENDERING
            description = "Opacity for immediate path position (priority 1)"
            range(0.0, 1.0)
        }

    /** Opacity for current path (priority 3) */
    @JvmField
    val highlightOpacityCurrentPath =
        Setting(0.25f) {
            category = SettingCategory.RENDERING
            description = "Opacity for current path (priority 3)"
            range(0.0, 1.0)
        }

    /** Opacity for discovered ores (priority 5) */
    @JvmField
    val highlightOpacityDiscoveredOres =
        Setting(0.1f) {
            category = SettingCategory.RENDERING
            description = "Opacity for discovered ores (priority 5)"
            range(0.0, 1.0)
        }

    /** Opacity for historical path (priority 6) */
    @JvmField
    val highlightOpacityHistoricalPath =
        Setting(0.25f) {
            category = SettingCategory.RENDERING
            description = "Opacity for historical path (priority 6)"
            range(0.0, 1.0)
        }

    /** Use ore-specific colors (diamond=blue, gold=gold, etc.) */
    @JvmField
    val highlightOreSpecificColors =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Use ore-specific colors (diamond=blue, gold=gold, etc.)"
        }

    /** Show already-traversed path positions */
    @JvmField
    val highlightShowHistoricalPath =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Show already-traversed path positions"
        }

    /** Pulse animation for active mining targets */
    @JvmField
    val highlightPulseActiveMining =
        Setting(true) {
            category = SettingCategory.RENDERING
            description = "Pulse animation for active mining targets"
        }

    /** Fade blocks with distance (50% at 64 blocks) */
    @JvmField
    val highlightDistanceFading =
        Setting(false) {
            category = SettingCategory.RENDERING
            description = "Fade blocks with distance (breaks highlight grouping)"
        }

    // BUILDING SETTINGS

    /**
     * A list of blocks to be treated as if they're air.
     *
     * If a schematic asks for air at a certain position, and that position currently contains a
     * block on this list, it will be treated as correct.
     */
    @JvmField
    val buildIgnoreBlocks =
        Setting(arrayListOf<Block>()) {
            category = SettingCategory.BUILDING
            description = "Blocks treated as air in schematics"
        }

    /**
     * A list of blocks to be treated as correct.
     *
     * If a schematic asks for any block on this list at a certain position, it will be treated
     * as correct, regardless of what it currently is.
     */
    @JvmField
    val buildSkipBlocks =
        Setting(arrayListOf<Block>()) {
            category = SettingCategory.BUILDING
            description = "Blocks always treated as correct"
        }

    /**
     * A mapping of blocks to blocks treated as correct in their position
     *
     * If a schematic asks for a block on this mapping, all blocks on the mapped list will be
     * accepted at that location as well
     */
    @JvmField
    val buildValidSubstitutes =
        Setting(hashMapOf<Block, List<Block>>()) {
            category = SettingCategory.BUILDING
            description = "Valid block substitutes"
        }

    /**
     * A mapping of blocks to be built instead
     *
     * If a schematic asks for a block on this mapping, Maestro will place the first placeable
     * block in the mapped list
     */
    @JvmField
    val buildSubstitutes =
        Setting(hashMapOf<Block, List<Block>>()) {
            category = SettingCategory.BUILDING
            description = "Block substitutes to build"
        }

    /**
     * A list of blocks to become air
     *
     * If a schematic asks for a block on this list, only air will be accepted at that location
     * (and nothing on buildIgnoreBlocks)
     */
    @JvmField
    val okIfAir =
        Setting(arrayListOf<Block>()) {
            category = SettingCategory.BUILDING
            description = "Blocks that can be air"
        }

    /**
     * If this is true, the builder will treat all non-air blocks as correct. It will only place new
     * blocks.
     */
    @JvmField
    val buildIgnoreExisting =
        Setting(false) {
            category = SettingCategory.BUILDING
            description = "Ignore existing blocks when building"
        }

    /**
     * If this is true, the builder will ignore directionality of certain blocks like glazed
     * terracotta.
     */
    @JvmField
    val buildIgnoreDirection =
        Setting(false) {
            category = SettingCategory.BUILDING
            description = "Ignore block directionality"
        }

    /** A list of names of block properties the builder will ignore. */
    @JvmField
    val buildIgnoreProperties =
        Setting(arrayListOf<String>()) {
            category = SettingCategory.BUILDING
            description = "Block properties to ignore"
        }

    /** Don't consider the next layer in builder until the current one is done */
    @JvmField
    val buildInLayers =
        Setting(false) {
            category = SettingCategory.BUILDING
            description = "Build in layers"
        }

    /**
     * false = build from bottom to top
     *
     * true = build from top to bottom
     */
    @JvmField
    val layerOrder =
        Setting(false) {
            category = SettingCategory.BUILDING
            description = "Layer build order (false=bottom-up)"
        }

    /** How high should the individual layers be? */
    @JvmField
    val layerHeight =
        Setting(1) {
            category = SettingCategory.BUILDING
            description = "Individual layer height"
        }

    /**
     * Start building the schematic at a specific layer. Can help on larger builds when schematic
     * wants to break things its already built
     */
    @JvmField
    val startAtLayer =
        Setting(0) {
            category = SettingCategory.BUILDING
            description = "Starting layer number"
        }

    /** If a layer is unable to be constructed, just skip it. */
    @JvmField
    val skipFailedLayers =
        Setting(false) {
            category = SettingCategory.BUILDING
            description = "Skip failed layers"
        }

    /** Only build the selected part of schematics */
    @JvmField
    val buildOnlySelection =
        Setting(false) {
            category = SettingCategory.BUILDING
            description = "Only build selection"
        }

    /**
     * How far to move before repeating the build. 0 to disable repeating on a certain axis, 0,0,0
     * to disable entirely
     */
    @JvmField
    val buildRepeat =
        Setting(Vec3i(0, 0, 0)) {
            category = SettingCategory.BUILDING
            description = "Build repeat offset"
        }

    /** How many times to buildrepeat. -1 for infinite. */
    @JvmField
    val buildRepeatCount =
        Setting(-1) {
            category = SettingCategory.BUILDING
            description = "Build repeat count"
        }

    /**
     * Don't notify schematics that they are moved. e.g. replacing will replace the same spots for
     * every repetition Mainly for backward compatibility.
     */
    @JvmField
    val buildRepeatSneaky =
        Setting(true) {
            category = SettingCategory.BUILDING
            description = "Don't notify schematics of movement"
        }

    /**
     * Allow standing above a block while mining it, in BuilderTask
     *
     * Experimental
     */
    @JvmField
    val breakFromAbove =
        Setting(false) {
            category = SettingCategory.BUILDING
            description = "Break from above (experimental)"
        }

    /**
     * As well as breaking from above, set a goal to up and to the side of all blocks to break.
     *
     * Never turn this on without also turning on breakFromAbove.
     */
    @JvmField
    val goalBreakFromAbove =
        Setting(false) {
            category = SettingCategory.BUILDING
            description = "Set goal up and to side when breaking"
        }

    /** Build in map art mode, which makes maestro only care about the top block in each column */
    @JvmField
    val mapArtMode =
        Setting(false) {
            category = SettingCategory.BUILDING
            description = "Map art mode"
        }

    /** Override builder's behavior to not attempt to correct blocks that are currently water */
    @JvmField
    val okIfWater =
        Setting(false) {
            category = SettingCategory.BUILDING
            description = "Accept water blocks as correct"
        }

    /** The set of incorrect blocks can never grow beyond this size */
    @JvmField
    val incorrectSize =
        Setting(100) {
            category = SettingCategory.BUILDING
            description = "Max incorrect blocks set size"
        }

    /**
     * Multiply the cost of breaking a block that's correct in the builder's schematic by this
     * coefficient
     */
    @JvmField
    val breakCorrectBlockPenaltyMultiplier =
        Setting(10.0) {
            category = SettingCategory.BUILDING
            description = "Penalty for breaking correct blocks"
        }

    /**
     * Multiply the cost of placing a block that's incorrect in the builder's schematic by this
     * coefficient
     */
    @JvmField
    val placeIncorrectBlockPenaltyMultiplier =
        Setting(2.0) {
            category = SettingCategory.BUILDING
            description = "Penalty for placing incorrect blocks"
        }

    /**
     * When this setting is true, build a schematic with the highest X coordinate being the origin,
     * instead of the lowest
     */
    @JvmField
    val schematicOrientationX =
        Setting(false) {
            category = SettingCategory.BUILDING
            description = "Schematic X orientation (highest=origin)"
        }

    /**
     * When this setting is true, build a schematic with the highest Y coordinate being the origin,
     * instead of the lowest
     */
    @JvmField
    val schematicOrientationY =
        Setting(false) {
            category = SettingCategory.BUILDING
            description = "Schematic Y orientation (highest=origin)"
        }

    /**
     * When this setting is true, build a schematic with the highest Z coordinate being the origin,
     * instead of the lowest
     */
    @JvmField
    val schematicOrientationZ =
        Setting(false) {
            category = SettingCategory.BUILDING
            description = "Schematic Z orientation (highest=origin)"
        }

    /**
     * Rotates the schematic before building it. Possible values are NONE, CLOCKWISE_90,
     * CLOCKWISE_180, COUNTERCLOCKWISE_90
     */
    @JvmField
    val buildSchematicRotation =
        Setting(Rotation.NONE) {
            category = SettingCategory.BUILDING
            description = "Schematic rotation"
        }

    /**
     * Mirrors the schematic before building it. Possible values are FRONT_BACK, LEFT_RIGHT
     */
    @JvmField
    val buildSchematicMirror =
        Setting(Mirror.NONE) {
            category = SettingCategory.BUILDING
            description = "Schematic mirror"
        }

    /**
     * The fallback used by the build command when no extension is specified. This may be useful if
     * schematics of a particular format are used often, and the user does not wish to have to
     * specify the extension with every usage.
     */
    @JvmField
    val schematicFallbackExtension =
        Setting("schematic") {
            category = SettingCategory.BUILDING
            description = "Default schematic file extension"
        }

    /**
     * Distance to scan every tick for updates. Expanding this beyond player reach distance (i.e.
     * setting it to 6 or above) is only necessary in very large schematics where rescanning the
     * whole thing is costly.
     */
    @JvmField
    val builderTickScanRadius =
        Setting(5) {
            category = SettingCategory.BUILDING
            description = "Builder scan radius per tick"
        }

    /**
     * Trim incorrect positions too far away, helps performance but hurts reliability in very large
     * schematics
     */
    @JvmField
    val distanceTrim =
        Setting(true) {
            category = SettingCategory.BUILDING
            description = "Trim far incorrect positions"
        }

    // MINING SETTINGS

    /** Rescan for the goal once every 5 ticks. Set to 0 to disable. */
    @JvmField
    val mineGoalUpdateInterval =
        Setting(5) {
            category = SettingCategory.MINING
            description = "Mine goal rescan interval (ticks)"
        }

    /**
     * After finding this many instances of the target block in the cache, it will stop expanding
     * outward the chunk search.
     */
    @JvmField
    val maxCachedWorldScanCount =
        Setting(10) {
            category = SettingCategory.MINING
            description = "Max cached world scan count"
        }

    /**
     * Mine will not scan for or remember more than this many target locations. Note that the number
     * of locations retrieved from cache is additionally limited by maxCachedWorldScanCount.
     */
    @JvmField
    val mineMaxOreLocationsCount =
        Setting(64) {
            category = SettingCategory.MINING
            description = "Max ore locations to remember"
        }

    /**
     * Sets the minimum y level whilst mining - set to 0 to turn off. if world has negative y
     * values, subtract the min world height to get the value to put here
     */
    @JvmField
    val minYLevelWhileMining =
        Setting(0) {
            category = SettingCategory.MINING
            description = "Minimum Y level while mining"
        }

    /** Sets the maximum y level to mine ores at. */
    @JvmField
    val maxYLevelWhileMining =
        Setting(2031) {
            category = SettingCategory.MINING
            description = "Maximum Y level while mining"
        }

    /**
     * This will only allow maestro to mine exposed ores, can be used to stop ore obfuscators on
     * servers that use them.
     */
    @JvmField
    val allowOnlyExposedOres =
        Setting(false) {
            category = SettingCategory.MINING
            description = "Only mine exposed ores"
        }

    /**
     * When allowOnlyExposedOres is enabled this is the distance around to search.
     *
     * It is recommended to keep this value low, as it dramatically increases calculation times.
     */
    @JvmField
    val allowOnlyExposedOresDistance =
        Setting(1) {
            category = SettingCategory.MINING
            description = "Exposed ore search distance"
        }

    /**
     * When GetToBlock or non-legit Mine doesn't know any locations for the desired block, explore
     * randomly instead of giving up.
     */
    @JvmField
    val exploreForBlocks =
        Setting(true) {
            category = SettingCategory.MINING
            description = "Explore randomly when blocks unknown"
        }

    /**
     * While exploring the world, offset the closest unloaded chunk by this much in both axes.
     *
     * This can result in more efficient loading, if you set this to the render distance.
     */
    @JvmField
    val worldExploringChunkOffset =
        Setting(0) {
            category = SettingCategory.MINING
            description = "World exploring chunk offset"
        }

    /**
     * Take the 10 closest chunks, even if they aren't strictly tied for distance metric from
     * origin.
     */
    @JvmField
    val exploreChunkSetMinimumSize =
        Setting(10) {
            category = SettingCategory.MINING
            description = "Explore chunk set minimum size"
        }

    /**
     * Attempt to maintain Y coordinate while exploring
     *
     * -1 to disable
     */
    @JvmField
    val exploreMaintainY =
        Setting(64) {
            category = SettingCategory.MINING
            description = "Maintain Y while exploring"
        }

    /** Replant normal Crops while farming and leave cactus and sugarcane to regrow */
    @JvmField
    val replantCrops =
        Setting(true) {
            category = SettingCategory.MINING
            description = "Replant crops while farming"
        }

    /**
     * Replant nether wart while farming. This setting only has an effect when replantCrops is also
     * enabled
     */
    @JvmField
    val replantNetherWart =
        Setting(false) {
            category = SettingCategory.MINING
            description = "Replant nether wart"
        }

    /** Farming will scan for at most this many blocks. */
    @JvmField
    val farmMaxScanSize =
        Setting(256) {
            category = SettingCategory.MINING
            description = "Farm max scan size"
        }

    /**
     * When the cache scan gives fewer blocks than the maximum threshold (but still above zero),
     * scan the main world too.
     *
     * Only if you have a beefy CPU and automatically mine blocks that are in cache
     */
    @JvmField
    val extendCacheOnThreshold =
        Setting(false) {
            category = SettingCategory.MINING
            description = "Extend cache scan on threshold"
        }

    /**
     * While mining, should it also consider dropped items of the correct type as a pathing
     * destination (as well as ore blocks)?
     */
    @JvmField
    val mineScanDroppedItems =
        Setting(true) {
            category = SettingCategory.MINING
            description = "Scan for dropped items while mining"
        }

    /**
     * While mining, wait this number of milliseconds after mining an ore to see if it will drop an
     * item instead of immediately going onto the next one
     */
    @JvmField
    val mineDropLoiterDurationMSThanksLouca =
        Setting(250L) {
            category = SettingCategory.MINING
            description = "Wait for drops after mining (ms)"
        }

    /**
     * Disallow MineBehavior from using X-Ray to see where the ores are. Turn this option on to
     * force it to mine "legit" where it will only mine an ore once it can actually see it, so it
     * won't do or know anything that a normal player couldn't. If you don't want it to look like
     * you're X-Raying, turn this on This will always explore, regardless of exploreForBlocks
     */
    @JvmField
    val legitMine =
        Setting(false) {
            category = SettingCategory.MINING
            description = "Mine without X-ray (legit mode)"
        }

    /** What Y level to go to for legit strip mining */
    @JvmField
    val legitMineYLevel =
        Setting(-59) {
            category = SettingCategory.MINING
            description = "Legit strip mining Y level"
        }

    /**
     * Magically see ores that are separated diagonally from existing ores. Basically like mining
     * around the ores that it finds in case there's one there touching it diagonally, except it
     * checks it un-legit-ly without having the mine blocks to see it. You can decide whether this
     * looks plausible or not.
     *
     * This is disabled because it results in some weird behavior. For example, it can "see"
     * the top block of a vein of iron_ore through a lava lake. This isn't an issue normally since
     * it won't consider anything touching lava, so it just ignores it. However, this setting
     * expands that and allows it to see the entire vein so it'll mine under the lava lake to get
     * the iron that it can reach without mining blocks adjacent to lava. This really defeats the
     * purpose of legitMine since a player could never do that, so that's one reason why its
     * disabled
     */
    @JvmField
    val legitMineIncludeDiagonals =
        Setting(false) {
            category = SettingCategory.MINING
            description = "Legit mine include diagonals"
        }

    /**
     * When mining block of a certain type, try to mine two at once instead of one. If the block
     * above is also a goal block, set GoalBlock instead of GoalTwoBlocks If the block below is also
     * a goal block, set GoalBlock to the position one down instead of GoalTwoBlocks
     */
    @JvmField
    val forceInternalMining =
        Setting(true) {
            category = SettingCategory.MINING
            description = "Force internal mining (mine two at once)"
        }

    /**
     * Modification to the previous setting, only has effect if forceInternalMining is true If true,
     * only apply the previous setting if the block adjacent to the goal isn't air.
     */
    @JvmField
    val internalMiningAirException =
        Setting(true) {
            category = SettingCategory.MINING
            description = "Internal mining air exception"
        }

    /** Stop using tools just before they are going to break. */
    @JvmField
    val itemSaver =
        Setting(false) {
            category = SettingCategory.MINING
            description = "Stop using tools before breaking"
        }

    /** Durability to leave on the tool when using itemSaver */
    @JvmField
    val itemSaverThreshold =
        Setting(10) {
            category = SettingCategory.MINING
            description = "Item saver durability threshold"
        }

    /**
     * Always prefer silk touch tools over regular tools. This will not sacrifice speed, but it will
     * always prefer silk touch tools over other tools of the same speed. This includes always
     * choosing ANY silk touch tool over your hand.
     */
    @JvmField
    val preferSilkTouch =
        Setting(false) {
            category = SettingCategory.MINING
            description = "Prefer silk touch tools"
        }

    /** Use sword to mine. */
    @JvmField
    val useSwordToMine =
        Setting(true) {
            category = SettingCategory.MINING
            description = "Use sword to mine"
        }

    // ADVANCED SETTINGS

    /** allows maestro to save death waypoints */
    @JvmField
    val doDeathWaypoints =
        Setting(true) {
            category = SettingCategory.ADVANCED
            description = "Save death waypoints"
        }

    /**
     * The big one. Download all chunks in simplified 2-bit format and save them for better
     * very-long-distance pathing.
     */
    @JvmField
    val chunkCaching =
        Setting(true) {
            category = SettingCategory.ADVANCED
            description = "Enable chunk caching"
        }

    /**
     * On save, delete from RAM any cached regions that are more than 1024 blocks away from the
     * player
     */
    @JvmField
    val pruneRegionsFromRAM =
        Setting(true) {
            category = SettingCategory.ADVANCED
            description = "Prune distant cached regions"
        }

    /**
     * The chunk packer queue can never grow to larger than this, if it does, the oldest chunks are
     * discarded
     *
     * The newest chunks are kept, so that if you're moving in a straight line quickly then stop,
     * your immediate render distance is still included
     */
    @JvmField
    val chunkPackerQueueMaxSize =
        Setting(2000) {
            category = SettingCategory.ADVANCED
            description = "Chunk packer queue max size"
        }

    /**
     * Cached chunks (regardless of if they're in RAM or saved to disk) expire and are deleted after
     * this number of seconds -1 to disable
     */
    @JvmField
    val cachedChunksExpirySeconds =
        Setting(-1L) {
            category = SettingCategory.ADVANCED
            description = "Cached chunks expiry (seconds)"
        }

    /**
     * Turn this on if your exploration filter is enormous, you don't want it to check if it's done,
     * and you are just fine with it just hanging on completion
     */
    @JvmField
    val disableCompletionCheck =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Disable exploration completion check"
        }

    /** Shows popup message in the upper right corner, similarly to when you make an advancement */
    @JvmField
    val logAsToast =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Log as toast popup"
        }

    /** Print all the debug messages to chat */
    @JvmField
    val chatDebug =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Print debug messages to chat"
        }

    /**
     * Allow chat based control of Maestro. Most likely should be disabled when Maestro is imported
     * for use in something else
     */
    @JvmField
    val chatControl =
        Setting(true) {
            category = SettingCategory.ADVANCED
            description = "Allow chat-based control"
        }

    /**
     * Some clients like Impact try to force chatControl to off, so here's a second setting to do it
     * anyway
     */
    @JvmField
    val chatControlAnyway =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Force chat control anyway"
        }

    /** Whether to allow you to run Maestro commands with the prefix */
    @JvmField
    val prefixControl =
        Setting(true) {
            category = SettingCategory.ADVANCED
            description = "Allow command prefix"
        }

    /** The command prefix for chat control */
    @JvmField
    val prefix =
        Setting("#") {
            category = SettingCategory.ADVANCED
            description = "Command prefix"
        }

    /** Use a short Maestro prefix [B] instead of [Maestro] when logging to chat */
    @JvmField
    val shortMaestroPrefix =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Use short prefix [B]"
        }

    /** Use a modern message tag instead of a prefix when logging to chat */
    @JvmField
    val useMessageTag =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Use message tag instead of prefix"
        }

    /** Echo commands to chat when they are run */
    @JvmField
    val echoCommands =
        Setting(true) {
            category = SettingCategory.ADVANCED
            description = "Echo commands to chat"
        }

    /** Censor coordinates in goals and block positions */
    @JvmField
    val censorCoordinates =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Censor coordinates"
        }

    /** Censor arguments to ran commands, to hide, for example, coordinates to #goal */
    @JvmField
    val censorRanCommands =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Censor command arguments"
        }

    /** Print out ALL command exceptions as a stack trace to stdout, even simple syntax errors */
    @JvmField
    val verboseCommandExceptions =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Verbose command exceptions"
        }

    /** Desktop notifications */
    @JvmField
    val desktopNotifications =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Enable desktop notifications"
        }

    /** Desktop notification on path complete */
    @JvmField
    val notificationOnPathComplete =
        Setting(true) {
            category = SettingCategory.ADVANCED
            description = "Notify on path complete"
        }

    /** Desktop notification on farm fail */
    @JvmField
    val notificationOnFarmFail =
        Setting(true) {
            category = SettingCategory.ADVANCED
            description = "Notify on farm fail"
        }

    /** Desktop notification on build finished */
    @JvmField
    val notificationOnBuildFinished =
        Setting(true) {
            category = SettingCategory.ADVANCED
            description = "Notify on build finished"
        }

    /** Desktop notification on explore finished */
    @JvmField
    val notificationOnExploreFinished =
        Setting(true) {
            category = SettingCategory.ADVANCED
            description = "Notify on explore finished"
        }

    /** Desktop notification on mine fail */
    @JvmField
    val notificationOnMineFail =
        Setting(true) {
            category = SettingCategory.ADVANCED
            description = "Notify on mine fail"
        }

    /**
     * The number of ticks of elytra movement to simulate while firework boost is not active. Higher
     * values are computationally more expensive.
     */
    @JvmField
    val elytraSimulationTicks =
        Setting(20) {
            category = SettingCategory.ADVANCED
            description = "Elytra simulation ticks"
        }

    /**
     * The maximum allowed deviation in pitch from a direct line-of-sight to the flight target.
     * Higher values are computationally more expensive.
     */
    @JvmField
    val elytraPitchRange =
        Setting(25) {
            category = SettingCategory.ADVANCED
            description = "Elytra pitch range"
        }

    /**
     * The minimum speed that the player can drop to (in blocks/tick) before a firework is
     * automatically deployed.
     */
    @JvmField
    val elytraFireworkSpeed =
        Setting(1.2) {
            category = SettingCategory.ADVANCED
            description = "Elytra firework deploy speed"
        }

    /**
     * The delay after the player's position is set-back by the server that a firework may be
     * automatically deployed. Value is in ticks.
     */
    @JvmField
    val elytraFireworkSetbackUseDelay =
        Setting(15) {
            category = SettingCategory.ADVANCED
            description = "Elytra firework setback delay"
        }

    /**
     * The minimum padding value that is added to the player's hitbox when considering which point
     * to fly to on the path. High values can result in points not being considered which are
     * otherwise safe to fly to. Low values can result in flight paths which are extremely tight,
     * and there's the possibility of crashing due to getting too low to the ground.
     */
    @JvmField
    val elytraMinimumAvoidance =
        Setting(0.2) {
            category = SettingCategory.ADVANCED
            description = "Elytra minimum hitbox padding"
        }

    /** If enabled, avoids using fireworks when descending along the flight path. */
    @JvmField
    val elytraConserveFireworks =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Conserve fireworks when descending"
        }

    /** Automatically path to and jump off of ledges to initiate elytra flight when grounded. */
    @JvmField
    val elytraAutoJump =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Auto jump for elytra flight"
        }

    /**
     * The seed used to generate chunks for long distance elytra path-finding in the nether.
     * Defaults to 2b2t's nether seed.
     */
    @JvmField
    val elytraNetherSeed =
        Setting(146008555100680L) {
            category = SettingCategory.ADVANCED
            description = "Nether seed for elytra pathfinding"
        }

    /**
     * Whether nether-pathfinder should generate terrain based on elytraNetherSeed. If
     * false all chunks that haven't been loaded are assumed to be air.
     */
    @JvmField
    val elytraPredictTerrain =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Predict nether terrain for elytra"
        }

    /** Automatically swap the current elytra with a new one when the durability gets too low */
    @JvmField
    val elytraAutoSwap =
        Setting(true) {
            category = SettingCategory.ADVANCED
            description = "Auto swap elytra"
        }

    /** The minimum durability an elytra can have before being swapped */
    @JvmField
    val elytraMinimumDurability =
        Setting(5) {
            category = SettingCategory.ADVANCED
            description = "Elytra minimum durability"
        }

    /** The minimum fireworks before landing early for safety */
    @JvmField
    val elytraMinFireworksBeforeLanding =
        Setting(5) {
            category = SettingCategory.ADVANCED
            description = "Minimum fireworks before landing"
        }

    /** Automatically land when elytra is almost out of durability, or almost out of fireworks */
    @JvmField
    val elytraAllowEmergencyLand =
        Setting(true) {
            category = SettingCategory.ADVANCED
            description = "Allow emergency landing"
        }

    /** Time between culling far away chunks from the nether pathfinder chunk cache */
    @JvmField
    val elytraTimeBetweenCacheCullSecs =
        Setting(TimeUnit.MINUTES.toSeconds(3)) {
            category = SettingCategory.ADVANCED
            description = "Elytra cache cull interval (seconds)"
        }

    /** Maximum distance chunks can be before being culled from the nether pathfinder chunk cache */
    @JvmField
    val elytraCacheCullDistance =
        Setting(5000) {
            category = SettingCategory.ADVANCED
            description = "Elytra cache cull distance"
        }

    /** Should elytra consider nether brick a valid landing block */
    @JvmField
    val elytraAllowLandOnNetherFortress =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Allow landing on nether fortresses"
        }

    /** Has the user read and understood the elytra terms and conditions */
    @JvmField
    val elytraTermsAccepted =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Elytra terms accepted"
        }

    /** Verbose chat logging in elytra mode */
    @JvmField
    val elytraChatSpam =
        Setting(false) {
            category = SettingCategory.ADVANCED
            description = "Elytra verbose logging"
        }

    // COORDINATION SETTINGS

    /** Enable multi-agent coordination via gRPC */
    @JvmField
    val coordinationEnabled =
        Setting(false) {
            category = SettingCategory.COORDINATION
            description = "Enable multi-agent coordination"
        }

    /** Coordinator server host to connect to */
    @JvmField
    val coordinationHost =
        Setting("localhost") {
            category = SettingCategory.COORDINATION
            description = "Coordinator server host"
        }

    /** Coordinator server port */
    @JvmField
    val coordinationPort =
        Setting(9090) {
            category = SettingCategory.COORDINATION
            description = "Coordinator server port"
        }

    /** Radius for area claims (blocks) */
    @JvmField
    val coordinationClaimRadius =
        Setting(16.0) {
            category = SettingCategory.COORDINATION
            description = "Area claim radius (blocks)"
        }

    // JAVA-ONLY SETTINGS (NOT SERIALIZABLE)

    /**
     * The function that is called when Maestro will log to chat. This function can be added to via
     * andThen, or it can completely be overridden via setting value;
     */
    @JvmField
    val logger =
        Setting<Consumer<Component>>(
            Consumer { msg ->
                try {
                    val tag = if (useMessageTag.value) Helper.MESSAGE_TAG else null
                    Minecraft
                        .getInstance()
                        .gui.chat
                        .addMessage(msg, null, tag)
                } catch (t: Throwable) {
                    LOGGER.warn("Failed to log message to chat: ${msg.string}", t)
                }
            },
        ) {
            javaOnly = true
        }

    /**
     * The function that is called when Maestro will send a desktop notification. This function can
     * be added to via andThen, or it can completely be overridden via setting value;
     */
    @JvmField
    val notifier =
        Setting<BiConsumer<String, Boolean>>(
            BiConsumer { title, error ->
                Helper.notifySystem(title, error)
            },
        ) {
            javaOnly = true
        }

    /**
     * The function that is called when Maestro will show a toast. This function can be added to via
     * andThen, or it can completely be overridden via setting value;
     */
    @JvmField
    val toaster: Setting<BiConsumer<Component, Component>> =
        Setting<BiConsumer<Component, Component>>(
            BiConsumer { title, message ->
                Toast.addOrUpdate(title, message)
            },
        ) {
            javaOnly = true
        }

    // REFLECTION INITIALIZATION (must be at the end)

    /** A map of lowercase setting field names to their respective setting */
    @JvmField
    val byLowerName: Map<String, Setting<*>>

    /** A list of all settings */
    @JvmField
    val allSettings: List<Setting<*>>

    @JvmField
    val settingTypes: Map<Setting<*>, Type>

    init {
        val fields = javaClass.declaredFields

        val tmpByName = mutableMapOf<String, Setting<*>>()
        val tmpAll = mutableListOf<Setting<*>>()
        val tmpSettingTypes = mutableMapOf<Setting<*>, Type>()

        try {
            for (field in fields) {
                if (field.type == Setting::class.java) {
                    field.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val setting = field.get(this) as Setting<*>
                    var name = field.name
                    setting.name = name
                    name = name.lowercase(Locale.ROOT)
                    if (tmpByName.containsKey(name)) {
                        throw IllegalStateException("Duplicate setting name")
                    }
                    tmpByName[name] = setting
                    tmpAll.add(setting)
                    val paramType = (field.genericType as ParameterizedType).actualTypeArguments[0]
                    setting.type = paramType
                    tmpSettingTypes[setting] = paramType
                }
            }
        } catch (e: IllegalAccessException) {
            throw IllegalStateException(e)
        }

        @Suppress("UNCHECKED_CAST")
        byLowerName = Collections.unmodifiableMap(tmpByName) as Map<String, Setting<*>>
        @Suppress("UNCHECKED_CAST")
        allSettings = Collections.unmodifiableList(tmpAll) as List<Setting<*>>
        @Suppress("UNCHECKED_CAST")
        settingTypes = Collections.unmodifiableMap(tmpSettingTypes) as Map<Setting<*>, Type>
    }

    /**
     * Get all settings of a particular value type
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getAllValuesByType(valueClass: Class<T>): List<Setting<T>> =
        allSettings.filter { it.getValueClass() == valueClass } as List<Setting<T>>
}
