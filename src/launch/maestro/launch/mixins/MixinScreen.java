package maestro.launch.mixins;

import static maestro.api.command.IMaestroChatControl.FORCE_COMMAND_PREFIX;

import maestro.api.IAgent;
import maestro.api.MaestroAPI;
import maestro.api.event.events.ChatEvent;
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

    // TODO: switch to enum extention with mixin 9.0 or whenever Mumfrey gets around to it
    @Inject(
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;)V",
                            remap = false,
                            ordinal = 1),
            method = "handleComponentClicked",
            cancellable = true)
    public void handleCustomClickEvent(Style style, CallbackInfoReturnable<Boolean> cir) {
        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null) {
            return;
        }
        String command = clickEvent.getValue();
        if (command == null || !command.startsWith(FORCE_COMMAND_PREFIX)) {
            return;
        }
        IAgent maestro = MaestroAPI.getProvider().getPrimaryAgent();
        if (maestro != null) {
            maestro.getGameEventHandler().onSendChatMessage(new ChatEvent(command));
        }
        cir.setReturnValue(true);
        cir.cancel();
    }
}
