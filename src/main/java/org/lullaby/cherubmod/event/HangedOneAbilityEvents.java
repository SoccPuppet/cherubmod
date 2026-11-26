package org.lullaby.cherubmod.event;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.eventbus.api.Event;
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
import java.util.UUID;

import static org.lullaby.cherubmod.event.JudgementAbilityEvents.getPlayerEntity;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HangedOneAbilityEvents {

    private static final UUID PASSIVE_MAX_HEALTH_UUID =
            UUID.fromString("c3f21a9e-4d17-4f88-9b62-71c8a5e90112");
    private static final UUID ACTIVE_MAX_HEALTH_UUID =
            UUID.fromString("7a49e02b-81c4-46a1-b3d5-90f42e81c623");

    private static final double PASSIVE_HEALTH_PENALTY = -4.0D;   // -2 hearts (20 -> 16 HP)
    private static final double ACTIVE_HEALTH_MULTIPLIER = -0.5D; // Halves max health (16 -> 8 HP)
    private static final int ACTIVE_DURATION_TICKS = 20 * 60;     // 1 minute (1200 ticks)
    private static final int GLOWING_DURATION_TICKS = 200;        // 10 seconds (matches Spectral Arrow)
    private static final float KILL_ABSORPTION_BONUS = 2.0F;      // +1 heart stacking

    public static boolean isHangedOneCherub(PlayerEntity player) {
        return player.getCapability(RaceCapability.INSTANCE)
                .map(data -> data.getRace() == Race.CHERUB && data.getArchangel() == Archangel.THE_HANGED_ONE)
                .orElse(false);
    }

    /** Triggered via ArchangelAbilityRegistry when pressing the universal active keybind. */
    public static void activateAbility(ServerPlayerEntity player, IRaceData data) {
        if (data.isHangedOneActive()) {
            return;
        }

        data.setHangedOneActiveTicks(ACTIVE_DURATION_TICKS);
        applyActiveHealthModifier(player);
        player.addEffect(new EffectInstance(Effects.DAMAGE_BOOST, ACTIVE_DURATION_TICKS, 1));
        CherubNetwork.syncToTrackingAndSelf(player);
    }

    /** Immune to Slowness. */
    @SubscribeEvent
    public static void onPotionApplicable(PotionEvent.PotionApplicableEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide || !(entity instanceof PlayerEntity)) {
            return;
        }
        if (event.getPotionEffect().getEffect() == Effects.MOVEMENT_SLOWDOWN
                && isHangedOneCherub((PlayerEntity) entity)) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Hitting enemies inflicts Glowing. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntityLiving();
        if (target.level.isClientSide || event.getAmount() <= 0.0F) {
            return;
        }

        PlayerEntity attacker = resolveAttacker(event.getSource());
        if (attacker == null || attacker == target || !isHangedOneCherub(attacker)) {
            return;
        }

        target.addEffect(new EffectInstance(Effects.GLOWING, GLOWING_DURATION_TICKS, 0));
    }

    /** Killing enemies grants 1 heart (2.0F) of stacking absorption. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntityLiving();
        if (victim.level.isClientSide) {
            return;
        }

        PlayerEntity killer = resolveAttacker(event.getSource());
        if (!(killer instanceof ServerPlayerEntity) || killer == victim || !killer.isAlive()) {
            return;
        }
        if (!isHangedOneCherub(killer)) {
            return;
        }

        killer.setAbsorptionAmount(killer.getAbsorptionAmount() + KILL_ABSORPTION_BONUS);
    }

    /** Maintains max-health modifiers, Slowness cleanup, and active ability expiration. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayerEntity)) {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) event.player;
        IRaceData data = player.getCapability(RaceCapability.INSTANCE).orElse(null);
        boolean isHangedOne = data != null
                && data.getRace() == Race.CHERUB
                && data.getArchangel() == Archangel.THE_HANGED_ONE;

        if (!isHangedOne) {
            removeHealthModifier(player, PASSIVE_MAX_HEALTH_UUID);
            removeHealthModifier(player, ACTIVE_MAX_HEALTH_UUID);
            if (data != null && data.isHangedOneActive()) {
                data.setHangedOneActiveTicks(0);
                removeActiveStrengthIfMatching(player);
                CherubNetwork.syncToTrackingAndSelf(player);
            }
            return;
        }

        // 1. Strip any pre-existing Slowness (e.g., swapped Archangels while slowed)
        if (player.hasEffect(Effects.MOVEMENT_SLOWDOWN)) {
            player.removeEffect(Effects.MOVEMENT_SLOWDOWN);
        }

        // 2. Maintain passive -2 hearts (-4.0 HP) modifier
        ensureModifier(
                player,
                PASSIVE_MAX_HEALTH_UUID,
                "cherubmod_hanged_one_max_health",
                PASSIVE_HEALTH_PENALTY,
                AttributeModifier.Operation.ADDITION
        );

        // 3. Tick active ability (-50% max health + Strength II for 1 minute)
        int remaining = data.getHangedOneActiveTicks();
        if (remaining > 0) {
            applyActiveHealthModifier(player);
            int next = remaining - 1;
            data.setHangedOneActiveTicks(next);

            if (next == 0) {
                removeHealthModifier(player, ACTIVE_MAX_HEALTH_UUID);
                CherubNetwork.syncToTrackingAndSelf(player);
            }
        } else {
            removeHealthModifier(player, ACTIVE_MAX_HEALTH_UUID);
        }
    }

    private static void applyActiveHealthModifier(ServerPlayerEntity player) {
        ensureModifier(
                player,
                ACTIVE_MAX_HEALTH_UUID,
                "cherubmod_hanged_one_active_health",
                ACTIVE_HEALTH_MULTIPLIER,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    private static void ensureModifier(
            ServerPlayerEntity player,
            UUID uuid,
            String name,
            double amount,
            AttributeModifier.Operation operation
    ) {
        ModifiableAttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        if (maxHealth.getModifier(uuid) == null) {
            maxHealth.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    private static void removeHealthModifier(ServerPlayerEntity player, UUID uuid) {
        ModifiableAttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getModifier(uuid) != null) {
            maxHealth.removeModifier(uuid);
        }
    }

    private static void removeActiveStrengthIfMatching(ServerPlayerEntity player) {
        EffectInstance strength = player.getEffect(Effects.DAMAGE_BOOST);
        if (strength != null && strength.getAmplifier() == 1 && strength.getDuration() <= ACTIVE_DURATION_TICKS) {
            player.removeEffect(Effects.DAMAGE_BOOST);
        }
    }

    @Nullable
    private static PlayerEntity resolveAttacker(@Nullable DamageSource source) {
        return getPlayerEntity(source);
    }
}