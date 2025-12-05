package maestro.api.player;

import maestro.api.AgentAPI;
import maestro.utils.accessor.IPlayerControllerMP;
import net.minecraft.client.Minecraft;
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

/** Player controller that chains to the primary player controller's methods */
public final class PlayerController {

    private final Minecraft mc;

    public PlayerController(Minecraft mc) {
        this.mc = mc;
    }

    public void syncHeldItem() {
        ((IPlayerControllerMP) mc.gameMode).callSyncCurrentPlayItem();
    }

    public boolean hasBrokenBlock() {
        return !((IPlayerControllerMP) mc.gameMode).isHittingBlock();
    }

    public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
        return mc.gameMode.continueDestroyBlock(pos, side);
    }

    public void resetBlockRemoving() {
        mc.gameMode.stopDestroyBlock();
    }

    public void windowClick(
            int windowId, int slotId, int mouseButton, ClickType type, Player player) {
        mc.gameMode.handleInventoryMouseClick(windowId, slotId, mouseButton, type, player);
    }

    public GameType getGameType() {
        return mc.gameMode.getPlayerMode();
    }

    public InteractionResult processRightClickBlock(
            LocalPlayer player, Level world, InteractionHand hand, BlockHitResult result) {
        // primaryplayercontroller is always in a ClientWorld so this is ok
        return mc.gameMode.useItemOn(player, hand, result);
    }

    public InteractionResult processRightClick(
            LocalPlayer player, Level world, InteractionHand hand) {
        return mc.gameMode.useItem(player, hand);
    }

    public boolean clickBlock(BlockPos loc, Direction face) {
        return mc.gameMode.startDestroyBlock(loc, face);
    }

    public void setHittingBlock(boolean hittingBlock) {
        ((IPlayerControllerMP) mc.gameMode).setIsHittingBlock(hittingBlock);
    }

    public double getBlockReachDistance() {
        return this.getGameType().isCreative()
                ? 5.0F
                : AgentAPI.getSettings().blockReachDistance.value;
    }
}
