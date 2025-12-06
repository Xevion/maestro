package maestro.pathing.movement

import kotlin.math.pow

object ActionCosts {
    /** These costs are measured roughly in ticks btw */
    const val WALK_ONE_BLOCK_COST: Double = 20 / 4.317 // 4.633

    const val WALK_ONE_IN_WATER_COST: Double = 20 / 2.2 // 9.091
    const val WALK_ONE_OVER_SOUL_SAND_COST: Double =
        WALK_ONE_BLOCK_COST * 2 // 0.4 in BlockSoulSand but effectively about half
    const val LADDER_UP_ONE_COST: Double = 20 / 2.35 // 8.511
    const val LADDER_DOWN_ONE_COST: Double = 20 / 3.0 // 6.667
    const val SNEAK_ONE_BLOCK_COST: Double = 20 / 1.3 // 15.385
    const val SPRINT_ONE_BLOCK_COST: Double = 20 / 5.612 // 3.564
    const val SPRINT_MULTIPLIER: Double = SPRINT_ONE_BLOCK_COST / WALK_ONE_BLOCK_COST // 0.769

    /** Swimming costs (3D underwater movement) */
    const val SWIM_ONE_BLOCK_COST: Double = 3.5 // Base swimming speed (faster than surface walking)

    const val SWIM_VERTICAL_ONE_BLOCK_COST: Double = 3.5 // Same as horizontal (true 3D movement)
    const val SWIM_FLOWING_WATER_MULTIPLIER: Double = 1.3 // 30% slower against current
    const val SWIM_THROUGH_PLANTS_MULTIPLIER: Double = 1.2 // 20% slower through kelp/seagrass
    const val UNDERWATER_MINING_MULTIPLIER: Double = 5.0 // Vanilla mining speed underwater

    /** To walk off an edge you need to walk 0.5 to the edge then 0.3 to start falling off */
    const val WALK_OFF_BLOCK_COST: Double = WALK_ONE_BLOCK_COST * 0.8 // 3.706

    /** To walk the rest of the way to be centered on the new block */
    const val CENTER_AFTER_FALL_COST: Double = WALK_ONE_BLOCK_COST - WALK_OFF_BLOCK_COST // 0.927

    /**
     * don't make this Double.MAX_VALUE because it's added to other things, maybe other COST_INFs,
     * and that would make it overflow to negative
     */
    const val COST_INF: Double = 1000000.0

    @JvmField val FALL_N_BLOCKS_COST: DoubleArray = generateFallNBlocksCost()

    @JvmField val FALL_1_25_BLOCKS_COST: Double = distanceToTicks(1.25)

    @JvmField val FALL_0_25_BLOCKS_COST: Double = distanceToTicks(0.25)

    /**
     * When you hit space, you get enough upward velocity to go 1.25 blocks Then, you fall the
     * remaining 0.25 to get on the surface, on block higher. Since parabolas are symmetric, the
     * amount of time it takes to ascend up from 1 to 1.25 will be the same amount of time that it
     * takes to fall back down from 1.25 to 1. And the same applies to the overall shape, if it
     * takes X ticks to fall back down 1.25 blocks, it will take X ticks to reach the peak of your
     * 1.25 block leap. Therefore, the part of your jump from y=0 to y=1.25 takes
     * distanceToTicks(1.25) ticks, and the sub-part from y=1 to y=1.25 takes distanceToTicks(0.25)
     * ticks. Therefore, the other sub-part, from y=0 to y-1, takes
     * distanceToTicks(1.25)-distanceToTicks(0.25) ticks. That's why JUMP_ONE_BLOCK_COST =
     * FALL_1_25_BLOCKS_COST - FALL_0_25_BLOCKS_COST
     */
    @JvmField val JUMP_ONE_BLOCK_COST: Double = FALL_1_25_BLOCKS_COST - FALL_0_25_BLOCKS_COST

    private fun generateFallNBlocksCost(): DoubleArray = DoubleArray(4097) { i -> distanceToTicks(i.toDouble()) }

    @JvmStatic
    fun velocity(ticks: Int): Double = (0.98.pow(ticks) - 1) * -3.92

    @JvmStatic
    fun oldFormula(ticks: Double): Double = -3.92 * (99 - 49.5 * (0.98.pow(ticks) + 1) - ticks)

    @JvmStatic
    fun distanceToTicks(distance: Double): Double {
        if (distance == 0.0) {
            return 0.0 // Avoid 0/0 NaN
        }
        var tmpDistance = distance
        var tickCount = 0
        while (true) {
            val fallDistance = velocity(tickCount)
            if (tmpDistance <= fallDistance) {
                return tickCount + tmpDistance / fallDistance
            }
            tmpDistance -= fallDistance
            tickCount++
        }
    }
}
