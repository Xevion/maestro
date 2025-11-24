package maestro.api.schematic;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;

public class MirroredSchematic implements ISchematic {

    private final ISchematic schematic;
    private final Mirror mirror;

    public MirroredSchematic(ISchematic schematic, Mirror mirror) {
        this.schematic = schematic;
        this.mirror = mirror;
    }

    @Override
    public boolean inSchematic(int x, int y, int z, BlockState currentState) {
        return schematic.inSchematic(
                mirrorX(x, widthX(), mirror),
                y,
                mirrorZ(z, lengthZ(), mirror),
                mirror(currentState, mirror));
    }

    @Override
    public BlockState desiredState(
            int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
        return mirror(
                schematic.desiredState(
                        mirrorX(x, widthX(), mirror),
                        y,
                        mirrorZ(z, lengthZ(), mirror),
                        mirror(current, mirror),
                        mirror(approxPlaceable, mirror)),
                mirror);
    }

    @Override
    public void reset() {
        schematic.reset();
    }

    @Override
    public int widthX() {
        return schematic.widthX();
    }

    @Override
    public int heightY() {
        return schematic.heightY();
    }

    @Override
    public int lengthZ() {
        return schematic.lengthZ();
    }

    private static int mirrorX(int x, int sizeX, Mirror mirror) {
        return switch (mirror) {
            case NONE, LEFT_RIGHT -> x;
            case FRONT_BACK -> sizeX - x - 1;
        };
    }

    private static int mirrorZ(int z, int sizeZ, Mirror mirror) {
        return switch (mirror) {
            case NONE, FRONT_BACK -> z;
            case LEFT_RIGHT -> sizeZ - z - 1;
        };
    }

    private static BlockState mirror(BlockState state, Mirror mirror) {
        if (state == null) {
            return null;
        }
        return state.mirror(mirror);
    }

    private static List<BlockState> mirror(List<BlockState> states, Mirror mirror) {
        if (states == null) {
            return null;
        }
        return states.stream().map(s -> mirror(s, mirror)).collect(Collectors.toList());
    }
}
