package maestro.selection;

import maestro.utils.PackedBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;

public class Selection {

    private final PackedBlockPos pos1;
    private final PackedBlockPos pos2;
    private final PackedBlockPos min;
    private final PackedBlockPos max;
    private final Vec3i size;
    private final AABB aabb;

    public Selection(PackedBlockPos pos1, PackedBlockPos pos2) {
        this.pos1 = pos1;
        this.pos2 = pos2;

        this.min =
                new PackedBlockPos(
                        Math.min(pos1.getX(), pos2.getX()),
                        Math.min(pos1.getY(), pos2.getY()),
                        Math.min(pos1.getZ(), pos2.getZ()));

        this.max =
                new PackedBlockPos(
                        Math.max(pos1.getX(), pos2.getX()),
                        Math.max(pos1.getY(), pos2.getY()),
                        Math.max(pos1.getZ(), pos2.getZ()));

        this.size =
                new Vec3i(
                        max.getX() - min.getX() + 1,
                        max.getY() - min.getY() + 1,
                        max.getZ() - min.getZ() + 1);

        this.aabb =
                new AABB(
                        min.getX(),
                        min.getY(),
                        min.getZ(),
                        max.getX() + 1,
                        max.getY() + 1,
                        max.getZ() + 1);
    }

    public PackedBlockPos pos1() {
        return pos1;
    }

    public PackedBlockPos pos2() {
        return pos2;
    }

    public PackedBlockPos min() {
        return min;
    }

    public PackedBlockPos max() {
        return max;
    }

    public Vec3i size() {
        return size;
    }

    public AABB aabb() {
        return aabb;
    }

    @Override
    public int hashCode() {
        return pos1.hashCode() ^ pos2.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Selection{pos1=%s,pos2=%s}", pos1, pos2);
    }

    /**
     * Since it might not be immediately obvious what this does, let me explain.
     *
     * <p>Let's say you specify [{@link Direction#UP}, this functions returns if pos2 is the highest
     * BlockPos. If you specify [{@link Direction#DOWN}, it returns if pos2 is the lowest BlockPos.
     *
     * @param facing The direction to check.
     * @return {@code true} if pos2 is further in that direction than pos1, {@code false} if it
     *     isn't, and something else if they're both at the same position on that axis (it really
     *     doesn't matter)
     */
    private boolean isPos2(Direction facing) {
        boolean negative = facing.getAxisDirection().getStep() < 0;

        return switch (facing.getAxis()) {
            case X -> (pos2.getX() > pos1.getX()) ^ negative;
            case Y -> (pos2.getY() > pos1.getY()) ^ negative;
            case Z -> (pos2.getZ() > pos1.getZ()) ^ negative;
        };
    }

    public Selection expand(Direction direction, int blocks) {
        if (isPos2(direction)) {
            return new Selection(pos1, pos2.relative(direction, blocks));
        } else {
            return new Selection(pos1.relative(direction, blocks), pos2);
        }
    }

    public Selection contract(Direction direction, int blocks) {
        if (isPos2(direction)) {
            return new Selection(pos1.relative(direction, blocks), pos2);
        } else {
            return new Selection(pos1, pos2.relative(direction, blocks));
        }
    }

    public Selection shift(Direction direction, int blocks) {
        return new Selection(pos1.relative(direction, blocks), pos2.relative(direction, blocks));
    }
}
