package maestro.utils.pathing

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import maestro.api.pathing.calc.IPath
import maestro.api.utils.IPlayerContext
import maestro.api.utils.MaestroLogger
import maestro.pathing.movement.CalculationContext
import org.slf4j.Logger

class Favoring {
    private val log: Logger = MaestroLogger.get("path")
    private val favorings: Long2DoubleOpenHashMap

    constructor(ctx: IPlayerContext, previous: IPath?, context: CalculationContext) : this(previous, context) {
        for (avoid in Avoidance.create(ctx)) {
            avoid.applySpherical(favorings)
        }
        log.atDebug().addKeyValue("favoring_size", favorings.size).log("Favoring map created")
    }

    constructor(previous: IPath?, context: CalculationContext) {
        favorings = Long2DoubleOpenHashMap()
        favorings.defaultReturnValue(1.0)
        val coefficient = context.backtrackCostFavoringCoefficient
        if (coefficient != 1.0 && previous != null) {
            previous.positions().forEach { pos -> favorings.put(pos.packed, coefficient) }
        }
    }

    val isEmpty: Boolean
        get() = favorings.isEmpty()

    fun calculate(hash: Long): Double = favorings.get(hash)
}
