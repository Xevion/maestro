package maestro.utils;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.*;
import java.util.Collections;
import maestro.Agent;
import maestro.api.MaestroAPI;
import maestro.api.pathing.goals.GoalBlock;
import maestro.api.utils.BetterBlockPos;
import maestro.api.utils.Helper;
import maestro.api.utils.MaestroLogger;
import maestro.utils.chat.ChatMessageBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;

public class GuiClick extends Screen implements Helper {
    private static final Logger log = MaestroLogger.get("event");

    private Matrix4f projectionViewMatrix;

    private BlockPos clickStart;
    private BlockPos currentMouseOver;

    public GuiClick() {
        super(Component.literal("CLICK"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics stack, int mouseX, int mouseY, float partialTicks) {
        double mx = mc.mouseHandler.xpos();
        double my = mc.mouseHandler.ypos();

        my = mc.getWindow().getScreenHeight() - my;
        my *= mc.getWindow().getHeight() / (double) mc.getWindow().getScreenHeight();
        mx *= mc.getWindow().getWidth() / (double) mc.getWindow().getScreenWidth();
        Vec3 near = toWorld(mx, my, 0);
        Vec3 far = toWorld(mx, my, 1); // "Use 0.945 that's what stack overflow says" - leijurv

        if (near != null && far != null) {
            Vec3 viewerPos =
                    new Vec3(PathRenderer.posX(), PathRenderer.posY(), PathRenderer.posZ());
            LocalPlayer player =
                    MaestroAPI.getProvider().getPrimaryAgent().getPlayerContext().player();
            BlockHitResult result =
                    player.level()
                            .clip(
                                    new ClipContext(
                                            near.add(viewerPos),
                                            far.add(viewerPos),
                                            ClipContext.Block.OUTLINE,
                                            ClipContext.Fluid.NONE,
                                            player));
            if (result != null && result.getType() == HitResult.Type.BLOCK) {
                currentMouseOver = result.getBlockPos();
            }
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        if (currentMouseOver
                != null) { // Catch this, or else a click into void will result in a crash
            if (mouseButton == 0) {
                if (clickStart != null && !clickStart.equals(currentMouseOver)) {
                    MaestroAPI.getProvider()
                            .getPrimaryAgent()
                            .getSelectionManager()
                            .removeAllSelections();
                    BetterBlockPos from = BetterBlockPos.from(clickStart);
                    BetterBlockPos to = BetterBlockPos.from(currentMouseOver);
                    MaestroAPI.getProvider()
                            .getPrimaryAgent()
                            .getSelectionManager()
                            .addSelection(from, to);
                    ChatMessageBuilder.info(log, "event")
                            .message("Selection made")
                            .key("from", from)
                            .key("to", to)
                            .withHover("Click to select region")
                            .withClick(
                                    "/maestro sel "
                                            + from.getX()
                                            + " "
                                            + from.getY()
                                            + " "
                                            + from.getZ()
                                            + " "
                                            + to.getX()
                                            + " "
                                            + to.getY()
                                            + " "
                                            + to.getZ())
                            .send();
                    clickStart = null;
                } else {
                    MaestroAPI.getProvider()
                            .getPrimaryAgent()
                            .getCustomGoalProcess()
                            .setGoalAndPath(new GoalBlock(currentMouseOver));
                }
            } else if (mouseButton == 1) {
                MaestroAPI.getProvider()
                        .getPrimaryAgent()
                        .getCustomGoalProcess()
                        .setGoalAndPath(new GoalBlock(currentMouseOver.above()));
            }
        }
        clickStart = null;
        return super.mouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        clickStart = currentMouseOver;
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public void onRender(PoseStack modelViewStack, Matrix4f projectionMatrix) {
        this.projectionViewMatrix = new Matrix4f(projectionMatrix);
        this.projectionViewMatrix.mul(modelViewStack.last().pose());
        this.projectionViewMatrix.invert();

        if (currentMouseOver != null) {
            Entity e = mc.getCameraEntity();
            // drawSingleSelectionBox WHEN?
            PathRenderer.drawManySelectionBoxes(
                    modelViewStack, e, Collections.singletonList(currentMouseOver), Color.CYAN);
            if (clickStart != null && !clickStart.equals(currentMouseOver)) {
                BufferBuilder bufferBuilder =
                        IRenderer.startLines(
                                Color.RED, Agent.settings().pathRenderLineWidthPixels.value, true);
                BetterBlockPos a = new BetterBlockPos(currentMouseOver);
                BetterBlockPos b = new BetterBlockPos(clickStart);
                IRenderer.emitAABB(
                        bufferBuilder,
                        modelViewStack,
                        new AABB(
                                Math.min(a.x, b.x),
                                Math.min(a.y, b.y),
                                Math.min(a.z, b.z),
                                Math.max(a.x, b.x) + 1,
                                Math.max(a.y, b.y) + 1,
                                Math.max(a.z, b.z) + 1));
                IRenderer.endLines(bufferBuilder, true);
            }
        }
    }

    private Vec3 toWorld(double x, double y, double z) {
        if (this.projectionViewMatrix == null) {
            return null;
        }

        x /= mc.getWindow().getWidth();
        y /= mc.getWindow().getHeight();
        x = x * 2 - 1;
        y = y * 2 - 1;

        Vector4f pos = new Vector4f((float) x, (float) y, (float) z, 1.0F);
        projectionViewMatrix.transform(pos);

        if (pos.w() == 0) {
            return null;
        }

        pos.mul(1 / pos.w());
        return new Vec3(pos.x(), pos.y(), pos.z());
    }
}
