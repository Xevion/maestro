package maestro.utils.schematic;

import java.util.stream.Stream;
import maestro.api.schematic.ISchematic;
import maestro.api.schematic.MaskSchematic;
import maestro.api.selection.ISelection;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

public class SelectionSchematic extends MaskSchematic {

    private final ISelection[] selections;

    public SelectionSchematic(ISchematic schematic, Vec3i origin, ISelection[] selections) {
        super(schematic);
        this.selections =
                Stream.of(selections)
                        .map(
                                sel ->
                                        sel.shift(Direction.WEST, origin.getX())
                                                .shift(Direction.DOWN, origin.getY())
                                                .shift(Direction.NORTH, origin.getZ()))
                        .toArray(ISelection[]::new);
    }

    @Override
    protected boolean partOfMask(int x, int y, int z, BlockState currentState) {
        for (ISelection selection : selections) {
            if (x >= selection.min().getX()
                    && y >= selection.min().getY()
                    && z >= selection.min().getZ()
                    && x <= selection.max().getX()
                    && y <= selection.max().getY()
                    && z <= selection.max().getZ()) {
                return true;
            }
        }
        return false;
    }
}
