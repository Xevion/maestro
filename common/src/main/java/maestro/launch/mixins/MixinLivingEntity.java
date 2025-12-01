package maestro.launch.mixins;

import java.util.Optional;
import maestro.api.IAgent;
import maestro.api.MaestroAPI;
import maestro.api.event.events.RotationMoveEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    /** Event called to override the movement direction when jumping */
    @Unique private RotationMoveEvent jumpRotationEvent;

    @Unique private RotationMoveEvent elytraRotationEvent;

    private MixinLivingEntity(EntityType<?> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void preMoveRelative(CallbackInfo ci) {
        this.getMaestro()
                .ifPresent(
                        maestro -> {
                            this.jumpRotationEvent =
                                    new RotationMoveEvent(
                                            RotationMoveEvent.Type.JUMP,
                                            this.getYRot(),
                                            this.getXRot());
                            maestro.getGameEventHandler()
                                    .onPlayerRotationMove(this.jumpRotationEvent);
                        });
    }

    @Redirect(
            method = "jumpFromGround",
            at =
                    @At(
                            value = "INVOKE",
                            target = "net/minecraft/world/entity/LivingEntity.getYRot()F"))
    private float overrideYaw(LivingEntity self) {
        if (self instanceof LocalPlayer
                && MaestroAPI.getProvider().getMaestroForPlayer((LocalPlayer) (Object) this)
                        != null) {
            return this.jumpRotationEvent.getYaw();
        }
        return self.getYRot();
    }

    @Inject(
            method = "updateFallFlyingMovement",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "net/minecraft/world/entity/LivingEntity.getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private void onPreElytraMove(Vec3 direction, final CallbackInfoReturnable<Vec3> cir) {
        this.getMaestro()
                .ifPresent(
                        maestro -> {
                            this.elytraRotationEvent =
                                    new RotationMoveEvent(
                                            RotationMoveEvent.Type.MOTION_UPDATE,
                                            this.getYRot(),
                                            this.getXRot());
                            maestro.getGameEventHandler()
                                    .onPlayerRotationMove(this.elytraRotationEvent);
                            this.setYRot(this.elytraRotationEvent.getYaw());
                            this.setXRot(this.elytraRotationEvent.getPitch());
                        });
    }

    @Inject(
            method = "travelFallFlying",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "net/minecraft/world/entity/LivingEntity.move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
                            shift = At.Shift.AFTER))
    private void onPostElytraMove(final CallbackInfo ci) {
        if (this.elytraRotationEvent != null) {
            this.setYRot(this.elytraRotationEvent.getOriginal().getYaw());
            this.setXRot(this.elytraRotationEvent.getOriginal().getPitch());
            this.elytraRotationEvent = null;
        }
    }

    @Unique
    @SuppressWarnings(
            "IsInstanceIncompatibleType") // Mixin: 'this' is LivingEntity, checking if it's a
    // LocalPlayer at runtime
    private Optional<IAgent> getMaestro() {
        // noinspection ConstantConditions
        if (LocalPlayer.class.isInstance(this)) {
            return Optional.ofNullable(
                    MaestroAPI.getProvider().getMaestroForPlayer((LocalPlayer) (Object) this));
        } else {
            return Optional.empty();
        }
    }
}
