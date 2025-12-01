package maestro.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import maestro.api.MaestroAPI;
import maestro.api.event.events.RenderEvent;
import maestro.api.pathing.goals.*;
import maestro.api.utils.IPlayerContext;
import maestro.api.utils.PackedBlockPos;
import maestro.api.utils.interfaces.IGoalRenderPos;
import maestro.behavior.PathingBehavior;
import maestro.pathing.path.PathExecutor;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class PathRenderer implements IRenderer {

    private static final ResourceLocation TEXTURE_BEACON_BEAM =
            ResourceLocation.parse("textures/entity/beacon_beam.png");

    private PathRenderer() {}

    public static double posX() {
        return renderManager.renderPosX();
    }

    public static double posY() {
        return renderManager.renderPosY();
    }

    public static double posZ() {
        return renderManager.renderPosZ();
    }

    public static void render(RenderEvent event, PathingBehavior behavior) {
        final IPlayerContext ctx = behavior.ctx;
        if (ctx.world() == null) {
            return;
        }
        if (ctx.minecraft().screen instanceof GuiClick) {
            ((GuiClick) ctx.minecraft().screen)
                    .onRender(event.modelViewStack, event.projectionMatrix);
        }

        final float partialTicks = event.partialTicks;
        final Goal goal = behavior.getGoal();

        final DimensionType thisPlayerDimension = ctx.world().dimensionType();
        final DimensionType currentRenderViewDimension =
                MaestroAPI.getProvider()
                        .getPrimaryAgent()
                        .getPlayerContext()
                        .world()
                        .dimensionType();

        if (thisPlayerDimension != currentRenderViewDimension) {
            // this is a path for a bot in a different dimension, don't render it
            return;
        }

        if (goal != null && settings.renderGoal.value) {
            drawGoal(event.modelViewStack, ctx, goal, partialTicks, settings.colorGoalBox.value);
        }

        if (!settings.renderPath.value) {
            return;
        }

        PathExecutor current = behavior.getCurrent(); // this should prevent most race conditions?
        PathExecutor next =
                behavior.getNext(); // like, now it's not possible for current!=null to be true,
        // then suddenly false because of another thread
        if (current != null && settings.renderSelectionBoxes.value) {
            drawManySelectionBoxes(
                    event.modelViewStack,
                    ctx.player(),
                    current.toBreak(),
                    settings.colorBlocksToBreak.value);
            drawManySelectionBoxes(
                    event.modelViewStack,
                    ctx.player(),
                    current.toPlace(),
                    settings.colorBlocksToPlace.value);
            drawManySelectionBoxes(
                    event.modelViewStack,
                    ctx.player(),
                    current.toWalkInto(),
                    settings.colorBlocksToWalkInto.value);
        }

        // drawManySelectionBoxes(player, Collections.singletonList(behavior.pathStart()),
        // partialTicks, Color.WHITE);

        // Render the current path, if there is one
        if (current != null && current.getPath() != null) {
            int renderBegin = Math.max(current.getPosition() - 3, 0);
            try {
                drawPathWithMovements(
                        event.modelViewStack,
                        current.getPath().positions(),
                        current.getPath().movements(),
                        renderBegin,
                        settings.colorCurrentPath.value,
                        settings.fadePath.value,
                        10,
                        20,
                        current.getPosition(),
                        0.4f);
            } catch (Exception e) {
                // Fall back to old rendering if movements access fails
                drawPath(
                        event.modelViewStack,
                        current.getPath().positions(),
                        renderBegin,
                        settings.colorCurrentPath.value,
                        settings.fadePath.value,
                        10,
                        20,
                        0.5D,
                        current.getPosition(),
                        0.4f);
            }
        }

        if (next != null && next.getPath() != null) {
            try {
                drawPathWithMovements(
                        event.modelViewStack,
                        next.getPath().positions(),
                        next.getPath().movements(),
                        0,
                        settings.colorNextPath.value,
                        settings.fadePath.value,
                        10,
                        20,
                        -1,
                        0.4f);
            } catch (Exception e) {
                // Fall back to old rendering if movements access fails
                drawPath(
                        event.modelViewStack,
                        next.getPath().positions(),
                        0,
                        settings.colorNextPath.value,
                        settings.fadePath.value,
                        10,
                        20,
                        0.5D,
                        -1,
                        0.4f);
            }
        }

        // If there is a path calculation currently running, render the path calculation process
        behavior.getInProgress()
                .ifPresent(
                        currentlyRunning -> {
                            currentlyRunning
                                    .bestPathSoFar()
                                    .ifPresent(
                                            p -> {
                                                // Best path so far is not verified, use old
                                                // rendering
                                                drawPath(
                                                        event.modelViewStack,
                                                        p.positions(),
                                                        0,
                                                        settings.colorBestPathSoFar.value,
                                                        settings.fadePath.value,
                                                        10,
                                                        20,
                                                        0.5D,
                                                        -1,
                                                        0.4f);
                                            });

                            currentlyRunning
                                    .pathToMostRecentNodeConsidered()
                                    .ifPresent(
                                            mr -> {
                                                drawPath(
                                                        event.modelViewStack,
                                                        mr.positions(),
                                                        0,
                                                        settings.colorMostRecentConsidered.value,
                                                        settings.fadePath.value,
                                                        10,
                                                        20,
                                                        0.5D,
                                                        -1,
                                                        0.4f);
                                                drawManySelectionBoxes(
                                                        event.modelViewStack,
                                                        ctx.player(),
                                                        Collections.singletonList(
                                                                mr.getDest().toBlockPos()),
                                                        settings.colorMostRecentConsidered.value);
                                            });
                        });
    }

    public static void drawPath(
            PoseStack stack,
            List<PackedBlockPos> positions,
            int startIndex,
            Color color,
            boolean fadeOut,
            int fadeStart0,
            int fadeEnd0) {
        drawPath(
                stack, positions, startIndex, color, fadeOut, fadeStart0, fadeEnd0, 0.5D, -1, 0.4F);
    }

    public static void drawPath(
            PoseStack stack,
            List<PackedBlockPos> positions,
            int startIndex,
            Color color,
            boolean fadeOut,
            int fadeStart0,
            int fadeEnd0,
            double offset,
            int currentPosition,
            float baseAlpha) {
        BufferBuilder bufferBuilder =
                IRenderer.startLines(
                        color,
                        baseAlpha,
                        settings.pathRenderLineWidthPixels.value,
                        settings.renderPathIgnoreDepth.value);

        int fadeStart = fadeStart0 + startIndex;
        int fadeEnd = fadeEnd0 + startIndex;

        for (int i = startIndex, next; i < positions.size() - 1; i = next) {
            PackedBlockPos start = positions.get(i);
            PackedBlockPos end = positions.get(next = i + 1);

            int dirX = end.getX() - start.getX();
            int dirY = end.getY() - start.getY();
            int dirZ = end.getZ() - start.getZ();

            while (next + 1 < positions.size()
                    && (!fadeOut || next + 1 < fadeStart)
                    && (dirX == positions.get(next + 1).getX() - end.getX()
                            && dirY == positions.get(next + 1).getY() - end.getY()
                            && dirZ == positions.get(next + 1).getZ() - end.getZ())) {
                end = positions.get(++next);
            }

            // Determine segment color (highlight current and next segments)
            Color segmentColor = color;
            if (currentPosition >= 0) {
                if (i == currentPosition) {
                    segmentColor = Color.WHITE; // Current segment
                } else if (i == currentPosition + 1) {
                    segmentColor = Color.YELLOW; // Next segment
                }
            }

            if (fadeOut) {
                float alpha;

                if (i <= fadeStart) {
                    alpha = baseAlpha;
                } else {
                    if (i > fadeEnd) {
                        break;
                    }
                    alpha =
                            baseAlpha
                                    * (1.0F
                                            - (float) (i - fadeStart)
                                                    / (float) (fadeEnd - fadeStart));
                }
                IRenderer.glColor(segmentColor, alpha);
            } else {
                IRenderer.glColor(segmentColor, baseAlpha);
            }

            emitPathLine(
                    bufferBuilder,
                    stack,
                    start.getX(),
                    start.getY(),
                    start.getZ(),
                    end.getX(),
                    end.getY(),
                    end.getZ(),
                    offset);
        }

        IRenderer.endLines(bufferBuilder, settings.renderPathIgnoreDepth.value);
    }

    public static void drawPathWithMovements(
            PoseStack stack,
            List<PackedBlockPos> positions,
            List<maestro.api.pathing.movement.IMovement> movements,
            int startIndex,
            Color color,
            boolean fadeOut,
            int fadeStart0,
            int fadeEnd0,
            int currentPosition,
            float baseAlpha) {
        // Validate inputs
        if (positions == null || movements == null) {
            return;
        }

        if (positions.isEmpty() || movements.isEmpty()) {
            return;
        }

        if (movements.size() != positions.size() - 1) {
            drawPath(
                    stack,
                    positions,
                    startIndex,
                    color,
                    fadeOut,
                    fadeStart0,
                    fadeEnd0,
                    0.5D,
                    currentPosition,
                    baseAlpha);
            return;
        }

        if (startIndex >= positions.size() - 1 || startIndex >= movements.size()) {
            return;
        }

        int fadeStart = fadeStart0 + startIndex;
        int fadeEnd = fadeEnd0 + startIndex;

        BufferBuilder bufferBuilder =
                IRenderer.startLines(
                        color,
                        baseAlpha,
                        settings.pathRenderLineWidthPixels.value,
                        settings.renderPathIgnoreDepth.value);

        for (int i = startIndex, next; i < positions.size() - 1; i = next) {
            PackedBlockPos start = positions.get(i);
            PackedBlockPos end = positions.get(next = i + 1);

            maestro.api.pathing.movement.IMovement movement = movements.get(i);
            if (movement == null) {
                // Determine segment color even when movement is null
                Color segmentColor = color;
                if (currentPosition >= 0) {
                    if (i == currentPosition) {
                        segmentColor = Color.WHITE;
                    } else if (i == currentPosition + 1) {
                        segmentColor = Color.YELLOW;
                    }
                }
                IRenderer.glColor(segmentColor, baseAlpha);
                emitPathLine(
                        bufferBuilder,
                        stack,
                        start.getX(),
                        start.getY(),
                        start.getZ(),
                        end.getX(),
                        end.getY(),
                        end.getZ(),
                        0.5);
                continue;
            }

            // Determine line color: segment highlighting takes priority over swimming colors
            Color lineColor;
            if (currentPosition >= 0) {
                if (i == currentPosition) {
                    lineColor = Color.WHITE; // Current segment
                } else if (i == currentPosition + 1) {
                    lineColor = Color.YELLOW; // Next segment
                } else {
                    lineColor =
                            getMovementColor(movement, color); // Swimming colors for other segments
                }
            } else {
                lineColor = getMovementColor(movement, color);
            }

            int dirX = end.getX() - start.getX();
            int dirY = end.getY() - start.getY();
            int dirZ = end.getZ() - start.getZ();

            while (next + 1 < positions.size()
                    && (!fadeOut || next + 1 < fadeStart)
                    && next < movements.size()
                    && !isSwimmingMovement(movements.get(next))
                    && (dirX == positions.get(next + 1).getX() - end.getX()
                            && dirY == positions.get(next + 1).getY() - end.getY()
                            && dirZ == positions.get(next + 1).getZ() - end.getZ())) {
                end = positions.get(++next);
            }

            if (fadeOut) {
                float alpha;

                if (i <= fadeStart) {
                    alpha = baseAlpha;
                } else {
                    if (i > fadeEnd) {
                        break;
                    }
                    alpha =
                            baseAlpha
                                    * (1.0F
                                            - (float) (i - fadeStart)
                                                    / (float) (fadeEnd - fadeStart));
                }
                IRenderer.glColor(lineColor, alpha);
            } else {
                IRenderer.glColor(lineColor, baseAlpha);
            }

            emitPathLine(
                    bufferBuilder,
                    stack,
                    start.getX(),
                    start.getY(),
                    start.getZ(),
                    end.getX(),
                    end.getY(),
                    end.getZ(),
                    0.5);
        }

        IRenderer.endLines(bufferBuilder, settings.renderPathIgnoreDepth.value);

        // Pass 2: Render directional arrows
        BufferBuilder arrowBuffer =
                IRenderer.startLines(
                        Color.CYAN,
                        settings.pathRenderLineWidthPixels.value,
                        settings.renderPathIgnoreDepth.value);

        int lastArrowIndex = -10;

        for (int i = startIndex; i < movements.size(); i++) {
            if (!shouldPlaceArrow(movements, i, lastArrowIndex)) {
                continue;
            }

            maestro.api.pathing.movement.IMovement movement = movements.get(i);
            if (movement == null) {
                continue;
            }
            PackedBlockPos pos = movement.getSrc();

            // Calculate direction vector
            net.minecraft.world.phys.Vec3 direction =
                    new net.minecraft.world.phys.Vec3(
                                    movement.getDest().getX() - pos.getX(),
                                    movement.getDest().getY() - pos.getY(),
                                    movement.getDest().getZ() - pos.getZ())
                            .normalize();

            // Set arrow color
            Color arrowColor = getArrowColor(movement);
            IRenderer.glColor(arrowColor, 0.6f);

            // Emit arrow at movement source position
            emitChevronArrow(
                    arrowBuffer,
                    stack,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    direction,
                    0.4);

            lastArrowIndex = i;
        }

        IRenderer.endLines(arrowBuffer, settings.renderPathIgnoreDepth.value);
    }

    private static void emitPathLine(
            BufferBuilder bufferBuilder,
            PoseStack stack,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            double offset) {
        final double extraOffset = offset + 0.03D;

        double vpX = posX();
        double vpY = posY();
        double vpZ = posZ();
        boolean renderPathAsFrickinThingy = !settings.renderPathAsLine.value;

        IRenderer.emitLine(
                bufferBuilder,
                stack,
                x1 + offset - vpX,
                y1 + offset - vpY,
                z1 + offset - vpZ,
                x2 + offset - vpX,
                y2 + offset - vpY,
                z2 + offset - vpZ);
        if (renderPathAsFrickinThingy) {
            IRenderer.emitLine(
                    bufferBuilder,
                    stack,
                    x2 + offset - vpX,
                    y2 + offset - vpY,
                    z2 + offset - vpZ,
                    x2 + offset - vpX,
                    y2 + extraOffset - vpY,
                    z2 + offset - vpZ);
            IRenderer.emitLine(
                    bufferBuilder,
                    stack,
                    x2 + offset - vpX,
                    y2 + extraOffset - vpY,
                    z2 + offset - vpZ,
                    x1 + offset - vpX,
                    y1 + extraOffset - vpY,
                    z1 + offset - vpZ);
            IRenderer.emitLine(
                    bufferBuilder,
                    stack,
                    x1 + offset - vpX,
                    y1 + extraOffset - vpY,
                    z1 + offset - vpZ,
                    x1 + offset - vpX,
                    y1 + offset - vpY,
                    z1 + offset - vpZ);
        }
    }

    public static void drawManySelectionBoxes(
            PoseStack stack, Entity player, Collection<BlockPos> positions, Color color) {
        BufferBuilder bufferBuilder =
                IRenderer.startLines(
                        color,
                        settings.pathRenderLineWidthPixels.value,
                        settings.renderSelectionBoxesIgnoreDepth.value);

        // BlockPos blockpos = movingObjectPositionIn.getBlockPos();
        BlockStateInterface bsi =
                new BlockStateInterface(
                        MaestroAPI.getProvider()
                                .getPrimaryAgent()
                                .getPlayerContext()); // TODO this assumes same dimension between
        // primary maestro and render view? is this
        // safe?

        positions.forEach(
                pos -> {
                    BlockState state = bsi.get0(pos);
                    VoxelShape shape = state.getShape(player.level(), pos);
                    AABB toDraw = shape.isEmpty() ? Shapes.block().bounds() : shape.bounds();
                    toDraw = toDraw.move(pos);
                    IRenderer.emitAABB(bufferBuilder, stack, toDraw, .002D);
                });

        IRenderer.endLines(bufferBuilder, settings.renderSelectionBoxesIgnoreDepth.value);
    }

    public static void drawGoal(
            PoseStack stack, IPlayerContext ctx, Goal goal, float partialTicks, Color color) {
        drawGoal(null, stack, ctx, goal, partialTicks, color, true);
    }

    private static void drawGoal(
            @Nullable BufferBuilder bufferBuilder,
            PoseStack stack,
            IPlayerContext ctx,
            Goal goal,
            float partialTicks,
            Color color,
            boolean setupRender) {
        if (!setupRender && bufferBuilder == null) {
            throw new RuntimeException("BufferBuilder must not be null if setupRender is false");
        }
        double renderPosX = posX();
        double renderPosY = posY();
        double renderPosZ = posZ();
        double minX, maxX;
        double minZ, maxZ;
        double minY, maxY;
        double y, y1, y2;
        if (!settings.renderGoalAnimated.value) {
            // y = 1 causes rendering issues when the player is at the same y as the top of a block
            // for some reason
            y = 0.999F;
        } else {
            y =
                    Mth.cos(
                            (float)
                                    (((float) ((System.nanoTime() / 100000L) % 20000L))
                                            / 20000F
                                            * Math.PI
                                            * 2));
        }
        if (goal instanceof IGoalRenderPos) {
            BlockPos goalPos = ((IGoalRenderPos) goal).getGoalPos();
            minX = goalPos.getX() + 0.002 - renderPosX;
            maxX = goalPos.getX() + 1 - 0.002 - renderPosX;
            minZ = goalPos.getZ() + 0.002 - renderPosZ;
            maxZ = goalPos.getZ() + 1 - 0.002 - renderPosZ;
            if (goal instanceof GoalGetToBlock || goal instanceof GoalTwoBlocks) {
                y /= 2;
            }
            y1 = 1 + y + goalPos.getY() - renderPosY;
            y2 = 1 - y + goalPos.getY() - renderPosY;
            minY = goalPos.getY() - renderPosY;
            maxY = minY + 2;
            if (goal instanceof GoalGetToBlock || goal instanceof GoalTwoBlocks) {
                y1 -= 0.5;
                y2 -= 0.5;
                maxY--;
            }
            drawDankLitGoalBox(
                    bufferBuilder,
                    stack,
                    color,
                    minX,
                    maxX,
                    minZ,
                    maxZ,
                    minY,
                    maxY,
                    y1,
                    y2,
                    setupRender);
        } else if (goal instanceof GoalXZ goalPos) {
            minY = ctx.world().getMinY();
            maxY = ctx.world().getMaxY();

            if (settings.renderGoalXZBeacon.value) {
                // TODO: check
                textureManager.getTexture(TEXTURE_BEACON_BEAM).bind();
                if (settings.renderGoalIgnoreDepth.value) {
                    RenderSystem.disableDepthTest();
                }

                stack.pushPose(); // push
                stack.translate(
                        goalPos.getX() - renderPosX,
                        -renderPosY,
                        goalPos.getZ() - renderPosZ); // translate

                // TODO: check
                BeaconRenderer.renderBeaconBeam(
                        stack,
                        ctx.minecraft().renderBuffers().bufferSource(),
                        TEXTURE_BEACON_BEAM,
                        settings.renderGoalAnimated.value ? partialTicks : 0,
                        1.0F,
                        settings.renderGoalAnimated.value ? ctx.world().getGameTime() : 0,
                        (int) minY,
                        (int) maxY,
                        color.getRGB(),

                        // Arguments filled by the private method lol
                        0.2F,
                        0.25F);

                stack.popPose(); // pop

                if (settings.renderGoalIgnoreDepth.value) {
                    RenderSystem.enableDepthTest();
                }
                return;
            }

            minX = goalPos.getX() + 0.002 - renderPosX;
            maxX = goalPos.getX() + 1 - 0.002 - renderPosX;
            minZ = goalPos.getZ() + 0.002 - renderPosZ;
            maxZ = goalPos.getZ() + 1 - 0.002 - renderPosZ;

            y1 = 0;
            y2 = 0;
            minY -= renderPosY;
            maxY -= renderPosY;
            drawDankLitGoalBox(
                    bufferBuilder,
                    stack,
                    color,
                    minX,
                    maxX,
                    minZ,
                    maxZ,
                    minY,
                    maxY,
                    y1,
                    y2,
                    setupRender);
        } else if (goal instanceof GoalComposite) {
            // Simple way to determine if goals can be batched, without having some sort of
            // GoalRenderer
            boolean batch =
                    Arrays.stream(((GoalComposite) goal).goals())
                            .allMatch(IGoalRenderPos.class::isInstance);
            BufferBuilder buf = bufferBuilder;
            if (batch) {
                buf =
                        IRenderer.startLines(
                                color,
                                settings.goalRenderLineWidthPixels.value,
                                settings.renderGoalIgnoreDepth.value);
            }
            for (Goal g : ((GoalComposite) goal).goals()) {
                drawGoal(buf, stack, ctx, g, partialTicks, color, !batch);
            }
            if (batch) {
                IRenderer.endLines(buf, settings.renderGoalIgnoreDepth.value);
            }
        } else if (goal instanceof GoalInverted) {
            drawGoal(
                    stack,
                    ctx,
                    ((GoalInverted) goal).origin,
                    partialTicks,
                    settings.colorInvertedGoalBox.value);
        } else if (goal instanceof GoalYLevel goalpos) {
            minX = ctx.player().position().x - settings.yLevelBoxSize.value - renderPosX;
            minZ = ctx.player().position().z - settings.yLevelBoxSize.value - renderPosZ;
            maxX = ctx.player().position().x + settings.yLevelBoxSize.value - renderPosX;
            maxZ = ctx.player().position().z + settings.yLevelBoxSize.value - renderPosZ;
            minY = ((GoalYLevel) goal).level - renderPosY;
            maxY = minY + 2;
            y1 = 1 + y + goalpos.level - renderPosY;
            y2 = 1 - y + goalpos.level - renderPosY;
            drawDankLitGoalBox(
                    bufferBuilder,
                    stack,
                    color,
                    minX,
                    maxX,
                    minZ,
                    maxZ,
                    minY,
                    maxY,
                    y1,
                    y2,
                    setupRender);
        }
    }

    private static void drawDankLitGoalBox(
            BufferBuilder bufferBuilder,
            PoseStack stack,
            Color colorIn,
            double minX,
            double maxX,
            double minZ,
            double maxZ,
            double minY,
            double maxY,
            double y1,
            double y2,
            boolean setupRender) {
        if (setupRender) {
            bufferBuilder =
                    IRenderer.startLines(
                            colorIn,
                            settings.goalRenderLineWidthPixels.value,
                            settings.renderGoalIgnoreDepth.value);
        }

        renderHorizontalQuad(bufferBuilder, stack, minX, maxX, minZ, maxZ, y1);
        renderHorizontalQuad(bufferBuilder, stack, minX, maxX, minZ, maxZ, y2);

        for (double y = minY; y < maxY; y += 16) {
            double max = Math.min(maxY, y + 16);
            IRenderer.emitLine(bufferBuilder, stack, minX, y, minZ, minX, max, minZ, 0.0, 1.0, 0.0);
            IRenderer.emitLine(bufferBuilder, stack, maxX, y, minZ, maxX, max, minZ, 0.0, 1.0, 0.0);
            IRenderer.emitLine(bufferBuilder, stack, maxX, y, maxZ, maxX, max, maxZ, 0.0, 1.0, 0.0);
            IRenderer.emitLine(bufferBuilder, stack, minX, y, maxZ, minX, max, maxZ, 0.0, 1.0, 0.0);
        }

        if (setupRender) {
            IRenderer.endLines(bufferBuilder, settings.renderGoalIgnoreDepth.value);
        }
    }

    private static void renderHorizontalQuad(
            BufferBuilder bufferBuilder,
            PoseStack stack,
            double minX,
            double maxX,
            double minZ,
            double maxZ,
            double y) {
        if (y != 0) {
            IRenderer.emitLine(bufferBuilder, stack, minX, y, minZ, maxX, y, minZ, 1.0, 0.0, 0.0);
            IRenderer.emitLine(bufferBuilder, stack, maxX, y, minZ, maxX, y, maxZ, 0.0, 0.0, 1.0);
            IRenderer.emitLine(bufferBuilder, stack, maxX, y, maxZ, minX, y, maxZ, -1.0, 0.0, 0.0);
            IRenderer.emitLine(bufferBuilder, stack, minX, y, maxZ, minX, y, minZ, 0.0, 0.0, -1.0);
        }
    }

    private static boolean isSwimmingMovement(maestro.api.pathing.movement.IMovement movement) {
        if (movement == null) {
            return false;
        }
        return movement instanceof maestro.pathing.movement.movements.MovementSwimHorizontal
                || movement instanceof maestro.pathing.movement.movements.MovementSwimVertical;
    }

    private static boolean isVerticalSwimming(maestro.api.pathing.movement.IMovement movement) {
        if (movement == null) {
            return false;
        }
        return movement instanceof maestro.pathing.movement.movements.MovementSwimVertical;
    }

    private static boolean isAscending(maestro.api.pathing.movement.IMovement movement) {
        if (movement == null) {
            return false;
        }
        if (movement instanceof maestro.pathing.movement.movements.MovementSwimVertical) {
            return movement.getDest().getY() > movement.getSrc().getY();
        }
        return false;
    }

    private static Color getMovementColor(
            maestro.api.pathing.movement.IMovement movement, Color defaultColor) {
        if (!isSwimmingMovement(movement)) {
            return defaultColor;
        }

        if (defaultColor.equals(Color.RED)) {
            return Color.CYAN;
        } else if (defaultColor.equals(Color.MAGENTA)) {
            return new Color(0, 200, 200);
        } else if (defaultColor.equals(Color.BLUE)) {
            return new Color(0, 150, 150);
        }
        return Color.CYAN;
    }

    private static Color getArrowColor(maestro.api.pathing.movement.IMovement movement) {
        if (isVerticalSwimming(movement)) {
            return isAscending(movement) ? Color.YELLOW : Color.ORANGE;
        }
        return Color.CYAN;
    }

    private static boolean shouldPlaceArrow(
            List<maestro.api.pathing.movement.IMovement> movements,
            int currentIndex,
            int lastArrowIndex) {
        if (currentIndex >= movements.size()) {
            return false;
        }

        maestro.api.pathing.movement.IMovement current = movements.get(currentIndex);
        if (current == null) {
            return false;
        }

        maestro.api.pathing.movement.IMovement previous =
                currentIndex > 0 ? movements.get(currentIndex - 1) : null;

        // Rule 1: Movement type change (walk↔swim, horizontal↔vertical)
        if (previous != null) {
            boolean wasSwimming = isSwimmingMovement(previous);
            boolean isSwimming = isSwimmingMovement(current);
            if (wasSwimming != isSwimming) {
                return true;
            }

            boolean wasVertical = isVerticalSwimming(previous);
            boolean isVertical = isVerticalSwimming(current);
            if (wasVertical != isVertical) {
                return true;
            }
        }

        // Rule 2: Significant direction change (>45°)
        if (previous != null && hasSignificantDirectionChange(previous, current)) {
            return true;
        }

        // Rule 3: Periodic placement (every 5 blocks)
        if (currentIndex - lastArrowIndex >= 5) {
            return true;
        }

        // Rule 4: Minimum spacing (not closer than 3 blocks)
        if (currentIndex - lastArrowIndex < 3) {
            return false;
        }

        return false;
    }

    private static boolean hasSignificantDirectionChange(
            maestro.api.pathing.movement.IMovement previous,
            maestro.api.pathing.movement.IMovement current) {
        // Calculate horizontal direction vectors (ignore Y)
        double prevDx = previous.getDest().getX() - previous.getSrc().getX();
        double prevDz = previous.getDest().getZ() - previous.getSrc().getZ();
        double currDx = current.getDest().getX() - current.getSrc().getX();
        double currDz = current.getDest().getZ() - current.getSrc().getZ();

        // Normalize
        double prevLen = Math.sqrt(prevDx * prevDx + prevDz * prevDz);
        double currLen = Math.sqrt(currDx * currDx + currDz * currDz);
        if (prevLen == 0 || currLen == 0) {
            return false;
        }

        prevDx /= prevLen;
        prevDz /= prevLen;
        currDx /= currLen;
        currDz /= currLen;

        // Dot product: cos(45°) ≈ 0.707
        double dot = prevDx * currDx + prevDz * currDz;
        return dot < 0.707; // Angle > 45°
    }

    private static void emitChevronArrow(
            BufferBuilder bufferBuilder,
            PoseStack stack,
            double x,
            double y,
            double z,
            net.minecraft.world.phys.Vec3 direction,
            double size) {
        // Calculate tip point (along direction)
        double tipX = x + direction.x * size;
        double tipY = y + direction.y * size;
        double tipZ = z + direction.z * size;

        // Calculate perpendicular vector for wings
        net.minecraft.world.phys.Vec3 perp;
        if (Math.abs(direction.y) < 0.9) {
            // Horizontal: use Y-axis cross product
            perp = new net.minecraft.world.phys.Vec3(-direction.z, 0, direction.x).normalize();
        } else {
            // Vertical: use X-axis cross product
            perp = new net.minecraft.world.phys.Vec3(0, -direction.z, direction.y).normalize();
        }

        // Wing points (70% back from tip, 30% spread)
        double backDist = size * 0.7;
        double wingSpread = size * 0.3;
        double wingX = x + direction.x * backDist;
        double wingY = y + direction.y * backDist;
        double wingZ = z + direction.z * backDist;

        double leftX = wingX + perp.x * wingSpread;
        double leftY = wingY + perp.y * wingSpread;
        double leftZ = wingZ + perp.z * wingSpread;

        double rightX = wingX - perp.x * wingSpread;
        double rightY = wingY - perp.y * wingSpread;
        double rightZ = wingZ - perp.z * wingSpread;

        // Adjust for camera position
        double vpX = posX();
        double vpY = posY();
        double vpZ = posZ();

        // Emit 3 lines forming chevron
        IRenderer.emitLine(
                bufferBuilder,
                stack,
                leftX - vpX,
                leftY - vpY,
                leftZ - vpZ,
                tipX - vpX,
                tipY - vpY,
                tipZ - vpZ);

        IRenderer.emitLine(
                bufferBuilder,
                stack,
                rightX - vpX,
                rightY - vpY,
                rightZ - vpZ,
                tipX - vpX,
                tipY - vpY,
                tipZ - vpZ);

        IRenderer.emitLine(
                bufferBuilder,
                stack,
                leftX - vpX,
                leftY - vpY,
                leftZ - vpZ,
                rightX - vpX,
                rightY - vpY,
                rightZ - vpZ);
    }
}
