package org.lullaby.cherubmod.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.lullaby.cherubmod.event.PlushAbilityEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @ModifyVariable(
            method = "setSecondsOnFire(I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int cherubmod_doublePlushFireDuration(int seconds) {
        Entity self = (Entity) (Object) this;
        if (!self.level.isClientSide
                && seconds > 0
                && self instanceof PlayerEntity
                && PlushAbilityEvents.isPlush((PlayerEntity) self)) {
            return seconds * 2;
        }
        return seconds;
    }
}