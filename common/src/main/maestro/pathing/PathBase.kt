package maestro.pathing

import maestro.Agent
import maestro.api.pathing.calc.IPath
import maestro.api.pathing.goals.Goal
import maestro.pathing.path.CutoffPath

abstract class PathBase : IPath {
    override fun cutoffAtLoadedChunks(bsi: Any): PathBase {
        if (!Agent
                .getPrimaryAgent()
                .settings.cutoffAtLoadBoundary.value
        ) {
            return this
        }
        val blockStateInterface = bsi as BlockStateInterface
        for (i in positions().indices) {
            val pos = positions()[i]
            if (!blockStateInterface.worldContainsLoadedChunk(pos.x, pos.z)) {
                return CutoffPath(this, i)
            }
        }
        return this
    }

    override fun staticCutoff(destination: Goal?): PathBase {
        val min =
            Agent
                .getPrimaryAgent()
                .settings.pathCutoffMinimumLength.value
        if (length() < min) {
            return this
        }
        if (destination == null || destination.isInGoal(dest.toBlockPos())) {
            return this
        }
        val factor =
            Agent
                .getPrimaryAgent()
                .settings.pathCutoffFactor.value
        val newLength = ((length() - min) * factor).toInt() + min - 1
        return CutoffPath(this, newLength)
    }
}
