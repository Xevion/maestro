package maestro.launch.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import maestro.Agent;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public class MixinLightmapTextureManager {

    @WrapOperation(
            method = "updateLightTexture",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/dimension/DimensionType;ambientLight()F"))
    private float modifyAmbientLight(DimensionType instance, Operation<Float> original) {
        if (Agent.getPrimaryAgent().getSettings().fullbright.value) {
            return 1.0F;
        }
        return original.call(instance);
    }
}
