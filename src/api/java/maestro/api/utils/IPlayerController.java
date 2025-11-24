package maestro.api.utils;

import maestro.api.MaestroAPI;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public interface IPlayerController {

    void syncHeldItem();

    boolean hasBrokenBlock();

    boolean onPlayerDamageBlock(BlockPos pos, Direction side);

    void resetBlockRemoving();

    void windowClick(int windowId, int slotId, int mouseButton, ClickType type, Player player);

    GameType getGameType();

    InteractionResult processRightClickBlock(
            LocalPlayer player, Level world, InteractionHand hand, BlockHitResult result);

    InteractionResult processRightClick(LocalPlayer player, Level world, InteractionHand hand);

    boolean clickBlock(BlockPos loc, Direction face);

    void setHittingBlock(boolean hittingBlock);

    default double getBlockReachDistance() {
        return this.getGameType().isCreative()
                ? 5.0F
                : MaestroAPI.getSettings().blockReachDistance.value;
    }
}
