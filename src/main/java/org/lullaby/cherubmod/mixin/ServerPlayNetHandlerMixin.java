package org.lullaby.cherubmod.mixin;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.ServerPlayNetHandler;
import org.lullaby.cherubmod.event.WorldAbilityEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetHandler.class)
public abstract class ServerPlayNetHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Shadow
    private int aboveGroundTickCount;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void cherubmod_preventWorldStasisFlightKick(CallbackInfo ci) {
        if (this.player != null && WorldAbilityEvents.isInWorldStasis(this.player)) {
            this.aboveGroundTickCount = 0;
        }
    }
}