package org.lullaby.cherubmod.event;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.Archangel;
import org.lullaby.cherubmod.capability.IRaceData;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.network.CherubNetwork;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class JudgementAbilityEvents {

    private static final float TOTEM_TRIGGER_RATIO = 0.50F;
    private static final float EXECUTE_HEALTH_RATIO = 0.10F;
    private static final float HEALTH_EPSILON = 0.001F;
    private static final byte ENTITY_EVENT_TOTEM = 35;

    public static boolean isJudgementCherub(PlayerEntity player) {
        return player.getCapability(RaceCapability.INSTANCE)
                .map(data -> data.getRace() == Race.CHERUB && data.getArchangel() == Archangel.JUDGEMENT)
                .orElse(false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntityLiving();
        if (target.level.isClientSide) {
            return;
        }

        DamageSource source = event.getSource();
        float damage = event.getAmount();
        if (damage <= 0.0F) {
            return;
        }

        // 1. Defensive: Free Totem of Undying when dropping to or below 50% health
        if (target instanceof ServerPlayerEntity && !source.isBypassInvul()) {
            ServerPlayerEntity victim = (ServerPlayerEntity) target;
            boolean triggered = tryTriggerJudgementTotem(victim, event);
            if (triggered) {
                // Prevent a simultaneous execute from killing through the totem that just triggered
                return;
            }
        }

        // 2. Offensive: Execute enemies left below 10% max health after the hit
        PlayerEntity attacker = resolveAttacker(source);
        if (attacker == null || attacker == target || !isJudgementCherub(attacker)) {
            return;
        }

        if (!target.isAlive() || target.isDeadOrDying()) {
            return;
        }
        if (target instanceof PlayerEntity) {
            PlayerEntity targetPlayer = (PlayerEntity) target;
            if (targetPlayer.isCreative() || targetPlayer.isSpectator()) {
                return;
            }
        }

        float currentHealth = target.getHealth();
        float remainingHealth = currentHealth - event.getAmount();
        float executeThreshold = target.getMaxHealth() * EXECUTE_HEALTH_RATIO;

        if (remainingHealth > 0.0F && remainingHealth < executeThreshold) {
            event.setAmount(currentHealth);
        }
    }

    private static boolean tryTriggerJudgementTotem(ServerPlayerEntity player, LivingDamageEvent event) {
        IRaceData data = player.getCapability(RaceCapability.INSTANCE).orElse(null);
        if (data == null
                || data.getRace() != Race.CHERUB
                || data.getArchangel() != Archangel.JUDGEMENT
                || !data.isJudgementTotemCharged()) {
            return false;
        }

        float damage = event.getAmount();
        float preHitHealth = player.getHealth();
        float halfHealth = player.getMaxHealth() * TOTEM_TRIGGER_RATIO;
        if (preHitHealth - damage > halfHealth + HEALTH_EPSILON) {
            return false;
        }

        // Consume charge and apply vanilla Totem of Undying effects
        data.setJudgementTotemCharged(false);
        player.removeAllEffects();
        player.addEffect(new EffectInstance(Effects.REGENERATION, 900, 1));
        player.addEffect(new EffectInstance(Effects.ABSORPTION, 100, 1));
        player.addEffect(new EffectInstance(Effects.FIRE_RESISTANCE, 800, 0));

        // Re-read health after removeAllEffects() in case Health Boost was stripped
        float postClearHealth = player.getHealth();
        if (postClearHealth - damage <= 0.0F) {
            if (postClearHealth > 1.0F) {
                event.setAmount(postClearHealth - 1.0F);
            } else {
                event.setAmount(0.0F);
                player.setHealth(1.0F);
            }
        }

        // Native vanilla Totem animation, particles, and sound (works without a held totem)
        player.level.broadcastEntityEvent(player, ENTITY_EVENT_TOTEM);
        CherubNetwork.syncToTrackingAndSelf(player);
        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayerEntity)) {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) event.player;
        if (!player.isAlive() || player.getHealth() < player.getMaxHealth() - HEALTH_EPSILON) {
            return;
        }

        player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
            if (data.getRace() == Race.CHERUB && !data.isJudgementTotemCharged()) {
                data.setJudgementTotemCharged(true);
                CherubNetwork.syncToTrackingAndSelf(player);
            }
        });
    }

    @Nullable
    private static PlayerEntity resolveAttacker(@Nullable DamageSource source) {
        if (source == null) {
            return null;
        }
        if (source.getEntity() instanceof PlayerEntity) {
            return (PlayerEntity) source.getEntity();
        }
        if (source.getDirectEntity() instanceof PlayerEntity) {
            return (PlayerEntity) source.getDirectEntity();
        }
        return null;
    }
}