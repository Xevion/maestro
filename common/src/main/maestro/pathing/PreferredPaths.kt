package maestro.pathing

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import maestro.api.pathing.calc.IPath
import maestro.api.player.PlayerContext
import maestro.api.utils.Loggers
import maestro.pathing.movement.CalculationContext
import org.slf4j.Logger

class PreferredPaths {
    private val log: Logger = Loggers.get("path")
    private val areas: Long2DoubleOpenHashMap = Long2DoubleOpenHashMap()

    constructor(ctx: PlayerContext, previous: IPath?, context: CalculationContext) : this(previous, context) {
        for (avoid in Avoidance.create(ctx)) {
            avoid.applySpherical(areas)
        }
        log.atDebug().addKeyValue("favoring_size", areas.size).log("Favoring map created")
    }

    constructor(previous: IPath?, context: CalculationContext) {
        areas.defaultReturnValue(1.0)
        val coefficient = context.backtrackCostFavoringCoefficient
        if (coefficient != 1.0 && previous != null) {
            previous.positions().forEach { pos -> areas.put(pos.packed, coefficient) }
        }
    }

    val isEmpty: Boolean
        get() = areas.isEmpty()

    fun calculate(hash: Long): Double = areas.get(hash)
}
