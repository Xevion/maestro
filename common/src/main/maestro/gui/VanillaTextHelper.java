package maestro.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Helper class for calling vanilla text rendering methods.
 *
 * <p>This is needed because Kotlin extension functions shadow the Java methods, so we need a way to
 * call the original methods as a fallback.
 */
public final class VanillaTextHelper {
    private VanillaTextHelper() {}

    /** Calls the vanilla GuiGraphics.drawString method directly. */
    public static int drawString(
            GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        return graphics.drawString(font, text, x, y, color, shadow);
    }

    /** Calls the vanilla GuiGraphics.drawString method directly with a Component. */
    public static int drawString(
            GuiGraphics graphics,
            Font font,
            Component text,
            int x,
            int y,
            int color,
            boolean shadow) {
        return graphics.drawString(font, text, x, y, color, shadow);
    }
}
