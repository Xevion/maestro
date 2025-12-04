package maestro.api.utils;

import maestro.api.behavior.IBehavior;
import maestro.api.behavior.ILookBehavior;
import maestro.api.utils.input.Input;
import maestro.pathing.movement.LookIntent;
import maestro.pathing.movement.MovementIntent;

public interface IInputOverrideHandler extends IBehavior {

    boolean isInputForcedDown(Input input);

    void setInputForceState(Input input, boolean forced);

    void clearAllKeys();

    void applyMovementIntent(MovementIntent intent, IPlayerContext ctx);

    void applyLookIntent(LookIntent intent, ILookBehavior lookBehavior);

    void applyIntent(
            maestro.pathing.movement.Intent intent, ILookBehavior lookBehavior, IPlayerContext ctx);

    void clearIntentTracking();
}
