package maestro.rendering.gfx

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.CoreShaders
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.EnumSet

/**
 * SDF-based voxel/block renderer with support for both simple single-block
 * rendering and efficient batched rendering with occlusion culling.
 *
 * Simple API (SDF anti-aliased):
 * ```kotlin
 * GfxRenderer.begin(poseStack)
 * GfxVoxel.block(x, y, z, color = 0xFFFF0000.toInt())
 * GfxVoxel.wireframe(aabb, color = 0xFF00FF00.toInt())
 * GfxRenderer.end()
 * ```
 *
 * Batch API (optimized for many blocks):
 * ```kotlin
 * GfxRenderer.begin(poseStack)
 * GfxVoxel.batch(
 *     positions = pathBlocks,
 *     color = 0x80FF0000.toInt(),
 *     faces = EnumSet.of(Direction.UP),  // top faces only
 *     occlusionCull = true,
 * )
 * GfxRenderer.end()
 * ```
 */
object GfxVoxel {
    private val tesselator = Tesselator.getInstance()

    /**
     * Small inset to prevent z-fighting with actual block faces.
     */
    private const val Z_FIGHT_OFFSET = 0.002

    // ═══════════════════════════════════════════════════════════════════════
    // Simple Single-Block API (SDF anti-aliased)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Draw a wireframe box using SDF anti-aliased lines.
     *
     * @param aabb The axis-aligned bounding box in world coordinates
     * @param color ARGB color
     * @param thickness Line thickness in world units
     * @param joins Join style for corners
     */
    fun wireframe(
        aabb: AABB,
        color: Int,
        thickness: Float = 0.02f,
        joins: PolylineJoins = PolylineJoins.MITER,
    ) {
        GfxCube.wireframe(aabb, color, thickness, joins)
    }

    /**
     * Draw a filled box with SDF anti-aliased edges.
     *
     * @param aabb The axis-aligned bounding box in world coordinates
     * @param color ARGB color
     */
    fun filled(
        aabb: AABB,
        color: Int,
    ) {
        GfxCube.filled(aabb, color)
    }

    /**
     * Draw a unit-cube block highlight.
     *
     * @param blockX Block X coordinate
     * @param blockY Block Y coordinate
     * @param blockZ Block Z coordinate
     * @param color ARGB color
     * @param thickness Line thickness for wireframe mode
     * @param mode Render mode (WIREFRAME, FILLED, or BOTH)
     * @param expand Expansion amount (negative shrinks, positive grows)
     * @param joins Join style for wireframe corners
     */
    fun block(
        blockX: Int,
        blockY: Int,
        blockZ: Int,
        color: Int,
        thickness: Float = 0.02f,
        mode: GfxCube.BoxMode = GfxCube.BoxMode.FILLED,
        expand: Double = 0.0,
        joins: PolylineJoins = PolylineJoins.MITER,
    ) {
        GfxCube.block(blockX, blockY, blockZ, color, thickness, mode, expand, joins)
    }

