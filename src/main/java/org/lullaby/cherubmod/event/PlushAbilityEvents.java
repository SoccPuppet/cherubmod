package org.lullaby.cherubmod.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.IRaceData;
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
    private static final int RESISTANCE_DURATION_TICKS = 320;
    private static final int RESISTANCE_REFRESH_THRESHOLD = 220;
    public static final double PLUSH_KNOCKBACK_MULTIPLIER = 5.0;
    private static final double VANILLA_VELOCITY_PACKET_CAP = 3.9D;

    public static final int WOOL_STUFFED_DURATION_TICKS = 600; // 30 seconds
    private static final String TAG_PLUSH_STUFFED_TICKS = "cherubmod_plush_stuffed_ticks";

    public static boolean isPlush(PlayerEntity player) {
        IRaceData data = player.getCapability(RaceCapability.INSTANCE).orElse(null);
        return data != null && data.getRace() == Race.PLUSH;
    }

    /** Call from RaceEvents on login, respawn, and dimension change, as well as /switched off. */
    public static void syncPlushState(ServerPlayerEntity player) {
        boolean plush = isPlush(player);
        int stuffedTicks = getStuffedTicks(player);

        if (!plush && stuffedTicks > 0) {
            setStuffedTicks(player, 0);
            stuffedTicks = 0;
        }

        PehkuiCompat.updatePlushScale(player, plush, plush && stuffedTicks > 0);

        if (plush) {
            ensureMaxHealthModifier(player);
            maintainResistance(player);
            if (player.getAirSupply() < player.getMaxAirSupply()) {
                player.setAirSupply(player.getMaxAirSupply());
            }
        } else {
            removeMaxHealthModifier(player);
            clearPlushResistance(player);
        }
    }

    public static void sendUncappedVelocityIfNeeded(ServerPlayerEntity player) {
        Vector3d motion = player.getDeltaMovement();
        if (Math.abs(motion.x) > VANILLA_VELOCITY_PACKET_CAP
                || Math.abs(motion.y) > VANILLA_VELOCITY_PACKET_CAP
                || Math.abs(motion.z) > VANILLA_VELOCITY_PACKET_CAP) {
            CherubNetwork.sendPlushVelocity(player, motion.x, motion.y, motion.z);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) event.player;
        int stuffedTicks = getStuffedTicks(player);
        if (stuffedTicks > 0 && isPlush(player)) {
            setStuffedTicks(player, stuffedTicks - 1);
        }
        syncPlushState(player);
    }

    /** Clear any active wool growth timer on death so the Plush respawns at 0.9 blocks. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (!entity.level.isClientSide && entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            if (getStuffedTicks(player) > 0) {
                setStuffedTicks(player, 0);
            }
        }
    }

    /** Other players can right-click a Plush with any wool to fully heal them for 30s of size 1.1 + Slowness I. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        PlayerEntity healer = event.getPlayer();
        Entity target = event.getTarget();

        if (!(target instanceof PlayerEntity) || target == healer) {
            return;
        }

        PlayerEntity plushTarget = (PlayerEntity) target;
        if (!plushTarget.isAlive() || plushTarget.isSpectator() || !isPlush(plushTarget)) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !ItemTags.WOOL.contains(stack.getItem())) {
            return;
        }

        if (!healer.level.isClientSide && plushTarget instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverTarget = (ServerPlayerEntity) plushTarget;

            if (!healer.abilities.instabuild) {
                stack.shrink(1);
            }

            float missingHealth = serverTarget.getMaxHealth() - serverTarget.getHealth();
            if (missingHealth > 0.0F) {
                serverTarget.heal(missingHealth);
            }

            setStuffedTicks(serverTarget, WOOL_STUFFED_DURATION_TICKS);
            PehkuiCompat.updatePlushScale(serverTarget, true, true);

            serverTarget.addEffect(new EffectInstance(
                    Effects.MOVEMENT_SLOWDOWN,
                    WOOL_STUFFED_DURATION_TICKS,
                    0,     // Slowness I
                    false, // not ambient
                    true,  // show particles
                    true   // show HUD icon
            ));

            serverTarget.level.playSound(
                    null,
                    serverTarget.getX(), serverTarget.getY(), serverTarget.getZ(),
                    SoundEvents.WOOL_PLACE,
                    SoundCategory.PLAYERS,
                    0.9F,
                    1.0F + serverTarget.level.random.nextFloat() * 0.2F
            );

            if (serverTarget.level instanceof ServerWorld) {
                double spreadX = Math.max(0.25D, serverTarget.getBbWidth() * 0.5D);
                double spreadY = serverTarget.getBbHeight() * 0.25D;
                ((ServerWorld) serverTarget.level).sendParticles(
                        ParticleTypes.HEART,
                        serverTarget.getX(),
                        serverTarget.getY() + serverTarget.getBbHeight() * 0.5D + 0.5D,
                        serverTarget.getZ(),
                        7,
                        spreadX,
                        spreadY,
                        spreadX,
                        0.02D
                );
            }
        }

        event.setCanceled(true);
        event.setCancellationResult(ActionResultType.sidedSuccess(healer.level.isClientSide));
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

    /** Immune to fall, kinetic (Elytra wall impact), suffocation, and drowning damage. */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide || !(entity instanceof PlayerEntity) || !isPlush((PlayerEntity) entity)) {
            return;
        }
        if (isImmuneDamage(event.getSource())) {
            event.setCanceled(true);
        }
    }

    /** Take double damage from all fire sources. */
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

    /** Take triple knockback from combat and projectiles. */
    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity.level.isClientSide || !(entity instanceof PlayerEntity) || !isPlush((PlayerEntity) entity)) {
            return;
        }
        event.setStrength(event.getStrength() * 3.0F);
    }

    private static boolean isImmuneDamage(DamageSource source) {
        String id = source.getMsgId();
        return source == DamageSource.FALL
                || source == DamageSource.FLY_INTO_WALL
                || source == DamageSource.IN_WALL
                || source == DamageSource.DROWN
                || "fall".equals(id)
                || "flyIntoWall".equals(id)
                || "inWall".equals(id)
                || "drown".equals(id);
    }

    private static CompoundNBT getPersistedTag(PlayerEntity player) {
        CompoundNBT entityData = player.getPersistentData();
        CompoundNBT persisted = entityData.getCompound(PlayerEntity.PERSISTED_NBT_TAG);
        if (!entityData.contains(PlayerEntity.PERSISTED_NBT_TAG)) {
            entityData.put(PlayerEntity.PERSISTED_NBT_TAG, persisted);
        }
        return persisted;
    }

    private static int getStuffedTicks(PlayerEntity player) {
        return getPersistedTag(player).getInt(TAG_PLUSH_STUFFED_TICKS);
    }

    private static void setStuffedTicks(PlayerEntity player, int ticks) {
        getPersistedTag(player).putInt(TAG_PLUSH_STUFFED_TICKS, Math.max(0, ticks));
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
                    true,
                    false,
                    true
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