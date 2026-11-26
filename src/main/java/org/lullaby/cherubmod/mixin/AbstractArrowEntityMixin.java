package org.lullaby.cherubmod.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import org.lullaby.cherubmod.event.PlushAbilityEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractArrowEntity.class)
public abstract class AbstractArrowEntityMixin {
    @Redirect(
            method = "onHitEntity(Lnet/minecraft/util/math/EntityRayTraceResult;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;push(DDD)V"
            )
    )
    private void cherubmod_triplePlushPunchKnockback(LivingEntity target, double x, double y, double z) {
        if (!target.level.isClientSide
                && target instanceof PlayerEntity
                && PlushAbilityEvents.isPlush((PlayerEntity) target)) {
            double mult = PlushAbilityEvents.PLUSH_KNOCKBACK_MULTIPLIER;
            target.push(x * mult, y * mult, z * mult);
            target.hurtMarked = true;
            return;
        }
        target.push(x, y, z);
    }
}