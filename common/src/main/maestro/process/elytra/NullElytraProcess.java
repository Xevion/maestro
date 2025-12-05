package maestro.process.elytra;

import maestro.Agent;
import maestro.api.pathing.goals.Goal;
import maestro.api.process.IElytraProcess;
import maestro.api.process.PathingCommand;
import maestro.process.MaestroProcessHelper;
import net.minecraft.core.BlockPos;

public final class NullElytraProcess extends MaestroProcessHelper implements IElytraProcess {

    public NullElytraProcess(Agent maestro) {
        super(maestro);
    }

    @Override
    public void repackChunks() {
        throw new UnsupportedOperationException("Called repackChunks() on NullElytraBehavior");
    }

    @Override
    public BlockPos currentDestination() {
        return null;
    }

    @Override
    public void pathTo(BlockPos destination) {
        throw new UnsupportedOperationException("Called pathTo() on NullElytraBehavior");
    }

    @Override
    public void pathTo(Goal destination) {
        throw new UnsupportedOperationException("Called pathTo() on NullElytraBehavior");
    }

    @Override
    public void resetState() {}

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        throw new UnsupportedOperationException("Called onTick on NullElytraProcess");
    }

    @Override
    public void onLostControl() {}

    @Override
    public String displayName0() {
        return "NullElytraProcess";
    }

    @Override
    public boolean isLoaded() {
        return false;
    }

    @Override
    public boolean isSafeToCancel() {
        return true;
    }
}
