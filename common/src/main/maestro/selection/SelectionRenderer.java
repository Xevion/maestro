package maestro.selection;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import maestro.Agent;
import maestro.api.event.events.RenderEvent;
import maestro.api.event.listener.AbstractGameEventListener;
import maestro.rendering.IRenderer;
import net.minecraft.world.phys.AABB;

public class SelectionRenderer implements IRenderer, AbstractGameEventListener {

    public static final double SELECTION_BOX_EXPANSION = .005D;

    private final SelectionManager manager;

    SelectionRenderer(Agent maestro, SelectionManager manager) {
        this.manager = manager;
        maestro.getGameEventHandler().registerEventListener(this);
    }

    public static void renderSelections(PoseStack stack, Selection[] selections) {
        float opacity = settings.selectionOpacity.value;
        boolean ignoreDepth = settings.renderSelectionIgnoreDepth.value;
        float lineWidth = settings.selectionLineWidth.value;

        if (!settings.renderSelection.value || selections.length == 0) {
            return;
        }

        BufferBuilder bufferBuilder =
                IRenderer.startLines(
                        settings.colorSelection.value, opacity, lineWidth, ignoreDepth);

        for (Selection selection : selections) {
            IRenderer.emitAABB(bufferBuilder, stack, selection.aabb(), SELECTION_BOX_EXPANSION);
        }

        if (settings.renderSelectionCorners.value) {
            IRenderer.glColor(settings.colorSelectionPos1.value, opacity);

            for (Selection selection : selections) {
                IRenderer.emitAABB(bufferBuilder, stack, new AABB(selection.pos1().toBlockPos()));
            }

            IRenderer.glColor(settings.colorSelectionPos2.value, opacity);

            for (Selection selection : selections) {
                IRenderer.emitAABB(bufferBuilder, stack, new AABB(selection.pos2().toBlockPos()));
            }
        }

        IRenderer.endLines(bufferBuilder, ignoreDepth);
    }

    @Override
    public void onRenderPass(RenderEvent event) {
        renderSelections(event.modelViewStack, manager.getSelections());
    }
}
