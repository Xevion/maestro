package maestro.api.schematic;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public class RotatedSchematic implements ISchematic {

    private final ISchematic schematic;
    private final Rotation rotation;
    private final Rotation inverseRotation;

    public RotatedSchematic(ISchematic schematic, Rotation rotation) {
        this.schematic = schematic;
        this.rotation = rotation;
        // I don't think a 14 line switch would improve readability
        this.inverseRotation = rotation.getRotated(rotation).getRotated(rotation);
    }

    @Override
    public boolean inSchematic(int x, int y, int z, BlockState currentState) {
        return schematic.inSchematic(
                rotateX(x, z, widthX(), lengthZ(), inverseRotation),
                y,
                rotateZ(x, z, widthX(), lengthZ(), inverseRotation),
                rotate(currentState, inverseRotation));
    }

    @Override
    public BlockState desiredState(
            int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
        return rotate(
                schematic.desiredState(
                        rotateX(x, z, widthX(), lengthZ(), inverseRotation),
                        y,
                        rotateZ(x, z, widthX(), lengthZ(), inverseRotation),
                        rotate(current, inverseRotation),
                        rotate(approxPlaceable, inverseRotation)),
                rotation);
    }

    @Override
    public void reset() {
        schematic.reset();
    }

    @Override
    public int widthX() {
        return flipsCoordinates(rotation) ? schematic.lengthZ() : schematic.widthX();
    }

    @Override
    public int heightY() {
        return schematic.heightY();
    }

    @Override
    public int lengthZ() {
        return flipsCoordinates(rotation) ? schematic.widthX() : schematic.lengthZ();
    }

    /** Wether {@code rotation} swaps the x and z components */
    private static boolean flipsCoordinates(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
    }

    /** The x component of x,y after applying the rotation */
    private static int rotateX(int x, int z, int sizeX, int sizeZ, Rotation rotation) {
        return switch (rotation) {
            case NONE -> x;
            case CLOCKWISE_90 -> sizeZ - z - 1;
            case CLOCKWISE_180 -> sizeX - x - 1;
            case COUNTERCLOCKWISE_90 -> z;
        };
    }

    /** The z component of x,y after applying the rotation */
    private static int rotateZ(int x, int z, int sizeX, int sizeZ, Rotation rotation) {
        return switch (rotation) {
            case NONE -> z;
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> sizeZ - z - 1;
            case COUNTERCLOCKWISE_90 -> sizeX - x - 1;
        };
    }

    private static BlockState rotate(BlockState state, Rotation rotation) {
        if (state == null) {
            return null;
        }
        return state.rotate(rotation);
    }

    private static List<BlockState> rotate(List<BlockState> states, Rotation rotation) {
        if (states == null) {
            return null;
        }
        return states.stream().map(s -> rotate(s, rotation)).collect(Collectors.toList());
    }
}
