package maestro.api.debug;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Interface for HUD debug rendering.
 *
 * <p>Handles 2D screen-space debug overlays.
 */
public interface IHudDebugRenderer {

    /**
     * Render HUD debug overlays.
     *
     * @param graphics the GUI graphics context for rendering
     * @param tickDelta partial tick time for smooth animations
     */
    void render(GuiGraphics graphics, float tickDelta);
}
