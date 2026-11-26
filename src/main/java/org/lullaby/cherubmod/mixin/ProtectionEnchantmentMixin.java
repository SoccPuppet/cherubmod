package org.lullaby.cherubmod.mixin;

import net.minecraft.enchantment.ProtectionEnchantment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.lullaby.cherubmod.event.PlushAbilityEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProtectionEnchantment.class)
public abstract class ProtectionEnchantmentMixin {

    @Inject(
            method = "getExplosionKnockbackAfterDampener(Lnet/minecraft/entity/LivingEntity;D)D",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void cherubmod_triplePlushExplosionKnockback(
            LivingEntity entity,
            double knockback,
            CallbackInfoReturnable<Double> cir
    ) {
        if (!entity.level.isClientSide
                && entity instanceof PlayerEntity
                && PlushAbilityEvents.isPlush((PlayerEntity) entity)) {
            cir.setReturnValue(cir.getReturnValueD() * PlushAbilityEvents.PLUSH_KNOCKBACK_MULTIPLIER);
        }
    }
}