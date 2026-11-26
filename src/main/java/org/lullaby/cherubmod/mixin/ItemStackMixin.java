package org.lullaby.cherubmod.mixin;

import net.minecraft.item.ItemStack;
import org.lullaby.cherubmod.possession.PossessionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "isDamageableItem", at = @At("HEAD"), cancellable = true)
    private void cherubmod_preventPossessionDamageable(CallbackInfoReturnable<Boolean> cir) {
        if (PossessionHelper.isPossessionStack((ItemStack) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setDamageValue", at = @At("HEAD"), cancellable = true)
    private void cherubmod_preventPossessionDamageChange(int damage, CallbackInfo ci) {
        if (PossessionHelper.isPossessionStack((ItemStack) (Object) this)) {
            ci.cancel();
        }
    }
}