package org.lullaby.cherubmod.mixin;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import org.lullaby.cherubmod.event.WorldAbilityEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {

    @Redirect(
            method = "updateSleepingPlayerList()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/ServerPlayerEntity;isSpectator()Z"
            )
    )
    private boolean cherubmod_excludeWorldCherubFromSleepVote(ServerPlayerEntity player) {
        return player.isSpectator() || WorldAbilityEvents.isWorldCherub(player);
    }

    @Redirect(
            method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;noneMatch(Ljava/util/function/Predicate;)Z"
            )
    )
    private boolean cherubmod_ignoreWorldCherubInSleepFinish(
            Stream<ServerPlayerEntity> stream,
            Predicate<ServerPlayerEntity> predicate
    ) {
        return stream.noneMatch(player -> !WorldAbilityEvents.isWorldCherub(player) && predicate.test(player));
    }
}