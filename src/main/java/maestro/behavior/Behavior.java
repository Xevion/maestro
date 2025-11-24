package maestro.behavior;

import maestro.Maestro;
import maestro.api.behavior.IBehavior;
import maestro.api.utils.IPlayerContext;

/**
 * A type of game event listener that is given {@link Maestro} instance context.
 *
 * @author Brady
 * @since 8/1/2018
 */
public class Behavior implements IBehavior {

    public final Maestro maestro;
    public final IPlayerContext ctx;

    protected Behavior(Maestro maestro) {
        this.maestro = maestro;
        this.ctx = maestro.getPlayerContext();
    }
}
