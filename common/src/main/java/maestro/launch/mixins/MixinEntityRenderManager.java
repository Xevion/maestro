package maestro.launch.mixins;

import maestro.utils.accessor.IEntityRenderManager;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderManager implements IEntityRenderManager {

    @Override
    public double renderPosX() {
        return ((EntityRenderDispatcher) (Object) this).camera.getPosition().x;
    }

    @Override
    public double renderPosY() {
        return ((EntityRenderDispatcher) (Object) this).camera.getPosition().y;
    }

    @Override
    public double renderPosZ() {
        return ((EntityRenderDispatcher) (Object) this).camera.getPosition().z;
    }
}
