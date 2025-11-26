package maestro.utils.pathing;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import maestro.api.pathing.calc.IPath;
import maestro.api.utils.BetterBlockPos;
import maestro.api.utils.IPlayerContext;
import maestro.api.utils.MaestroLogger;
import maestro.pathing.movement.CalculationContext;
import org.slf4j.Logger;

public final class Favoring {
    private static final Logger log = MaestroLogger.get("path");

    private final Long2DoubleOpenHashMap favorings;

    public Favoring(IPlayerContext ctx, IPath previous, CalculationContext context) {
        this(previous, context);
        for (Avoidance avoid : Avoidance.create(ctx)) {
            avoid.applySpherical(favorings);
        }
        log.atDebug().addKeyValue("favoring_size", favorings.size()).log("Favoring map created");
    }

    public Favoring(
            IPath previous,
            CalculationContext context) { // create one just from previous path, no mob avoidances
        favorings = new Long2DoubleOpenHashMap();
        favorings.defaultReturnValue(1.0D);
        double coeff = context.backtrackCostFavoringCoefficient;
        if (coeff != 1D && previous != null) {
            previous.positions().forEach(pos -> favorings.put(BetterBlockPos.longHash(pos), coeff));
        }
    }

    public boolean isEmpty() {
        return favorings.isEmpty();
    }

    public double calculate(long hash) {
        return favorings.get(hash);
    }
}
