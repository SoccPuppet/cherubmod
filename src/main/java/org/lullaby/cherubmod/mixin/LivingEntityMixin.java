package org.lullaby.cherubmod.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.DamageSource;
import org.lullaby.cherubmod.event.MoonAbilityEvents;
import org.lullaby.cherubmod.event.StrengthAbilityEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @ModifyVariable(
            method = "hurt(Lnet/minecraft/util/DamageSource;F)Z",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private float cherubmod_adjustCherubDamageAmount(float amount, DamageSource source, float originalAmount) {
        LivingEntity target = (LivingEntity) (Object) this;
        if (target.level.isClientSide) {
            return amount;
        }
        float adjusted = StrengthAbilityEvents.adjustIncomingDamage(target, source, amount);
        return MoonAbilityEvents.adjustIncomingDamage(target, source, adjusted);
    }
}