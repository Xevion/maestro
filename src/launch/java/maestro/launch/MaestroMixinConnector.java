package maestro.launch;

import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

public class MaestroMixinConnector implements IMixinConnector {

    @Override
    public void connect() {
        Mixins.addConfiguration("mixins.maestro.json");
    }
}
