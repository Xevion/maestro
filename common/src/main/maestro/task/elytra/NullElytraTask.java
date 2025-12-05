package maestro.task.elytra;

import com.google.errorprone.annotations.DoNotCall;
import maestro.Agent;
import maestro.api.pathing.goals.Goal;
import maestro.api.task.PathingCommand;
import maestro.task.TaskHelper;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

public final class NullElytraTask extends TaskHelper {

    public NullElytraTask(Agent maestro) {
        super(maestro);
    }

    @DoNotCall
    public void repackChunks() {
        throw new UnsupportedOperationException("Called repackChunks() on NullElytraBehavior");
    }

    public BlockPos currentDestination() {
        return null;
    }

    @DoNotCall
    public void pathTo(BlockPos destination) {
        throw new UnsupportedOperationException("Called pathTo() on NullElytraBehavior");
    }

    @DoNotCall
    public void pathTo(Goal destination) {
        throw new UnsupportedOperationException("Called pathTo() on NullElytraBehavior");
    }

    public void resetState() {}

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        throw new UnsupportedOperationException("Called onTick on NullElytraTask");
    }

    @Override
    public void onLostControl() {}

    @Override
    public @NotNull String displayName0() {
        return "NullElytraTask";
    }

    public boolean isLoaded() {
        return false;
    }

    public boolean isSafeToCancel() {
        return true;
    }
}
