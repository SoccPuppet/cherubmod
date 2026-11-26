package org.lullaby.cherubmod.mixin;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.CreatureAttribute;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.util.DamageSource;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.damage.StrengthPunchDamageSource;
import org.lullaby.cherubmod.event.MoonAbilityEvents;
import org.lullaby.cherubmod.event.PlushAbilityEvents;
import org.lullaby.cherubmod.event.StrengthAbilityEvents;
import org.lullaby.cherubmod.event.WorldAbilityEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Redirect(
            method = "attack(Lnet/minecraft/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/DamageSource;playerAttack(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/util/DamageSource;"
            )
    )
    private DamageSource cherubmod_strengthPunchSource(PlayerEntity attacker) {
        if (StrengthAbilityEvents.isStrengthPunch(attacker)) {
            float critMultiplier = StrengthAbilityEvents.consumePendingCritMultiplier(attacker);
            boolean crouching = attacker.isCrouching() || attacker.isShiftKeyDown();
            return StrengthPunchDamageSource.createPrimary(attacker, critMultiplier, crouching);
        }
        return DamageSource.playerAttack(attacker);
    }

    @Redirect(
            method = "attack(Lnet/minecraft/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/enchantment/EnchantmentHelper;getFireAspect(Lnet/minecraft/entity/LivingEntity;)I"
            )
    )
    private int cherubmod_suppressMoonFireAspect(LivingEntity attacker) {
        if (attacker instanceof PlayerEntity && MoonAbilityEvents.isMoonCherub((PlayerEntity) attacker)) {
            return 0;
        }
        return EnchantmentHelper.getFireAspect(attacker);
    }

    @Redirect(
            method = "attack(Lnet/minecraft/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/enchantment/EnchantmentHelper;getDamageBonus(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/CreatureAttribute;)F"
            )
    )
    private float cherubmod_scaleWorldEnchantDamage(ItemStack stack, CreatureAttribute creatureAttribute) {
        float bonus = EnchantmentHelper.getDamageBonus(stack, creatureAttribute);
        PlayerEntity attacker = (PlayerEntity) (Object) this;
        if (bonus > 0.0F && WorldAbilityEvents.isWorldCherub(attacker)) {
            return bonus * WorldAbilityEvents.getDpsDamageMultiplier(attacker);
        }
        return bonus;
    }

    @Redirect(
            method = "attack(Lnet/minecraft/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/play/ServerPlayNetHandler;send(Lnet/minecraft/network/IPacket;)V"
            )
    )
    private void cherubmod_syncUncappedPlushAttackVelocity(ServerPlayNetHandler connection, IPacket<?> packet) {
        connection.send(packet);
        if (packet instanceof SEntityVelocityPacket && PlushAbilityEvents.isPlush(connection.player)) {
            PlushAbilityEvents.sendUncappedVelocityIfNeeded(connection.player);
        }
    }

    @Inject(method = "attack(Lnet/minecraft/entity/Entity;)V", at = @At("RETURN"))
    private void cherubmod_clearPendingStrengthCrit(Entity p_71059_1_, CallbackInfo ci) {
        PlayerEntity attacker = (PlayerEntity) (Object) this;
        StrengthAbilityEvents.clearPendingCritMultiplier(attacker);
    }

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
