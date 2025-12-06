package maestro.player;

import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import maestro.Agent;
import maestro.cache.WorldData;
import maestro.utils.PackedBlockPos;
import maestro.utils.RayTraceUtils;
import maestro.utils.Rotation;
import maestro.utils.Vec3ExtKt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Provides information about the primary player. */
public final class PlayerContext {

    private final Agent agent;
    private final Minecraft mc;
    private final PlayerController playerController;

    public PlayerContext(Agent agent, Minecraft mc) {
        this.agent = agent;
        this.mc = mc;
        this.playerController = new PlayerController(mc);
    }

    public Minecraft minecraft() {
        return this.mc;
    }

    public LocalPlayer player() {
        return this.mc.player;
    }

    public PlayerController playerController() {
        return this.playerController;
    }

    public Level world() {
        return this.mc.level;
    }

    public WorldData worldData() {
        return this.agent.getWorldProvider().getCurrentWorld();
    }

    public PackedBlockPos viewerPos() {
        final Entity entity = this.mc.getCameraEntity();
        return entity == null ? this.playerFeet() : new PackedBlockPos(entity.blockPosition());
    }

    public BlockPos viewerBlockPos() {
        final Entity entity = this.mc.getCameraEntity();
        return entity == null ? this.playerFeetBlockPos() : entity.blockPosition();
    }

    public Rotation playerRotations() {
        return this.agent
                .getLookBehavior()
                .getEffectiveRotation()
                .orElseGet(() -> new Rotation(player().getYRot(), player().getXRot()));
    }

    public HitResult objectMouseOver() {
        return RayTraceUtils.rayTraceTowards(
                player(), playerRotations(), playerController().getBlockReachDistance());
    }

    public Iterable<Entity> entities() {
        return ((ClientLevel) world()).entitiesForRendering();
    }

    public Stream<Entity> entitiesStream() {
        return StreamSupport.stream(entities().spliterator(), false);
    }

    public PackedBlockPos playerFeet() {
        return new PackedBlockPos(playerFeetBlockPos());
    }

    public BlockPos playerFeetBlockPos() {
        // TODO find a better way to deal with soul sand!!!!!
        BlockPos feetPos =
                new BlockPos(
                        (int) Math.floor(player().position().x),
                        (int) Math.floor(player().position().y + 0.1251),
                        (int) Math.floor(player().position().z));

        // sometimes when calling this from another thread or while world is null, it'll throw a
        // NullPointerException
        // that causes the game to immediately crash
        //
        // so of course crashing on 2b is horribly bad due to queue times and logout spot
        // catch the NPE and ignore it if it does happen
        //
        // this does not impact performance at all since we're not null checking constantly
        // if there is an exception, the only overhead is Java generating the exception object... so
        // we can ignore it
        try {
            if (world().getBlockState(feetPos).getBlock() instanceof SlabBlock) {
                return feetPos.above();
            }
        } catch (NullPointerException ignored) {
            // expected
        }

        return feetPos;
    }

    public Vec3 playerFeetAsVec() {
        return player().position();
    }

    public Vec3 playerHead() {
        return Vec3ExtKt.withY(
                player().position(), player().position().y + player().getEyeHeight());
    }

    public Vec3 playerMotion() {
        return player().getDeltaMovement();
    }

    /**
     * Returns the block that the crosshair is currently placed over. Updated once per tick.
     *
     * @return The position of the highlighted block
     */
    public Optional<BlockPos> getSelectedBlock() {
        HitResult result = objectMouseOver();
        if (result != null && result.getType() == HitResult.Type.BLOCK) {
            return Optional.of(((BlockHitResult) result).getBlockPos());
        }
        return Optional.empty();
    }

    public boolean isLookingAt(BlockPos pos) {
        return getSelectedBlock().equals(Optional.of(pos));
    }
}
