package org.lullaby.cherubmod.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.potion.Effect;
import org.lullaby.cherubmod.event.JudgementAbilityEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Effect.class)
public abstract class EffectMixin {

    @Inject(
            method = "applyInstantenousEffect(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/LivingEntity;ID)V",
            at = @At("TAIL")
    )
    private void cherubmod_onInstantEffectApplied(
            @Nullable Entity source,
            @Nullable Entity indirectSource,
            LivingEntity target,
            int amplifier,
            double health,
            CallbackInfo ci
    ) {
        JudgementAbilityEvents.onInstantEffectApplied(
                (Effect) (Object) this,
                target,
                amplifier,
                health
        );
    }
}