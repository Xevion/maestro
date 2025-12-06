package maestro.launch.mixins;

import maestro.Agent;
import maestro.utils.accessor.IGuiScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class MixinScreen implements IGuiScreen {

    /**
     * Intercept COPY_TO_CLIPBOARD click events that contain Maestro commands. We use
     * COPY_TO_CLIPBOARD instead of RUN_COMMAND for safety - if the mixin fails, it just copies text
     * instead of sending commands to the server.
     */
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    public void interceptMaestroCommands(Style style, CallbackInfoReturnable<Boolean> cir) {
        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null || clickEvent.getAction() != ClickEvent.Action.COPY_TO_CLIPBOARD) {
            return;
        }

        String value = clickEvent.getValue();
        Agent agent = Agent.getPrimaryAgent();

        // Check if this is a Maestro command (starts with configured prefix)
        String prefix = agent.getSettings().prefix.value;
        if (value.startsWith(prefix)) {
            // Extract command without prefix and execute directly
            String command = value.substring(prefix.length());
            agent.executeCommand(command);

            // Prevent default copy-to-clipboard behavior
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
