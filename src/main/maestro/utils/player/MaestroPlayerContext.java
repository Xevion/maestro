package maestro.utils.player;

import maestro.Agent;
import maestro.api.cache.IWorldData;
import maestro.api.utils.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * Implementation of {@link IPlayerContext} that provides information about the primary player.
 *
 * @author Brady
 * @since 11/12/2018
 */
public final class MaestroPlayerContext implements IPlayerContext {

    private final Agent maestro;
    private final Minecraft mc;
    private final IPlayerController playerController;

    public MaestroPlayerContext(Agent maestro, Minecraft mc) {
        this.maestro = maestro;
        this.mc = mc;
        this.playerController = new MaestroPlayerController(mc);
    }

    @Override
    public Minecraft minecraft() {
        return this.mc;
    }

    @Override
    public LocalPlayer player() {
        return this.mc.player;
    }

    @Override
    public IPlayerController playerController() {
        return this.playerController;
    }

    @Override
    public Level world() {
        return this.mc.level;
    }

    @Override
    public IWorldData worldData() {
        return this.maestro.getWorldProvider().getCurrentWorld();
    }

    @Override
    public BetterBlockPos viewerPos() {
        final Entity entity = this.mc.getCameraEntity();
        return entity == null ? this.playerFeet() : BetterBlockPos.from(entity.blockPosition());
    }

    @Override
    public Rotation playerRotations() {
        return this.maestro
                .getLookBehavior()
                .getEffectiveRotation()
                .orElseGet(IPlayerContext.super::playerRotations);
    }

    @Override
    public HitResult objectMouseOver() {
        return RayTraceUtils.rayTraceTowards(
                player(), playerRotations(), playerController().getBlockReachDistance());
    }
}
