package maestro.api.schematic.mask;

import maestro.api.schematic.mask.operator.BinaryOperatorMask;
import maestro.api.schematic.mask.operator.NotMask;
import maestro.api.utils.BooleanBinaryOperators;
import net.minecraft.world.level.block.state.BlockState;

public interface Mask {

    /**
     * @param x The relative x position of the block
     * @param y The relative y position of the block
     * @param z The relative z position of the block
     * @param currentState The current state of that block in the world, may be {@code null}
     * @return Whether the given position is included in this mask
     */
    boolean partOfMask(int x, int y, int z, BlockState currentState);

    int widthX();

    int heightY();

    int lengthZ();

    default Mask not() {
        return new NotMask(this);
    }

    default Mask union(Mask other) {
        return new BinaryOperatorMask(this, other, BooleanBinaryOperators.OR);
    }

    default Mask intersection(Mask other) {
        return new BinaryOperatorMask(this, other, BooleanBinaryOperators.AND);
    }

    default Mask xor(Mask other) {
        return new BinaryOperatorMask(this, other, BooleanBinaryOperators.XOR);
    }
}
