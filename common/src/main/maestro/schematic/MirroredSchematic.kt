package maestro.schematic

import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.state.BlockState

class MirroredSchematic(
    schematic: ISchematic,
    private val mirror: Mirror,
) : TransformedSchematic(schematic) {
    override fun inSchematic(
        x: Int,
        y: Int,
        z: Int,
        currentState: BlockState?,
    ): Boolean =
        schematic.inSchematic(
            mirrorX(x, widthX(), mirror),
            y,
            mirrorZ(z, lengthZ(), mirror),
            mirror(currentState, mirror),
        )

    override fun desiredState(
        x: Int,
        y: Int,
        z: Int,
        current: BlockState?,
        approxPlaceable: List<BlockState>?,
    ): BlockState =
        mirror(
            schematic.desiredState(
                mirrorX(x, widthX(), mirror),
                y,
                mirrorZ(z, lengthZ(), mirror),
                mirror(current, mirror),
                mirror(approxPlaceable, mirror),
            ),
            mirror,
        )!!

    companion object {
        private fun mirrorX(
            x: Int,
            sizeX: Int,
            mirror: Mirror,
        ): Int =
            when (mirror) {
                Mirror.NONE, Mirror.LEFT_RIGHT -> x
                Mirror.FRONT_BACK -> sizeX - x - 1
            }

        private fun mirrorZ(
            z: Int,
            sizeZ: Int,
            mirror: Mirror,
        ): Int =
            when (mirror) {
                Mirror.NONE, Mirror.FRONT_BACK -> z
                Mirror.LEFT_RIGHT -> sizeZ - z - 1
            }

        private fun mirror(
            state: BlockState?,
            mirror: Mirror,
        ): BlockState? = state?.mirror(mirror)

        private fun mirror(
            states: List<BlockState>?,
            mirror: Mirror,
        ): List<BlockState>? = states?.map { mirror(it, mirror)!! }
    }
}