    /**
     * Draw a block highlight at the given position.
     */
    fun block(
        pos: BlockPos,
        color: Int,
        thickness: Float = 0.02f,
        mode: GfxCube.BoxMode = GfxCube.BoxMode.FILLED,
        expand: Double = 0.0,
        joins: PolylineJoins = PolylineJoins.MITER,
    ) {
        block(pos.x, pos.y, pos.z, color, thickness, mode, expand, joins)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Batch Rendering API (optimized for many blocks)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Render multiple blocks with automatic occlusion culling and color batching.
     *
     * Blocks with the same color that share a face will have that face culled
     * for better performance and visual clarity.
     *
     * @param blocks Map of block positions to ARGB colors
     * @param faces Which faces to render (before occlusion culling)
     * @param respectShape Query world for actual VoxelShape bounds (slabs, stairs, etc.)
     * @param occlusionCull Skip faces shared between same-color adjacent blocks
     */
    fun batch(
        blocks: Map<BlockPos, Int>,
        faces: EnumSet<Direction> = EnumSet.allOf(Direction::class.java),
        respectShape: Boolean = false,
        occlusionCull: Boolean = true,
    ) {
        if (blocks.isEmpty()) return
        check(GfxRenderer.active) { "Must call GfxRenderer.begin() before batch()" }

        val world = if (respectShape) Minecraft.getInstance().level else null

        // Build neighbor info for occlusion culling
        val blockInfo =
            if (occlusionCull) {
                blocks.mapValues { (pos, color) ->
                    BlockInfo(pos, color, computeNeighborMask(pos, color, blocks, world))
                }
            } else {
                blocks.mapValues { (pos, color) -> BlockInfo(pos, color, 0) }
            }

        // Group by color to minimize state changes
        val byColor = blockInfo.values.groupBy { it.color }

        // Render each color group
        byColor.forEach { (color, colorBlocks) ->
            renderColorGroup(colorBlocks, color, faces, world)
        }
    }

    /**
     * Render multiple blocks with a single color.
     *
     * @param positions Collection of block positions
     * @param color ARGB color for all blocks
     * @param faces Which faces to render (before occlusion culling)
     * @param respectShape Query world for actual VoxelShape bounds
     * @param occlusionCull Skip faces shared between adjacent blocks
     */
    fun batch(
        positions: Collection<BlockPos>,
        color: Int,
        faces: EnumSet<Direction> = EnumSet.allOf(Direction::class.java),
        respectShape: Boolean = false,
        occlusionCull: Boolean = true,
    ) {
        batch(positions.associateWith { color }, faces, respectShape, occlusionCull)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal Implementation
    // ═══════════════════════════════════════════════════════════════════════

    private data class BlockInfo(
        val pos: BlockPos,
        val color: Int,
        val neighborMask: Int, // Bitfield: bit N set = neighbor on Direction.entries[N]
    ) {
        fun shouldRenderFace(direction: Direction): Boolean = (neighborMask and (1 shl direction.ordinal)) == 0
    }

    /**
     * Compute bitmask of neighbors with same color that share touching faces.
     */
    private fun computeNeighborMask(
        pos: BlockPos,
        color: Int,
        blocks: Map<BlockPos, Int>,
        world: Level?,
    ): Int {
        var mask = 0
        for (dir in Direction.entries) {
            val neighborPos = pos.relative(dir)
            val neighborColor = blocks[neighborPos]

            if (neighborColor == color) {
                if (world == null || shapesTouch(world, pos, dir, neighborPos)) {
                    mask = mask or (1 shl dir.ordinal)
                }
            }
        }
        return mask
    }

    /**
     * Check if VoxelShapes actually touch on the given face.
     * Handles non-full blocks (slabs, stairs, etc.) correctly.
     */
    private fun shapesTouch(
        world: Level,
        pos: BlockPos,
        dir: Direction,
        neighborPos: BlockPos,
    ): Boolean {
        val shape = world.getBlockState(pos).getShape(world, pos)
        val neighborShape = world.getBlockState(neighborPos).getShape(world, neighborPos)

        // If either shape is empty, treat as full block
        if (shape.isEmpty || neighborShape.isEmpty) return true

        return when (dir) {
            Direction.DOWN ->
                shape.min(Direction.Axis.Y) == 0.0 &&
                    neighborShape.max(Direction.Axis.Y) == 1.0
            Direction.UP ->
                shape.max(Direction.Axis.Y) == 1.0 &&
                    neighborShape.min(Direction.Axis.Y) == 0.0
            Direction.NORTH ->
                shape.min(Direction.Axis.Z) == 0.0 &&
                    neighborShape.max(Direction.Axis.Z) == 1.0
            Direction.SOUTH ->
                shape.max(Direction.Axis.Z) == 1.0 &&
                    neighborShape.min(Direction.Axis.Z) == 0.0
            Direction.WEST ->
                shape.min(Direction.Axis.X) == 0.0 &&
                    neighborShape.max(Direction.Axis.X) == 1.0
            Direction.EAST ->
                shape.max(Direction.Axis.X) == 1.0 &&
                    neighborShape.min(Direction.Axis.X) == 0.0
        }
    }

    /**
     * Render a group of blocks with the same color.
     */
    private fun renderColorGroup(
        blocks: List<BlockInfo>,
        color: Int,
        faces: EnumSet<Direction>,
        world: Level?,
    ) {
        val pose = GfxRenderer.pose

        val a = GfxRenderer.alpha(color)
        val r = GfxRenderer.red(color)
        val g = GfxRenderer.green(color)
        val b = GfxRenderer.blue(color)

        RenderSystem.setShader(CoreShaders.POSITION_COLOR)
        val buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        val matrix = pose.last()

        for (info in blocks) {
            val shape: VoxelShape? = world?.getBlockState(info.pos)?.getShape(world, info.pos)

            // Get render bounds with z-fight offset
            val x1: Float
            val y1: Float
            val z1: Float
            val x2: Float
            val y2: Float
            val z2: Float

            if (shape == null || shape.isEmpty) {
                // Full cube with z-fight offset
                val min =
                    GfxRenderer.toCameraSpace(
                        info.pos.x + Z_FIGHT_OFFSET,
                        info.pos.y + Z_FIGHT_OFFSET,
                        info.pos.z + Z_FIGHT_OFFSET,
                    )
                val max =
                    GfxRenderer.toCameraSpace(
                        info.pos.x + 1.0 - Z_FIGHT_OFFSET,
                        info.pos.y + 1.0 - Z_FIGHT_OFFSET,
                        info.pos.z + 1.0 - Z_FIGHT_OFFSET,
                    )
                x1 = min.x.toFloat()
                y1 = min.y.toFloat()
                z1 = min.z.toFloat()
                x2 = max.x.toFloat()
                y2 = max.y.toFloat()
                z2 = max.z.toFloat()
            } else {
                // Use VoxelShape bounds with z-fight offset
                val min =
                    GfxRenderer.toCameraSpace(
                        info.pos.x + shape.min(Direction.Axis.X) + Z_FIGHT_OFFSET,
                        info.pos.y + shape.min(Direction.Axis.Y) + Z_FIGHT_OFFSET,
                        info.pos.z + shape.min(Direction.Axis.Z) + Z_FIGHT_OFFSET,
                    )
                val max =
                    GfxRenderer.toCameraSpace(
                        info.pos.x + shape.max(Direction.Axis.X) - Z_FIGHT_OFFSET,
                        info.pos.y + shape.max(Direction.Axis.Y) - Z_FIGHT_OFFSET,
                        info.pos.z + shape.max(Direction.Axis.Z) - Z_FIGHT_OFFSET,
                    )
                x1 = min.x.toFloat()
                y1 = min.y.toFloat()
                z1 = min.z.toFloat()
                x2 = max.x.toFloat()
                y2 = max.y.toFloat()
                z2 = max.z.toFloat()
            }

            // Render each face if: (1) requested AND (2) not occluded
            if (faces.contains(Direction.DOWN) && info.shouldRenderFace(Direction.DOWN)) {
                // Bottom face (Y-)
                buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a)
                buffer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a)
                buffer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a)
                buffer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a)
            }
            if (faces.contains(Direction.UP) && info.shouldRenderFace(Direction.UP)) {
                // Top face (Y+)
                buffer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a)
                buffer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a)
                buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a)
                buffer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a)
            }
            if (faces.contains(Direction.NORTH) && info.shouldRenderFace(Direction.NORTH)) {
                // North face (Z-)
                buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a)
                buffer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a)
                buffer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a)
                buffer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a)
            }
            if (faces.contains(Direction.SOUTH) && info.shouldRenderFace(Direction.SOUTH)) {
                // South face (Z+)
                buffer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a)
                buffer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a)
                buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a)
                buffer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a)
            }
            if (faces.contains(Direction.WEST) && info.shouldRenderFace(Direction.WEST)) {
                // West face (X-)
                buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a)
                buffer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a)
                buffer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a)
                buffer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a)
            }
            if (faces.contains(Direction.EAST) && info.shouldRenderFace(Direction.EAST)) {
                // East face (X+)
                buffer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a)
                buffer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a)
                buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a)
                buffer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a)
            }
        }

        val mesh = buffer.build()
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh)
        }
    }
}
