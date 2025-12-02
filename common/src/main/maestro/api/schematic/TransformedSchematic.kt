package maestro.api.schematic

import net.minecraft.world.level.block.state.BlockState

/**
 * Base class for schematic decorators that transform coordinates or block states while delegating
 * dimension queries to the wrapped schematic.
 *
 * Eliminates boilerplate delegation code for dimension methods and reset().
 */
abstract class TransformedSchematic(
    protected val schematic: ISchematic,
) : ISchematic {
    override fun widthX(): Int = schematic.widthX()

    override fun heightY(): Int = schematic.heightY()

    override fun lengthZ(): Int = schematic.lengthZ()

    override fun reset() {
        schematic.reset()
    }

    abstract override fun inSchematic(
        x: Int,
        y: Int,
        z: Int,
        currentState: BlockState?,
    ): Boolean

    abstract override fun desiredState(
        x: Int,
        y: Int,
        z: Int,
        current: BlockState?,
        approxPlaceable: List<BlockState>?,
    ): BlockState
}
