package maestro.api.process;

import maestro.api.utils.BlockOptionalMeta;
import net.minecraft.world.level.block.Block;

/** but it rescans the world every once in a while so it doesn't get fooled by its cache */
public interface IGetToBlockProcess extends IMaestroProcess {

    void getToBlock(BlockOptionalMeta block);

    default void getToBlock(Block block) {
        getToBlock(new BlockOptionalMeta(block));
    }

    boolean blacklistClosest();
}
