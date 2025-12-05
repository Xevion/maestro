package maestro.process.schematic;

import java.util.List;
import maestro.api.schematic.AbstractSchematic;
import maestro.api.schematic.IStaticSchematic;
import net.minecraft.world.level.block.state.BlockState;

/** Default implementation of {@link IStaticSchematic} */
public class StaticSchematic extends AbstractSchematic implements IStaticSchematic {

    protected BlockState[][][] states;

    public StaticSchematic() {}

    public StaticSchematic(BlockState[][][] states) {
        this.states = states;
        boolean empty = states.length == 0 || states[0].length == 0 || states[0][0].length == 0;
        this.x = empty ? 0 : states.length;
        this.z = empty ? 0 : states[0].length;
        this.y = empty ? 0 : states[0][0].length;
    }

    @Override
    public BlockState desiredState(
            int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
        return this.states[x][z][y];
    }

    @Override
    public BlockState getDirect(int x, int y, int z) {
        return this.states[x][z][y];
    }

    @Override
    public BlockState[] getColumn(int x, int z) {
        return this.states[x][z];
    }
}
