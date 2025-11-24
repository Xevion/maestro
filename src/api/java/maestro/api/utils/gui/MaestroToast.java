package maestro.api.utils.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public class MaestroToast {
    private static final SystemToast.SystemToastId MAESTRO_TOAST_ID =
            new SystemToast.SystemToastId(5000L);

    public static void addOrUpdate(Component title, Component subtitle) {
        SystemToast.addOrUpdate(
                Minecraft.getInstance().getToastManager(), MAESTRO_TOAST_ID, title, subtitle);
    }
}
