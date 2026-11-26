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
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.compat.PehkuiCompat;
import org.lullaby.cherubmod.network.CherubNetwork;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlushAbilityEvents {

    private static final UUID PLUSH_MAX_HEALTH_UUID =
            UUID.fromString("e8a14c2d-9b30-4f76-a52e-3c19d74b0811");

    private static final double PLUSH_HEALTH_PENALTY = -6.0D; // 20 -> 14 HP (7 hearts)
    private static final int RESISTANCE_DURATION_TICKS = 320; // 16s; refreshed at <= 11s so icon never blinks
    private static final int RESISTANCE_REFRESH_THRESHOLD = 220;
    public static final double PLUSH_KNOCKBACK_MULTIPLIER = 6.0;
    private static final double VANILLA_VELOCITY_PACKET_CAP = 3.9D;

    public static boolean isPlush(PlayerEntity player) {
        return player.getCapability(RaceCapability.INSTANCE)
                .map(data -> data.getRace() == Race.PLUSH)
                .orElse(false);
    }

    /** Call from RaceEvents on login, respawn, and dimension change, as well as /switched off. */
    public static void syncPlushState(ServerPlayerEntity player) {
        boolean plush = isPlush(player);
        PehkuiCompat.updatePlushScale(player, plush);

        if (plush) {
            ensureMaxHealthModifier(player);
            maintainResistance(player);
        } else {
            removeMaxHealthModifier(player);
            clearPlushResistance(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayerEntity)) {
            return;
        }
        syncPlushState((ServerPlayerEntity) event.player);
    }

    /** Immune to fall distance calculation, sounds, and fall damage. */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity instanceof PlayerEntity && isPlush((PlayerEntity) entity)) {
            event.setDistance(0.0F);
            event.setDamageMultiplier(0.0F);
            event.setCanceled(true);
        }
    }

    /** Immune to both fall and kinetic (Elytra wall impact) damage, preventing hurt flash/armor wear. */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide || !(entity instanceof PlayerEntity) || !isPlush((PlayerEntity) entity)) {
            return;
        }
        DamageSource source = event.getSource();
        if (isFallOrKinetic(source)) {
            event.setCanceled(true);
        }
    }

    /** Take double damage from all fire sources (fire, burning, lava, magma blocks, fireballs). */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide || !(entity instanceof PlayerEntity) || !isPlush((PlayerEntity) entity)) {
            return;
        }
        if (event.getSource().isFire()) {
            event.setAmount(event.getAmount() * 2.0F);
        }
    }

    /** Take massive knockback */
    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide || !(entity instanceof PlayerEntity) || !isPlush((PlayerEntity) entity)) {
            return;
        }
        event.setStrength((float) (event.getStrength() * PLUSH_KNOCKBACK_MULTIPLIER));
    }

    public static void sendUncappedVelocityIfNeeded(ServerPlayerEntity player) {
        Vector3d motion = player.getDeltaMovement();
        if (Math.abs(motion.x) > VANILLA_VELOCITY_PACKET_CAP
                || Math.abs(motion.y) > VANILLA_VELOCITY_PACKET_CAP
                || Math.abs(motion.z) > VANILLA_VELOCITY_PACKET_CAP) {
            CherubNetwork.sendPlushVelocity(player, motion.x, motion.y, motion.z);
        }
    }

    private static boolean isFallOrKinetic(DamageSource source) {
        String id = source.getMsgId();
        return source == DamageSource.FALL
                || source == DamageSource.FLY_INTO_WALL
                || "fall".equals(id)
                || "flyIntoWall".equals(id);
    }

    private static void ensureMaxHealthModifier(ServerPlayerEntity player) {
        ModifiableAttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        if (maxHealth.getModifier(PLUSH_MAX_HEALTH_UUID) == null) {
            maxHealth.addTransientModifier(new AttributeModifier(
                    PLUSH_MAX_HEALTH_UUID,
                    "cherubmod_plush_max_health",
                    PLUSH_HEALTH_PENALTY,
                    AttributeModifier.Operation.ADDITION
            ));
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void removeMaxHealthModifier(ServerPlayerEntity player) {
        ModifiableAttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getModifier(PLUSH_MAX_HEALTH_UUID) != null) {
            maxHealth.removeModifier(PLUSH_MAX_HEALTH_UUID);
        }
    }

    private static void maintainResistance(ServerPlayerEntity player) {
        EffectInstance current = player.getEffect(Effects.DAMAGE_RESISTANCE);
        if (current == null || (current.getAmplifier() == 0 && current.getDuration() <= RESISTANCE_REFRESH_THRESHOLD)) {
            player.addEffect(new EffectInstance(
                    Effects.DAMAGE_RESISTANCE,
                    RESISTANCE_DURATION_TICKS,
                    0,
                    true,  // ambient
                    false, // no distracting particles
                    true   // show HUD icon
            ));
        }
    }

    private static void clearPlushResistance(ServerPlayerEntity player) {
        EffectInstance current = player.getEffect(Effects.DAMAGE_RESISTANCE);
        if (current != null
                && current.getAmplifier() == 0
                && current.isAmbient()
                && current.getDuration() <= RESISTANCE_DURATION_TICKS) {
            player.removeEffect(Effects.DAMAGE_RESISTANCE);
        }
    }
}