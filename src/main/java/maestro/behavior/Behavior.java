package maestro.behavior;

import maestro.Agent;
import maestro.api.behavior.IBehavior;
import maestro.api.utils.IPlayerContext;

/**
 * A type of game event listener that is given {@link Agent} instance context.
 *
 * @author Brady
 * @since 8/1/2018
 */
public class Behavior implements IBehavior {

    public final Agent maestro;
    public final IPlayerContext ctx;

    protected Behavior(Agent maestro) {
        this.maestro = maestro;
        this.ctx = maestro.getPlayerContext();
    }
}
