package maestro.process;

import maestro.Maestro;
import maestro.api.process.PathingCommand;
import maestro.api.process.PathingCommandType;
import maestro.utils.MaestroProcessHelper;

public class InventoryPauserProcess extends MaestroProcessHelper {

    boolean pauseRequestedLastTick;
    boolean safeToCancelLastTick;
    int ticksOfStationary;

    public InventoryPauserProcess(Maestro maestro) {
        super(maestro);
    }

    @Override
    public boolean isActive() {
        return ctx.player() != null && ctx.world() != null;
    }

    private double motion() {
        return ctx.player().getDeltaMovement().multiply(1, 0, 1).length();
    }

    private boolean stationaryNow() {
        return motion() < 0.00001;
    }

    public boolean stationaryForInventoryMove() {
        pauseRequestedLastTick = true;
        return safeToCancelLastTick && ticksOfStationary > 1;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        // logDebug(pauseRequestedLastTick + " " + safeToCancelLastTick + " " + ticksOfStationary);
        safeToCancelLastTick = isSafeToCancel;
        if (pauseRequestedLastTick) {
            pauseRequestedLastTick = false;
            if (stationaryNow()) {
                ticksOfStationary++;
            }
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        ticksOfStationary = 0;
        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    @Override
    public void onLostControl() {}

    @Override
    public String displayName0() {
        return "inventory pauser";
    }

    @Override
    public double priority() {
        return 5.1; // slightly higher than backfill
    }

    @Override
    public boolean isTemporary() {
        return true;
    }
}
