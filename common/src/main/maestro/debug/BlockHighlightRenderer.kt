package maestro.debug

import com.mojang.blaze3d.vertex.PoseStack
import maestro.rendering.IRenderer
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import java.awt.Color

/**
 * Renders blocks with highlighting.
 * Features automatic occlusion culling for adjacent same-color blocks.
 */
object BlockHighlightRenderer : IRenderer {
    /**
     * Small inset to prevent z-fighting with actual block faces.
     * Shrinks the rendered quads by this amount on all sides.
     */
    private const val Z_FIGHT_OFFSET = 0.002

    /**
     * Render blocks with highlighting and occlusion culling.
     *
     * @param stack PoseStack for transformations
     * @param blocks Map of positions to colors
     * @param sides Which sides to render (before occlusion culling)
     * @param alpha Transparency (0.0-1.0)
     * @param ignoreDepth Render through blocks if true
     */
    fun renderBlocks(
        stack: PoseStack,
        blocks: Map<BlockPos, Color>,
        sides: SideHighlights = SideHighlights.all(),
        alpha: Float = 0.5f,
        ignoreDepth: Boolean = false,
    ) {
        if (blocks.isEmpty()) return

        val world = Minecraft.getInstance().level ?: return

        // Convert to BlockHighlight instances
        val highlightBlocks = blocks.map { (pos, color) -> pos to BlockHighlight(pos, color) }.toMap()

        // Update neighbour information for occlusion culling
        highlightBlocks.values.forEach { it.updateNeighbours(world, highlightBlocks) }

        // Group by color to minimize OpenGL state changes
        val byColor = highlightBlocks.values.groupBy { it.color }

        // Render each color group
        byColor.forEach { (color, colorBlocks) ->
            renderColorGroup(stack, world, colorBlocks, sides, color, alpha, ignoreDepth)
        }
    }

    /**
     * Convenience method for rendering blocks with a single color.
     *
     * @param stack PoseStack for transformations
     * @param positions Collection of block positions
     * @param color Render color for all blocks
     * @param sides Which sides to render (before occlusion culling)
     * @param alpha Transparency (0.0-1.0)
     * @param ignoreDepth Render through blocks if true
     */
    fun renderSingleColor(
        stack: PoseStack,
        positions: Collection<BlockPos>,
        color: Color,
        sides: SideHighlights = SideHighlights.all(),
        alpha: Float = 0.5f,
        ignoreDepth: Boolean = false,
    ) {
        val blocks = positions.associateWith { color }
        renderBlocks(stack, blocks, sides, alpha, ignoreDepth)
    }

    /**
     * Render a group of blocks with the same color.
     * Batches all rendering to minimize state changes.
     */
    private fun renderColorGroup(
        stack: PoseStack,
        world: Level,
        blocks: List<BlockHighlight>,
        sides: SideHighlights,
        color: Color,
        alpha: Float,
        ignoreDepth: Boolean,
    ) {
        val buffer = IRenderer.startQuads(color, alpha, ignoreDepth)

        val vpX = IRenderer.renderManager.renderPosX()
        val vpY = IRenderer.renderManager.renderPosY()
        val vpZ = IRenderer.renderManager.renderPosZ()

        blocks.forEach { block ->
            val shape = world.getBlockState(block.pos).getShape(world, block.pos)

            // Get render bounds with inset to prevent z-fighting
            val x1 =
                block.pos.x - vpX + if (shape.isEmpty) Z_FIGHT_OFFSET else shape.min(Direction.Axis.X) + Z_FIGHT_OFFSET
            val y1 =
                block.pos.y - vpY + if (shape.isEmpty) Z_FIGHT_OFFSET else shape.min(Direction.Axis.Y) + Z_FIGHT_OFFSET
            val z1 =
                block.pos.z - vpZ + if (shape.isEmpty) Z_FIGHT_OFFSET else shape.min(Direction.Axis.Z) + Z_FIGHT_OFFSET
            val x2 =
                block.pos.x - vpX + if (shape.isEmpty) 1.0 - Z_FIGHT_OFFSET else shape.max(Direction.Axis.X) - Z_FIGHT_OFFSET
            val y2 =
                block.pos.y - vpY + if (shape.isEmpty) 1.0 - Z_FIGHT_OFFSET else shape.max(Direction.Axis.Y) - Z_FIGHT_OFFSET
            val z2 =
                block.pos.z - vpZ + if (shape.isEmpty) 1.0 - Z_FIGHT_OFFSET else shape.max(Direction.Axis.Z) - Z_FIGHT_OFFSET

            // Render each face if: (1) requested by sides AND (2) not occluded by neighbour
            if (sides.shouldRender(Direction.DOWN) && block.shouldRenderFace(Direction.DOWN)) {
                IRenderer.emitQuadHorizontal(buffer, stack, x1, z1, x2, z2, y1)
            }
            if (sides.shouldRender(Direction.UP) && block.shouldRenderFace(Direction.UP)) {
                IRenderer.emitQuadHorizontal(buffer, stack, x1, z1, x2, z2, y2)
            }
            if (sides.shouldRender(Direction.NORTH) && block.shouldRenderFace(Direction.NORTH)) {
                IRenderer.emitQuadVertical(buffer, stack, x1, z1, x2, z1, y1, y2)
            }
            if (sides.shouldRender(Direction.SOUTH) && block.shouldRenderFace(Direction.SOUTH)) {
                IRenderer.emitQuadVertical(buffer, stack, x1, z2, x2, z2, y1, y2)
            }
            if (sides.shouldRender(Direction.WEST) && block.shouldRenderFace(Direction.WEST)) {
                IRenderer.emitQuadVertical(buffer, stack, x1, z1, x1, z2, y1, y2)
            }
            if (sides.shouldRender(Direction.EAST) && block.shouldRenderFace(Direction.EAST)) {
                IRenderer.emitQuadVertical(buffer, stack, x2, z1, x2, z2, y1, y2)
            }
        }

        IRenderer.endQuads(buffer, ignoreDepth)
    }
}
