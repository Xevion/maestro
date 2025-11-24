package maestro.api.utils;

import maestro.api.behavior.IBehavior;
import maestro.api.utils.input.Input;

public interface IInputOverrideHandler extends IBehavior {

    boolean isInputForcedDown(Input input);

    void setInputForceState(Input input, boolean forced);

    void clearAllKeys();
}
