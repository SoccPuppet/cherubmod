package org.lullaby.cherubmod.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.DamageSource;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void cherubmod_keepInventoryOnDeath(CallbackInfo info){
        PlayerEntity player = (PlayerEntity)(Object)this;
        player.getCapability(RaceCapability.INSTANCE).ifPresent(cap -> {
            if(cap.getRace() == Race.CHERUB || cap.isPendingRaceChange()){
                info.cancel();
            }
        });
    }
}
