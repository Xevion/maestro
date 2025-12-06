package maestro.task.schematic;

import java.util.stream.Stream;
import maestro.schematic.ISchematic;
import maestro.schematic.MaskSchematic;
import maestro.selection.Selection;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

public class SelectionSchematic extends MaskSchematic {

    private final Selection[] selections;

    public SelectionSchematic(ISchematic schematic, Vec3i origin, Selection[] selections) {
        super(schematic);
        this.selections =
                Stream.of(selections)
                        .map(
                                sel ->
                                        sel.shift(Direction.WEST, origin.getX())
                                                .shift(Direction.DOWN, origin.getY())
                                                .shift(Direction.NORTH, origin.getZ()))
                        .toArray(Selection[]::new);
    }

    @Override
    protected boolean partOfMask(int x, int y, int z, BlockState currentState) {
        for (Selection selection : selections) {
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
