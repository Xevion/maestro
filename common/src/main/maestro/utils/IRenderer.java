package maestro.utils;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.awt.*;
import maestro.api.MaestroAPI;
import maestro.api.Settings;
import maestro.utils.accessor.IEntityRenderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public interface IRenderer {

    Tesselator tesselator = Tesselator.getInstance();
    IEntityRenderManager renderManager =
            (IEntityRenderManager) Minecraft.getInstance().getEntityRenderDispatcher();
    TextureManager textureManager = Minecraft.getInstance().getTextureManager();
    Settings settings = MaestroAPI.getSettings();

    @SuppressWarnings("MutablePublicArray")
    float[] color = new float[] {1.0F, 1.0F, 1.0F, 255.0F};

    static void glColor(Color color, float alpha) {
        float[] colorComponents = color.getColorComponents(null);
        IRenderer.color[0] = colorComponents[0];
        IRenderer.color[1] = colorComponents[1];
        IRenderer.color[2] = colorComponents[2];
        IRenderer.color[3] = alpha;
    }

    static BufferBuilder startLines(
            Color color, float alpha, float lineWidth, boolean ignoreDepth) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(CoreShaders.RENDERTYPE_LINES);
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        glColor(color, alpha);
        RenderSystem.lineWidth(lineWidth);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        if (ignoreDepth) {
            RenderSystem.disableDepthTest();
        }
        return tesselator.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
    }

    static BufferBuilder startLines(Color color, float lineWidth, boolean ignoreDepth) {
        return startLines(color, .4f, lineWidth, ignoreDepth);
    }

    static void endLines(BufferBuilder bufferBuilder, boolean ignoredDepth) {
        MeshData meshData = bufferBuilder.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        if (ignoredDepth) {
            RenderSystem.enableDepthTest();
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    static void emitLine(
            BufferBuilder bufferBuilder,
            PoseStack stack,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2) {
        Vec3 delta = new Vec3(x2 - x1, y2 - y1, z2 - z1);
        Vec3 normalized = Vec3ExtKt.normalized(delta);

        final float nx = (float) normalized.x;
        final float ny = (float) normalized.y;
        final float nz = (float) normalized.z;

        emitLine(bufferBuilder, stack, x1, y1, z1, x2, y2, z2, nx, ny, nz);
    }

    static void emitLine(
            BufferBuilder bufferBuilder,
            PoseStack stack,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            double nx,
            double ny,
            double nz) {
        emitLine(
                bufferBuilder,
                stack,
                (float) x1,
                (float) y1,
                (float) z1,
                (float) x2,
                (float) y2,
                (float) z2,
                (float) nx,
                (float) ny,
                (float) nz);
    }

    static void emitLine(
            BufferBuilder bufferBuilder,
            PoseStack stack,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float nx,
            float ny,
            float nz) {
        PoseStack.Pose pose = stack.last();

        bufferBuilder
                .addVertex(pose, x1, y1, z1)
                .setColor(color[0], color[1], color[2], color[3])
                .setNormal(pose, nx, ny, nz);
        bufferBuilder
                .addVertex(pose, x2, y2, z2)
                .setColor(color[0], color[1], color[2], color[3])
                .setNormal(pose, nx, ny, nz);
    }

    static void emitAABB(BufferBuilder bufferBuilder, PoseStack stack, AABB aabb) {
        AABB toDraw =
                aabb.move(
                        -renderManager.renderPosX(),
                        -renderManager.renderPosY(),
                        -renderManager.renderPosZ());

        // bottom
        emitLine(
                bufferBuilder,
                stack,
                toDraw.minX,
                toDraw.minY,
                toDraw.minZ,
                toDraw.maxX,
                toDraw.minY,
                toDraw.minZ,
                1.0,
                0.0,
                0.0);
        emitLine(
                bufferBuilder,
                stack,
                toDraw.maxX,
                toDraw.minY,
                toDraw.minZ,
                toDraw.maxX,
                toDraw.minY,
                toDraw.maxZ,
                0.0,
                0.0,
                1.0);
        emitLine(
                bufferBuilder,
                stack,
                toDraw.maxX,
                toDraw.minY,
                toDraw.maxZ,
                toDraw.minX,
                toDraw.minY,
                toDraw.maxZ,
                -1.0,
                0.0,
                0.0);
        emitLine(
                bufferBuilder,
                stack,
                toDraw.minX,
                toDraw.minY,
                toDraw.maxZ,
                toDraw.minX,
                toDraw.minY,
                toDraw.minZ,
                0.0,
                0.0,
                -1.0);
        // top
        emitLine(
                bufferBuilder,
                stack,
                toDraw.minX,
                toDraw.maxY,
                toDraw.minZ,
                toDraw.maxX,
                toDraw.maxY,
                toDraw.minZ,
                1.0,
                0.0,
                0.0);
        emitLine(
                bufferBuilder,
                stack,
                toDraw.maxX,
                toDraw.maxY,
                toDraw.minZ,
                toDraw.maxX,
                toDraw.maxY,
                toDraw.maxZ,
                0.0,
                0.0,
                1.0);
        emitLine(
                bufferBuilder,
                stack,
                toDraw.maxX,
                toDraw.maxY,
                toDraw.maxZ,
                toDraw.minX,
                toDraw.maxY,
                toDraw.maxZ,
                -1.0,
                0.0,
                0.0);
        emitLine(
                bufferBuilder,
                stack,
                toDraw.minX,
                toDraw.maxY,
                toDraw.maxZ,
                toDraw.minX,
                toDraw.maxY,
                toDraw.minZ,
                0.0,
                0.0,
                -1.0);
        // corners
        emitLine(
                bufferBuilder,
                stack,
                toDraw.minX,
                toDraw.minY,
                toDraw.minZ,
                toDraw.minX,
                toDraw.maxY,
                toDraw.minZ,
                0.0,
                1.0,
                0.0);
        emitLine(
                bufferBuilder,
                stack,
                toDraw.maxX,
                toDraw.minY,
                toDraw.minZ,
                toDraw.maxX,
                toDraw.maxY,
                toDraw.minZ,
                0.0,
                1.0,
                0.0);
        emitLine(
                bufferBuilder,
                stack,
                toDraw.maxX,
                toDraw.minY,
                toDraw.maxZ,
                toDraw.maxX,
                toDraw.maxY,
                toDraw.maxZ,
                0.0,
                1.0,
                0.0);
        emitLine(
                bufferBuilder,
                stack,
                toDraw.minX,
                toDraw.minY,
                toDraw.maxZ,
                toDraw.minX,
                toDraw.maxY,
                toDraw.maxZ,
                0.0,
                1.0,
                0.0);
    }

    static void emitAABB(BufferBuilder bufferBuilder, PoseStack stack, AABB aabb, double expand) {
        emitAABB(bufferBuilder, stack, aabb.inflate(expand, expand, expand));
    }

    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack, Vec3 start, Vec3 end) {
        double vpX = renderManager.renderPosX();
        double vpY = renderManager.renderPosY();
        double vpZ = renderManager.renderPosZ();
        emitLine(
                bufferBuilder,
                stack,
                start.x - vpX,
                start.y - vpY,
                start.z - vpZ,
                end.x - vpX,
                end.y - vpY,
                end.z - vpZ);
    }

    /**
     * Start rendering filled quads (transparent faces).
     *
     * @param color Base color for the quads
     * @param alpha Transparency (0.0-1.0)
     * @param ignoreDepth If true, render through blocks
     * @return BufferBuilder ready for quad vertices
     */
    static BufferBuilder startQuads(Color color, float alpha, boolean ignoreDepth) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        glColor(color, alpha);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        if (ignoreDepth) {
            RenderSystem.disableDepthTest();
        }

        return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    /**
     * Finish rendering quads and upload to GPU.
     *
     * @param bufferBuilder The buffer from startQuads()
     * @param ignoredDepth If depth test was disabled
     */
    static void endQuads(BufferBuilder bufferBuilder, boolean ignoredDepth) {
        MeshData meshData = bufferBuilder.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        if (ignoredDepth) {
            RenderSystem.enableDepthTest();
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /**
     * Emit a horizontal quad (for top/bottom faces of blocks). Vertices are emitted
     * counter-clockwise when viewed from above.
     *
     * @param buf Buffer from startQuads()
     * @param stack Transformation stack
     * @param x1 Min X coordinate
     * @param z1 Min Z coordinate
     * @param x2 Max X coordinate
     * @param z2 Max Z coordinate
     * @param y Y coordinate (height)
     */
    static void emitQuadHorizontal(
            BufferBuilder buf,
            PoseStack stack,
            double x1,
            double z1,
            double x2,
            double z2,
            double y) {
        PoseStack.Pose pose = stack.last();

        // Counter-clockwise winding when viewed from above
        buf.addVertex(pose, (float) x1, (float) y, (float) z1)
                .setColor(color[0], color[1], color[2], color[3]);
        buf.addVertex(pose, (float) x1, (float) y, (float) z2)
                .setColor(color[0], color[1], color[2], color[3]);
        buf.addVertex(pose, (float) x2, (float) y, (float) z2)
                .setColor(color[0], color[1], color[2], color[3]);
        buf.addVertex(pose, (float) x2, (float) y, (float) z1)
                .setColor(color[0], color[1], color[2], color[3]);
    }

    /**
     * Emit a vertical quad (for side faces of blocks). Vertices are emitted counter-clockwise when
     * viewed from outside.
     *
     * @param buf Buffer from startQuads()
     * @param stack Transformation stack
     * @param x1 First X coordinate
     * @param z1 First Z coordinate
     * @param x2 Second X coordinate
     * @param z2 Second Z coordinate
     * @param y1 Min Y coordinate (bottom)
     * @param y2 Max Y coordinate (top)
     */
    static void emitQuadVertical(
            BufferBuilder buf,
            PoseStack stack,
            double x1,
            double z1,
            double x2,
            double z2,
            double y1,
            double y2) {
        PoseStack.Pose pose = stack.last();

        // Counter-clockwise winding when viewed from outside
        buf.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(color[0], color[1], color[2], color[3]);
        buf.addVertex(pose, (float) x1, (float) y2, (float) z1)
                .setColor(color[0], color[1], color[2], color[3]);
        buf.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(color[0], color[1], color[2], color[3]);
        buf.addVertex(pose, (float) x2, (float) y1, (float) z2)
                .setColor(color[0], color[1], color[2], color[3]);
    }
}
