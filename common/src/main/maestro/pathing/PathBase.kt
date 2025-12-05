package maestro.pathing

import maestro.Agent
import maestro.api.MaestroAPI
import maestro.api.pathing.calc.IPath
import maestro.api.pathing.goals.Goal
import maestro.pathing.path.CutoffPath

abstract class PathBase : IPath {
    override fun cutoffAtLoadedChunks(bsiAny: Any): PathBase {
        if (!Agent.settings().cutoffAtLoadBoundary.value) {
            return this
        }
        val bsi = bsiAny as BlockStateInterface
        for (i in positions().indices) {
            val pos = positions()[i]
            if (!bsi.worldContainsLoadedChunk(pos.x, pos.z)) {
                return CutoffPath(this, i)
            }
        }
        return this
    }

    override fun staticCutoff(destination: Goal?): PathBase {
        val min = MaestroAPI.getSettings().pathCutoffMinimumLength.value
        if (length() < min) {
            return this
        }
        if (destination == null || destination.isInGoal(dest.toBlockPos())) {
            return this
        }
        val factor = MaestroAPI.getSettings().pathCutoffFactor.value
        val newLength = ((length() - min) * factor).toInt() + min - 1
        return CutoffPath(this, newLength)
    }
}
