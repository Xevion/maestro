package maestro.debug

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import java.awt.Color

/**
 * Represents a block to be rendered with highlighting.
 * Tracks neighbour relationships for occlusion culling.
 *
 * @property pos Block position
 * @property color Render color
 */
data class BlockHighlight(
    val pos: BlockPos,
    val color: Color,
) {
    private var neighbours: Int = 0 // Bitfield: bit N = neighbour on Direction.entries[N]

    /**
     * Update neighbour information for occlusion culling.
     * Only considers blocks with the SAME color as neighbours for occlusion.
     *
     * @param world The world to check block states in
     * @param blocksHighlight Map of all highlight blocks being rendered
     */
    fun updateNeighbours(
        world: Level,
        blocksHighlight: Map<BlockPos, BlockHighlight>,
    ) {
        neighbours = 0
        Direction.entries.forEach { dir ->
            val neighbourPos = pos.relative(dir)
            val neighbourBlock = blocksHighlight[neighbourPos]

            // Only occlude if same color AND shapes touch
            if (neighbourBlock != null && neighbourBlock.color == this.color) {
                if (shapesTouch(world, dir, neighbourPos)) {
                    neighbours = neighbours or (1 shl dir.ordinal)
                }
            }
        }
    }

    /**
     * Check if a face should be rendered (not occluded by neighbour).
     *
     * @param direction The face direction to check
     * @return True if the face should be rendered (not occluded)
     */
    fun shouldRenderFace(direction: Direction): Boolean = (neighbours and (1 shl direction.ordinal)) == 0

    /**
     * Check if VoxelShapes actually touch on the given face.
     * This handles non-full blocks (slabs, stairs, etc.) correctly.
     *
     * @param world The world to get block states from
     * @param dir Direction from this block to neighbour
     * @param neighbourPos Position of the neighbour block
     * @return True if the shapes touch on the shared face
     */
    private fun shapesTouch(
        world: Level,
        dir: Direction,
        neighbourPos: BlockPos,
    ): Boolean {
        val shape = world.getBlockState(pos).getShape(world, pos)
        val neighbourShape = world.getBlockState(neighbourPos).getShape(world, neighbourPos)

        // If either shape is empty, treat as full block
        if (shape.isEmpty || neighbourShape.isEmpty) return true

        // Check if the shapes actually touch on the shared face
        // For example, SOUTH direction: current block's max Z must be 1.0
        // AND neighbour's min Z must be 0.0 for the faces to touch
        return when (dir) {
            Direction.DOWN ->
                shape.min(Direction.Axis.Y) == 0.0 &&
                    neighbourShape.max(Direction.Axis.Y) == 1.0
            Direction.UP ->
                shape.max(Direction.Axis.Y) == 1.0 &&
                    neighbourShape.min(Direction.Axis.Y) == 0.0
            Direction.NORTH ->
                shape.min(Direction.Axis.Z) == 0.0 &&
                    neighbourShape.max(Direction.Axis.Z) == 1.0
            Direction.SOUTH ->
                shape.max(Direction.Axis.Z) == 1.0 &&
                    neighbourShape.min(Direction.Axis.Z) == 0.0
            Direction.WEST ->
                shape.min(Direction.Axis.X) == 0.0 &&
                    neighbourShape.max(Direction.Axis.X) == 1.0
            Direction.EAST ->
                shape.max(Direction.Axis.X) == 1.0 &&
                    neighbourShape.min(Direction.Axis.X) == 0.0
        }
    }
}
