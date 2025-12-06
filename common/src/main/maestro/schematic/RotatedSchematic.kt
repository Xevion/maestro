package maestro.schematic

import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState

class RotatedSchematic(
    schematic: ISchematic,
    private val rotation: Rotation,
) : TransformedSchematic(schematic) {
    // I don't think a 14 line switch would improve readability
    private val inverseRotation: Rotation = rotation.getRotated(rotation).getRotated(rotation)

    override fun inSchematic(
        x: Int,
        y: Int,
        z: Int,
        currentState: BlockState?,
    ): Boolean =
        schematic.inSchematic(
            rotateX(x, z, widthX(), lengthZ(), inverseRotation),
            y,
            rotateZ(x, z, widthX(), lengthZ(), inverseRotation),
            rotate(currentState, inverseRotation),
        )

    override fun desiredState(
        x: Int,
        y: Int,
        z: Int,
        current: BlockState?,
        approxPlaceable: List<BlockState>?,
    ): BlockState =
        rotate(
            schematic.desiredState(
                rotateX(x, z, widthX(), lengthZ(), inverseRotation),
                y,
                rotateZ(x, z, widthX(), lengthZ(), inverseRotation),
                rotate(current, inverseRotation),
                rotate(approxPlaceable, inverseRotation),
            ),
            rotation,
        )!!

    override fun widthX(): Int = if (flipsCoordinates(rotation)) schematic.lengthZ() else schematic.widthX()

    override fun lengthZ(): Int = if (flipsCoordinates(rotation)) schematic.widthX() else schematic.lengthZ()

    companion object {
        /** Whether [rotation] swaps the x and z components */
        private fun flipsCoordinates(rotation: Rotation): Boolean =
            rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90

        /** The x component of x,z after applying the rotation */
        private fun rotateX(
            x: Int,
            z: Int,
            sizeX: Int,
            sizeZ: Int,
            rotation: Rotation,
        ): Int =
            when (rotation) {
                Rotation.NONE -> x
                Rotation.CLOCKWISE_90 -> sizeZ - z - 1
                Rotation.CLOCKWISE_180 -> sizeX - x - 1
                Rotation.COUNTERCLOCKWISE_90 -> z
            }

        /** The z component of x,z after applying the rotation */
        private fun rotateZ(
            x: Int,
            z: Int,
            sizeX: Int,
            sizeZ: Int,
            rotation: Rotation,
        ): Int =
            when (rotation) {
                Rotation.NONE -> z
                Rotation.CLOCKWISE_90 -> x
                Rotation.CLOCKWISE_180 -> sizeZ - z - 1
                Rotation.COUNTERCLOCKWISE_90 -> sizeX - x - 1
            }

        private fun rotate(
            state: BlockState?,
            rotation: Rotation,
        ): BlockState? = state?.rotate(rotation)

        private fun rotate(
            states: List<BlockState>?,
            rotation: Rotation,
        ): List<BlockState>? = states?.map { rotate(it, rotation)!! }
    }
}
