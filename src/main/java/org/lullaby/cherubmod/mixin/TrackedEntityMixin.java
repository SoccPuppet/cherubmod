package org.lullaby.cherubmod.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.TrackedEntity;
import org.lullaby.cherubmod.event.PlushAbilityEvents;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrackedEntity.class)
public abstract class TrackedEntityMixin {

    @Shadow
    @Final
    private Entity entity;

    @Inject(
            method = "sendChanges()V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/Entity;hurtMarked:Z",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    private void cherubmod_syncUncappedPlushVelocity(CallbackInfo ci) {
        if (this.entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) this.entity;
            if (PlushAbilityEvents.isPlush(player)) {
                PlushAbilityEvents.sendUncappedVelocityIfNeeded(player);
            }
        }
    }
}