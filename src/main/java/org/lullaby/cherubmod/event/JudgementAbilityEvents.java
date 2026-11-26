package org.lullaby.cherubmod.event;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class JudgementAbilityEvents {

    private static final float TOTEM_TRIGGER_RATIO = 0.50F;
    private static final float EXECUTE_HEALTH_RATIO = 0.10F;
    private static final float HEALTH_EPSILON = 0.001F;
    private static final byte ENTITY_EVENT_TOTEM = 35;

    private static final int WITHER_FROM_HEAL_TICKS = 80;        // 4 seconds
    private static final int MIN_WITHER_FROM_HEAL_TICKS = 20;    // 1 second minimum on splash edge
    private static final int SLOW_FALLING_FROM_HARM_TICKS = 160; // 8 seconds
    private static final int MIN_SLOW_FALLING_TICKS = 40;        // 2 seconds minimum on splash edge

    // Suppresses only the specific paired effect currently being applied to stop recursion
    private static final ThreadLocal<Effect> SUPPRESSED_PAIR_EFFECT = new ThreadLocal<>();

    private static Map<Effect, Effect> effectPairs;

    private static Map<Effect, Effect> getEffectPairs() {
        if (effectPairs == null) {
            Map<Effect, Effect> map = new IdentityHashMap<>();
            registerPair(map, Effects.DAMAGE_RESISTANCE, Effects.WEAKNESS);
            registerPair(map, Effects.JUMP, Effects.MOVEMENT_SLOWDOWN);
            registerPair(map, Effects.DAMAGE_BOOST, Effects.DIG_SLOWDOWN);
            registerPair(map, Effects.REGENERATION, Effects.HUNGER);
            registerPair(map, Effects.MOVEMENT_SPEED, Effects.GLOWING);
            registerPair(map, Effects.DIG_SPEED, Effects.UNLUCK);
            registerPair(map, Effects.INVISIBILITY, Effects.BLINDNESS);
            registerPair(map, Effects.LUCK, Effects.BAD_OMEN);
            registerPair(map, Effects.HEALTH_BOOST, Effects.POISON);
            registerPair(map, Effects.HEAL, Effects.WITHER);
            registerPair(map, Effects.SLOW_FALLING, Effects.HARM);
            effectPairs = Collections.unmodifiableMap(map);
        }
        return effectPairs;
    }

    private static void registerPair(Map<Effect, Effect> map, Effect positive, Effect negative) {
        map.put(positive, negative);
        map.put(negative, positive);
    }

    @Nullable
    public static Effect getPairedEffect(@Nullable Effect effect) {
        return effect != null ? getEffectPairs().get(effect) : null;
    }

    public static boolean isNotJudgementCherub(PlayerEntity player) {
        IRaceData data = player.getCapability(RaceCapability.INSTANCE).orElse(null);
        return data == null
                || data.getRace() != Race.CHERUB
                || data.getArchangel() != Archangel.JUDGEMENT;
    }

    // -------------------------------------------------------------------------
    // Rebirth Effect Mirroring (Timed Effects, Tipped Arrows, /effect)
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPotionAdded(PotionEvent.PotionAddedEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide || !(entity instanceof ServerPlayerEntity)) {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) entity;
        if (!player.isAlive() || player.isSpectator() || isNotJudgementCherub(player)) {
            return;
        }

        EffectInstance added = event.getPotionEffect();

        Effect sourceEffect = added.getEffect();
        if (sourceEffect == SUPPRESSED_PAIR_EFFECT.get()) {
            return;
        }

        Effect pairedEffect = getPairedEffect(sourceEffect);
        if (pairedEffect == null) {
            return;
        }

        // Only fire instant pulses (Wither -> Heal, Slow Falling -> Harm) on fresh application or tier upgrade
        if (pairedEffect.isInstantenous()) {
            EffectInstance old = event.getOldPotionEffect();
            if (old != null && added.getAmplifier() <= old.getAmplifier()) {
                return;
            }
        }

        applyPairedEffect(
                player,
                sourceEffect,
                pairedEffect,
                added.getDuration(),
                added.getAmplifier(),
                added.isAmbient(),
                added.isVisible(),
                added.showIcon(),
                1.0D
        );
    }

    /**
     * Called from EffectMixin at TAIL of Effect#applyInstantenousEffect.
     * Covers drinkable, splash, and lingering Healing/Harming potions, which bypass LivingEntity#addEffect.
     */
    public static void onInstantEffectApplied(Effect sourceEffect, LivingEntity target, int amplifier, double health) {
        if (target.level.isClientSide || health <= 0.0D || !(target instanceof ServerPlayerEntity)) {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) target;
        if (!player.isAlive() || player.isSpectator() || isNotJudgementCherub(player)) {
            return;
        }

        if (sourceEffect == SUPPRESSED_PAIR_EFFECT.get()) {
            return;
        }

        Effect pairedEffect = getPairedEffect(sourceEffect);
        if (pairedEffect == null) {
            return;
        }

        applyPairedEffect(
                player,
                sourceEffect,
                pairedEffect,
                0,
                amplifier,
                false,
                true,
                true,
                Math.min(1.0D, health)
        );
    }

    private static void applyPairedEffect(
            ServerPlayerEntity player,
            Effect sourceEffect,
            Effect pairedEffect,
            int duration,
            int amplifier,
            boolean ambient,
            boolean visible,
            boolean showIcon,
            double instantScale
    ) {
        Effect previous = SUPPRESSED_PAIR_EFFECT.get();
        SUPPRESSED_PAIR_EFFECT.set(pairedEffect);
        try {
            if (pairedEffect.isInstantenous()) {
                pairedEffect.applyInstantenousEffect(null, null, player, amplifier, instantScale);
            } else {
                int pairedDuration = resolvePairedDuration(sourceEffect, pairedEffect, duration, instantScale);
                int pairedAmplifier = pairedEffect == Effects.SLOW_FALLING ? 0 : amplifier;
                boolean pairedVisible = !ambient && visible;
                player.addEffect(new EffectInstance(
                        pairedEffect,
                        pairedDuration,
                        pairedAmplifier,
                        ambient,
                        pairedVisible,
                        showIcon
                ));
            }
        } finally {
            if (previous == null) {
                SUPPRESSED_PAIR_EFFECT.remove();
            } else {
                SUPPRESSED_PAIR_EFFECT.set(previous);
            }
        }
    }

    private static int resolvePairedDuration(Effect sourceEffect, Effect pairedEffect, int sourceDuration, double scale) {
        if (sourceEffect == Effects.HEAL && pairedEffect == Effects.WITHER) {
            return Math.max(MIN_WITHER_FROM_HEAL_TICKS, (int) Math.round(WITHER_FROM_HEAL_TICKS * scale));
        }
        if (sourceEffect == Effects.HARM && pairedEffect == Effects.SLOW_FALLING) {
            return Math.max(MIN_SLOW_FALLING_TICKS, (int) Math.round(SLOW_FALLING_FROM_HARM_TICKS * scale));
        }
        return sourceDuration;
    }

    // -------------------------------------------------------------------------
    // Free Totem at Half Health & Sub-10% Execution
    // -------------------------------------------------------------------------

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

        if (target instanceof ServerPlayerEntity && !source.isBypassInvul()) {
            ServerPlayerEntity victim = (ServerPlayerEntity) target;
            if (tryTriggerJudgementTotem(victim, event)) {
                return;
            }
        }

        PlayerEntity attacker = resolveAttacker(source);
        if (attacker == null || attacker == target || isNotJudgementCherub(attacker)) {
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

        data.setJudgementTotemCharged(false);
        player.removeAllEffects();
        player.addEffect(new EffectInstance(Effects.REGENERATION, 900, 1));
        player.addEffect(new EffectInstance(Effects.ABSORPTION, 100, 1));
        player.addEffect(new EffectInstance(Effects.FIRE_RESISTANCE, 800, 0));

        float postClearHealth = player.getHealth();
        if (postClearHealth - damage <= 0.0F) {
            if (postClearHealth > 1.0F) {
                event.setAmount(postClearHealth - 1.0F);
            } else {
                event.setAmount(0.0F);
                player.setHealth(1.0F);
            }
        }

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

        IRaceData data = player.getCapability(RaceCapability.INSTANCE).orElse(null);
        if (data != null && data.getRace() == Race.CHERUB && !data.isJudgementTotemCharged()) {
            data.setJudgementTotemCharged(true);
            CherubNetwork.syncToTrackingAndSelf(player);
        }
    }

    @Nullable
    private static PlayerEntity resolveAttacker(@Nullable DamageSource source) {
        return getPlayerEntity(source);
    }

    static PlayerEntity getPlayerEntity(@Nullable DamageSource source) {
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