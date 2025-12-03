package maestro.launch.mixins;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import maestro.Agent;
import maestro.api.IAgent;
import maestro.api.MaestroAPI;
import maestro.api.event.events.RenderEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    /**
     * Disables smart culling when freecam is active to prevent cave/chunk occlusion. Modifies the
     * first parameter (smartCull boolean) of SectionOcclusionGraph.update().
     */
    @ModifyArg(
            method = "setupRender",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;)V"),
            index = 0)
    private boolean maestro$disableSmartCullDuringFreecam(boolean smartCull) {
        Agent agent = (Agent) MaestroAPI.getProvider().getPrimaryAgent();
        if (agent != null && agent.isFreecamActive()) {
            return false;
        }
        return smartCull;
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void onStartHand(
            final GraphicsResourceAllocator graphicsResourceAllocator,
            final DeltaTracker deltaTracker,
            final boolean bl,
            final Camera camera,
            final GameRenderer gameRenderer,
            final Matrix4f matrix4f,
            final Matrix4f matrix4f2,
            final CallbackInfo ci) {
        for (IAgent maestro : MaestroAPI.getProvider().getAllMaestros()) {
            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(matrix4f);
            maestro.getGameEventHandler()
                    .onRenderPass(
                            new RenderEvent(
                                    deltaTracker.getGameTimeDeltaPartialTick(false),
                                    poseStack,
                                    matrix4f2));
        }
    }
}
