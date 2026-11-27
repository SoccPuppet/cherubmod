package org.lullaby.cherubmod.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.DamageSource;
import org.lullaby.cherubmod.event.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
        adjusted = MoonAbilityEvents.adjustIncomingDamage(target, source, adjusted);
        return WorldAbilityEvents.adjustIncomingMeleeDamage(target, source, adjusted);
    }

    @Inject(method = "canBreatheUnderwater()Z", at = @At("HEAD"), cancellable = true)
    private void cherubmod_plushBreatheUnderwater(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof PlayerEntity && PlushAbilityEvents.isPlush((PlayerEntity) self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tickEffects()V", at = @At("HEAD"), cancellable = true)
    private void cherubmod_pauseEffectsInWorldStasis(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof PlayerEntity && WorldAbilityEvents.isInWorldStasis((PlayerEntity) self)) {
            ci.cancel();
        }
    }

    @Inject(method = "attackable()Z", at = @At("HEAD"), cancellable = true)
    private void cherubmod_untargetableInWorldStasis(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof PlayerEntity && WorldAbilityEvents.isInWorldStasis((PlayerEntity) self)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canAttack(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void cherubmod_preventTargetingWorldStasis(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof PlayerEntity && WorldAbilityEvents.isInWorldStasis((PlayerEntity) target)) {
            cir.setReturnValue(false);
        }
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