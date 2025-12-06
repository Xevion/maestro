package maestro.launch.mixins;

import maestro.utils.accessor.IItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class MixinItemStack implements IItemStack {

    @Shadow @Final private Item item;

    @Unique private int maestrohash;

    @Shadow
    public abstract int getDamageValue();

    @Unique
    private void recalculateHash() {
        maestrohash = item == null ? -1 : item.hashCode() + getDamageValue();
    }

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        recalculateHash();
    }

    @Inject(method = "setDamageValue", at = @At("TAIL"))
    private void onItemDamageSet(CallbackInfo ci) {
        recalculateHash();
    }

    @Override
    public int getMaestroHash() {
        // TODO: figure out why <init> mixin not working, was 0 for some reason
        if (maestrohash == 0) recalculateHash();
        return maestrohash;
    }
}
