package org.lullaby.cherubmod.event;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.Archangel;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MoonAbilityEvents {

    private static final int SUN_WEAKNESS_DURATION = 220; // > 200 ticks prevents HUD icon blinking
    private static final int SUN_WEAKNESS_REFRESH_THRESHOLD = 210;

    // Tracks players currently receiving Weakness I from Moon sunlight exposure
    private static final Set<UUID> SUN_WEAKNESS_ACTIVE = ConcurrentHashMap.newKeySet();

    public static boolean isMoonCherub(PlayerEntity player) {
        return player.getCapability(RaceCapability.INSTANCE)
                .map(data -> data.getRace() == Race.CHERUB && data.getArchangel() == Archangel.THE_MOON)
                .orElse(false);
    }

    public static boolean isDaytime(World world) {
        if (!world.dimensionType().hasSkyLight() || world.dimensionType().hasFixedTime()) {
            return false;
        }
        if (!world.isClientSide) {
            return world.isDay();
        }
        // ClientWorld never calls updateSkyBrightness(), leaving skyDarken stuck at 0
        double rainFactor = 1.0D - (double) (world.getRainLevel(1.0F) * 5.0F) / 16.0D;
        double thunderFactor = 1.0D - (double) (world.getThunderLevel(1.0F) * 5.0F) / 16.0D;
        double sunFactor = 0.5D + 2.0D * MathHelper.clamp(
                MathHelper.cos(world.getTimeOfDay(1.0F) * ((float) Math.PI * 2.0F)),
                -0.25D,
                0.25D
        );
        int skyDarken = (int) ((1.0D - sunFactor * rainFactor * thunderFactor) * 11.0D);
        return skyDarken < 4;
    }

    public static boolean isExposedToDaytimeSky(PlayerEntity player) {
        World world = player.level;
        if (!isDaytime(world)) {
            return false;
        }
        BlockPos eyePos = new BlockPos(player.getX(), player.getEyeY(), player.getZ());
        return world.canSeeSky(eyePos);
    }

    /**
     * Called from LivingEntityMixin at HEAD of LivingEntity#hurt.
     * Zeroes incoming damage from Moon Cherubs before lastHurt is recorded while
     * letting LivingEntity#hurt complete and apply normal + enchanted knockback.
     */
    public static float adjustIncomingDamage(LivingEntity target, DamageSource source, float amount) {
        PlayerEntity attacker = resolveAttacker(source);
        if (attacker != null && attacker != target && isMoonCherub(attacker)) {
            return 0.0F;
        }
        return amount;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntityLiving();
        if (target.level.isClientSide) {
            return;
        }
        PlayerEntity attacker = resolveAttacker(event.getSource());
        if (attacker != null && attacker != target && isMoonCherub(attacker)) {
            event.setAmount(0.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntityLiving();
        if (target.level.isClientSide) {
            return;
        }
        PlayerEntity attacker = resolveAttacker(event.getSource());
        if (attacker != null && attacker != target && isMoonCherub(attacker)) {
            event.setAmount(0.0F);
        }
    }

    @SubscribeEvent
    public static void onPotionApplicable(PotionEvent.PotionApplicableEvent event) {
        if (!(event.getEntityLiving() instanceof PlayerEntity)) {
            return;
        }
        EffectInstance effect = event.getPotionEffect();
        if (effect.getEffect() == Effects.BLINDNESS) {
            PlayerEntity player = (PlayerEntity) event.getEntityLiving();
            if (isMoonCherub(player)) {
                event.setResult(Event.Result.DENY);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        PlayerEntity player = event.player;
        if (player.level.isClientSide) {
            return;
        }

        UUID uuid = player.getUUID();
        if (!player.isAlive() || player.isSpectator() || !isMoonCherub(player)) {
            clearSunWeaknessIfOwned(player, uuid);
            return;
        }

        // Strip any pre-existing or force-applied Blindness
        if (player.hasEffect(Effects.BLINDNESS)) {
            player.removeEffect(Effects.BLINDNESS);
        }

        if (isExposedToDaytimeSky(player)) {
            SUN_WEAKNESS_ACTIVE.add(uuid);
            EffectInstance current = player.getEffect(Effects.WEAKNESS);
            if (current == null || (current.getAmplifier() == 0 && current.getDuration() <= SUN_WEAKNESS_REFRESH_THRESHOLD)) {
                // ambient = true, showParticles = false, showIcon = true
                player.addEffect(new EffectInstance(Effects.WEAKNESS, SUN_WEAKNESS_DURATION, 0, true, false, true));
            }
        } else {
            clearSunWeaknessIfOwned(player, uuid);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SUN_WEAKNESS_ACTIVE.remove(event.getPlayer().getUUID());
    }

    private static void clearSunWeaknessIfOwned(PlayerEntity player, UUID uuid) {
        if (!SUN_WEAKNESS_ACTIVE.remove(uuid)) {
            return;
        }
        EffectInstance current = player.getEffect(Effects.WEAKNESS);
        if (current != null && current.getAmplifier() == 0 && current.getDuration() <= SUN_WEAKNESS_DURATION) {
            player.removeEffect(Effects.WEAKNESS);
        }
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