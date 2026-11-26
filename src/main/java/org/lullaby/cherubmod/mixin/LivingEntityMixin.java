package org.lullaby.cherubmod.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.DamageSource;
import org.lullaby.cherubmod.event.HangedOneAbilityEvents;
import org.lullaby.cherubmod.event.MoonAbilityEvents;
import org.lullaby.cherubmod.event.StrengthAbilityEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

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

    @Redirect(
            method = "knockback(FDD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getAttributeValue(Lnet/minecraft/entity/ai/attributes/Attribute;)D"
            )
    )
    private double cherubmod_hangedOneKnockbackResistance(LivingEntity entity, Attribute attribute) {
        double value = entity.getAttributeValue(attribute);
        if (attribute == Attributes.KNOCKBACK_RESISTANCE
                && entity instanceof PlayerEntity
                && HangedOneAbilityEvents.isHangedOneCherub((PlayerEntity) entity)) {
            return value - 0.5D;
        }
        return value;
    }
}