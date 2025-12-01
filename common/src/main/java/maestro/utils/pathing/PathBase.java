package maestro.utils.pathing;

import maestro.Agent;
import maestro.api.MaestroAPI;
import maestro.api.pathing.calc.IPath;
import maestro.api.pathing.goals.Goal;
import maestro.api.utils.PackedBlockPos;
import maestro.pathing.path.CutoffPath;
import maestro.utils.BlockStateInterface;

public abstract class PathBase implements IPath {

    @Override
    public PathBase cutoffAtLoadedChunks(Object bsi0) { // <-- cursed cursed cursed
        if (!Agent.settings().cutoffAtLoadBoundary.value) {
            return this;
        }
        BlockStateInterface bsi = (BlockStateInterface) bsi0;
        for (int i = 0; i < positions().size(); i++) {
            PackedBlockPos pos = positions().get(i);
            if (!bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
                return new CutoffPath(this, i);
            }
        }
        return this;
    }

    @Override
    public PathBase staticCutoff(Goal destination) {
        int min = MaestroAPI.getSettings().pathCutoffMinimumLength.value;
        if (length() < min) {
            return this;
        }
        if (destination == null || destination.isInGoal(getDest().toBlockPos())) {
            return this;
        }
        double factor = MaestroAPI.getSettings().pathCutoffFactor.value;
        int newLength = (int) ((length() - min) * factor) + min - 1;
        return new CutoffPath(this, newLength);
    }
}
