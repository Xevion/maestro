package maestro.process.elytra;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import maestro.api.utils.PackedBlockPos;
import net.minecraft.world.phys.Vec3;

public final class NetherPath extends AbstractList<PackedBlockPos> {

    private static final NetherPath EMPTY_PATH = new NetherPath(Collections.emptyList());

    private final List<PackedBlockPos> backing;

    NetherPath(List<PackedBlockPos> backing) {
        this.backing = backing;
    }

    @Override
    public PackedBlockPos get(int index) {
        return this.backing.get(index);
    }

    @Override
    public int size() {
        return this.backing.size();
    }

    /**
     * @return The last position in the path, or {@code null} if empty
     */
    @Override
    public PackedBlockPos getLast() {
        return this.isEmpty() ? null : this.backing.getLast();
    }

    public Vec3 getVec(int index) {
        final PackedBlockPos pos = this.get(index);
        return new Vec3(pos.getX(), pos.getY(), pos.getZ());
    }

    public static NetherPath emptyPath() {
        return EMPTY_PATH;
    }
}
