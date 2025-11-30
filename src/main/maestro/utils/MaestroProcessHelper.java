package maestro.utils;

import maestro.Agent;
import maestro.api.process.IMaestroProcess;
import maestro.api.utils.Helper;
import maestro.api.utils.IPlayerContext;

public abstract class MaestroProcessHelper implements IMaestroProcess, Helper {

    protected final Agent maestro;
    protected final IPlayerContext ctx;

    public MaestroProcessHelper(Agent maestro) {
        this.maestro = maestro;
        this.ctx = maestro.getPlayerContext();
    }

    @Override
    public boolean isTemporary() {
        return false;
    }
}
