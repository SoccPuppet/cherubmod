package org.lullaby.cherubmod.event;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldAbilityEvents {

    public static final double TARGET_ATTACK_SPEED = 0.8D;
    private static final double MIN_VALID_SPEED = 1.0E-4D;
    private static final double EPSILON = 1.0E-6D;
    public static final int STASIS_MAX_TICKS = 80;       // 4 seconds max stasis
    public static final int STASIS_COOLDOWN_TICKS = 200; // 10 seconds cooldown after exit

    private static final double STASIS_DRIFT_EPSILON_SQ = 0.04D; // 0.2 blocks
    private static final double AGGRO_CLEAR_RADIUS = 48.0D;

    private static final class StasisSnapshot {
        final RegistryKey<World> dimension;
        final double x;
        final double y;
        final double z;
        final int airSupply;
        final int remainingFireTicks;
        final CompoundNBT foodTag;
        final boolean hadNoGravity;

        StasisSnapshot(ServerPlayerEntity player) {
            this.dimension = player.level.dimension();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.airSupply = player.getAirSupply();
            this.remainingFireTicks = player.getRemainingFireTicks();
            this.foodTag = new CompoundNBT();
            player.getFoodData().addAdditionalSaveData(this.foodTag);
            this.hadNoGravity = player.isNoGravity();
        }
    }
    private static final Set<UUID> KNOWN_WORLD_CHERUBS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, StasisSnapshot> STASIS_SNAPSHOTS = new ConcurrentHashMap<>();

    private static final UUID WORLD_ATTACK_SPEED_UUID =
            UUID.fromString("91d4b8c2-3f7a-4b16-8c55-2e6f1a09d421");
    private static final UUID WORLD_ATTACK_DAMAGE_UUID =
            UUID.fromString("4e8a71d0-6b23-49c8-a104-7d3c9f52b814");

    public static boolean isInWorldStasis(PlayerEntity player) {
        IRaceData data = player.getCapability(RaceCapability.INSTANCE).orElse(null);
        return data != null
                && data.getRace() == Race.CHERUB
                && data.getArchangel() == Archangel.THE_WORLD
                && data.isWorldStasisActive();
    }

    public static void toggleStepOutOfTime(ServerPlayerEntity player, IRaceData data) {
        if (data.getRace() != Race.CHERUB || data.getArchangel() != Archangel.THE_WORLD) {
            return;
        }
        if (data.isWorldStasisActive()) {
            exitStasis(player, data, true);
            return;
        }
        if (data.getWorldStasisCooldownTicks() > 0) {
            return;
        }
        enterStasis(player, data);
    }

    private static void enterStasis(ServerPlayerEntity player, IRaceData data) {
        player.stopUsingItem();
        if (player.isPassenger()) {
            player.stopRiding();
        }
        if (player.isFallFlying()) {
            player.stopFallFlying();
        }

        STASIS_SNAPSHOTS.put(player.getUUID(), new StasisSnapshot(player));

        player.setNoGravity(true);
        player.setDeltaMovement(Vector3d.ZERO);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;

        clearNearbyMobTargeting(player);

        data.setWorldStasisTicks(STASIS_MAX_TICKS);
        CherubNetwork.syncToTrackingAndSelf(player);

        player.level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.BEACON_POWER_SELECT,
                SoundCategory.PLAYERS,
                0.7F,
                1.6F
        );
    }

    private static void exitStasis(ServerPlayerEntity player, IRaceData data, boolean startCooldown) {
        StasisSnapshot snapshot = STASIS_SNAPSHOTS.remove(player.getUUID());
        if (snapshot != null) {
            player.setNoGravity(snapshot.hadNoGravity);
        } else {
            player.setNoGravity(false);
        }

        player.setDeltaMovement(Vector3d.ZERO);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;

        data.setWorldStasisTicks(0);
        if (startCooldown) {
            data.setWorldStasisCooldownTicks(STASIS_COOLDOWN_TICKS);
        }
        CherubNetwork.syncToTrackingAndSelf(player);

        player.level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.BEACON_DEACTIVATE,
                SoundCategory.PLAYERS,
                0.6F,
                1.4F
        );
    }

    private static void clearNearbyMobTargeting(ServerPlayerEntity player) {
        for (MobEntity mob : player.level.getEntitiesOfClass(
                MobEntity.class,
                player.getBoundingBox().inflate(AGGRO_CLEAR_RADIUS)
        )) {
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
            if (mob.getLastHurtByMob() == player) {
                mob.setLastHurtByMob(null);
            }
        }
    }

    public static boolean isWorldCherub(PlayerEntity player) {
        IRaceData data = player.getCapability(RaceCapability.INSTANCE).orElse(null);
        return data != null
                && data.getRace() == Race.CHERUB
                && data.getArchangel() == Archangel.THE_WORLD;
    }

    public static boolean isBlockedTimeEffect(Effect effect) {
        return effect == Effects.MOVEMENT_SPEED
                || effect == Effects.MOVEMENT_SLOWDOWN
                || effect == Effects.DIG_SPEED
                || effect == Effects.DIG_SLOWDOWN;
    }

    /**
     * Computes the DPS-preserving damage multiplier (naturalAttackSpeed / 0.8).
     * Works on both server and client because synced AttributeModifiers retain their UUIDs.
     */
    public static float getDpsDamageMultiplier(PlayerEntity player) {
        ModifiableAttributeInstance attackSpeedAttr = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeedAttr == null) {
            return 1.0F;
        }
        double naturalSpeed = computeNaturalAttackSpeed(attackSpeedAttr);
        if (naturalSpeed <= MIN_VALID_SPEED) {
            return 1.0F;
        }
        return (float) (naturalSpeed / TARGET_ATTACK_SPEED);
    }

    /** Immune to Speed, Slowness, Haste, and Mining Fatigue. */
    @SubscribeEvent
    public static void onPotionApplicable(PotionEvent.PotionApplicableEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (!(entity instanceof PlayerEntity)) {
            return;
        }
        PlayerEntity player = (PlayerEntity) entity;
        if (isInWorldStasis(player)) {
            event.setResult(Event.Result.DENY);
            return;
        }
        EffectInstance effect = event.getPotionEffect();
        if (isBlockedTimeEffect(effect.getEffect()) && isWorldCherub(player)) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Immediately recalculate fixed 0.8 attack speed & DPS damage when swapping gear. */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntityLiving();
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            if (isWorldCherub(player)) {
                syncWorldAttributes(player);
            }
        }
    }

    /** Strips any pre-existing/force-applied blocked effects and keeps attributes synced. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayerEntity)) {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) event.player;
        IRaceData data = player.getCapability(RaceCapability.INSTANCE).orElse(null);
        boolean isWorld = data != null
                && data.getRace() == Race.CHERUB
                && data.getArchangel() == Archangel.THE_WORLD;

        // Refresh multiplayer sleep vote immediately whenever a player enters/leaves THE_WORLD
        UUID uuid = player.getUUID();
        boolean wasKnownWorld = KNOWN_WORLD_CHERUBS.contains(uuid);
        if (isWorld != wasKnownWorld) {
            if (isWorld) {
                KNOWN_WORLD_CHERUBS.add(uuid);
            } else {
                KNOWN_WORLD_CHERUBS.remove(uuid);
            }
            ((ServerWorld) player.level).updateSleepingPlayerList();
        }

        if (!isWorld) {
            removeWorldModifiers(player);
            if (data != null && (data.isWorldStasisActive() || STASIS_SNAPSHOTS.containsKey(uuid))) {
                exitStasis(player, data, false);
            }
            return;
        }

        stripBlockedEffects(player);
        syncWorldAttributes(player);
        tickWorldStasis(player, data);
    }

    private static void tickWorldStasis(ServerPlayerEntity player, IRaceData data) {
        int stasisTicks = data.getWorldStasisTicks();
        if (stasisTicks > 0) {
            if (!player.isAlive() || player.isSpectator()) {
                exitStasis(player, data, false);
                return;
            }

            StasisSnapshot snapshot = STASIS_SNAPSHOTS.get(player.getUUID());
            if (snapshot == null || !snapshot.dimension.equals(player.level.dimension())) {
                exitStasis(player, data, true);
                return;
            }

            // Lock position/velocity and freeze oxygen, burn timer, and hunger/saturation
            player.setNoGravity(true);
            player.setDeltaMovement(Vector3d.ZERO);
            player.fallDistance = 0.0F;
            player.setAirSupply(snapshot.airSupply);
            player.setRemainingFireTicks(snapshot.remainingFireTicks);
            player.getFoodData().readAdditionalSaveData(snapshot.foodTag);

            double dx = player.getX() - snapshot.x;
            double dy = player.getY() - snapshot.y;
            double dz = player.getZ() - snapshot.z;
            if (dx * dx + dy * dy + dz * dz > STASIS_DRIFT_EPSILON_SQ) {
                player.connection.teleport(snapshot.x, snapshot.y, snapshot.z, player.yRot, player.xRot);
            }

            int next = stasisTicks - 1;
            if (next <= 0) {
                exitStasis(player, data, true);
            } else {
                data.setWorldStasisTicks(next);
            }
            return;
        }

        int cooldown = data.getWorldStasisCooldownTicks();
        if (cooldown > 0) {
            int next = cooldown - 1;
            data.setWorldStasisCooldownTicks(next);
            if (next == 0) {
                CherubNetwork.syncToTrackingAndSelf(player);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntityLiving() instanceof PlayerEntity
                && !event.getSource().isBypassInvul()
                && isInWorldStasis((PlayerEntity) event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntityLiving() instanceof PlayerEntity
                && !event.getSource().isBypassInvul()
                && isInWorldStasis((PlayerEntity) event.getEntityLiving())) {
            event.setAmount(0.0F);
            event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (event.getEntityLiving() instanceof PlayerEntity
                && isInWorldStasis((PlayerEntity) event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }
    @SubscribeEvent
    public static void onLivingSetAttackTarget(LivingSetAttackTargetEvent event) {
        if (event.getTarget() instanceof PlayerEntity
                && event.getEntityLiving() instanceof MobEntity
                && isInWorldStasis((PlayerEntity) event.getTarget())) {
            ((MobEntity) event.getEntityLiving()).setTarget(null);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isInWorldStasis(event.getPlayer())) {
            event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isInWorldStasis(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isInWorldStasis(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isInWorldStasis(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isInWorldStasis(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isInWorldStasis(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntityLiving() instanceof PlayerEntity
                && isInWorldStasis((PlayerEntity) event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (isInWorldStasis(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        if (isInWorldStasis(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerEntity entity = event.getPlayer();
        UUID uuid = entity.getUUID();
        KNOWN_WORLD_CHERUBS.remove(uuid);
        if (!(entity instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) entity;
        IRaceData data = player.getCapability(RaceCapability.INSTANCE).orElse(null);
        if (data != null && (data.isWorldStasisActive() || STASIS_SNAPSHOTS.containsKey(uuid))) {
            StasisSnapshot snapshot = STASIS_SNAPSHOTS.remove(uuid);
            player.setNoGravity(snapshot != null && snapshot.hadNoGravity);
            data.setWorldStasisTicks(0);
            data.setWorldStasisCooldownTicks(STASIS_COOLDOWN_TICKS);
        }
    }

    public static void syncWorldAttributes(ServerPlayerEntity player) {
        ModifiableAttributeInstance attackSpeedAttr = player.getAttribute(Attributes.ATTACK_SPEED);
        ModifiableAttributeInstance attackDamageAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackSpeedAttr == null || attackDamageAttr == null) {
            return;
        }

        double naturalSpeed = computeNaturalAttackSpeed(attackSpeedAttr);
        if (naturalSpeed <= MIN_VALID_SPEED) {
            removeModifierIfPresent(attackSpeedAttr, WORLD_ATTACK_SPEED_UUID);
            removeModifierIfPresent(attackDamageAttr, WORLD_ATTACK_DAMAGE_UUID);
            return;
        }

        // naturalSpeed * (1 + speedMod) = 0.8  ->  speedMod = (0.8 / naturalSpeed) - 1
        double speedModAmount = (TARGET_ATTACK_SPEED / naturalSpeed) - 1.0D;
        // naturalDamage * (1 + damageMod) * 0.8 = naturalDamage * naturalSpeed
        double damageModAmount = (naturalSpeed / TARGET_ATTACK_SPEED) - 1.0D;

        updateMultiplyTotalModifier(
                attackSpeedAttr,
                WORLD_ATTACK_SPEED_UUID,
                "cherubmod_world_attack_speed",
                speedModAmount
        );
        updateMultiplyTotalModifier(
                attackDamageAttr,
                WORLD_ATTACK_DAMAGE_UUID,
                "cherubmod_world_dps_damage",
                damageModAmount
        );
    }

    private static void removeWorldModifiers(ServerPlayerEntity player) {
        ModifiableAttributeInstance attackSpeedAttr = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeedAttr != null) {
            removeModifierIfPresent(attackSpeedAttr, WORLD_ATTACK_SPEED_UUID);
        }
        ModifiableAttributeInstance attackDamageAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamageAttr != null) {
            removeModifierIfPresent(attackDamageAttr, WORLD_ATTACK_DAMAGE_UUID);
        }
    }

    private static void stripBlockedEffects(ServerPlayerEntity player) {
        if (player.hasEffect(Effects.MOVEMENT_SPEED)) {
            player.removeEffect(Effects.MOVEMENT_SPEED);
        }
        if (player.hasEffect(Effects.MOVEMENT_SLOWDOWN)) {
            player.removeEffect(Effects.MOVEMENT_SLOWDOWN);
        }
        if (player.hasEffect(Effects.DIG_SPEED)) {
            player.removeEffect(Effects.DIG_SPEED);
        }
        if (player.hasEffect(Effects.DIG_SLOWDOWN)) {
            player.removeEffect(Effects.DIG_SLOWDOWN);
        }
    }

    private static double computeNaturalAttackSpeed(ModifiableAttributeInstance attackSpeedAttr) {
        double base = attackSpeedAttr.getBaseValue();
        for (AttributeModifier mod : attackSpeedAttr.getModifiers(AttributeModifier.Operation.ADDITION)) {
            base += mod.getAmount();
        }

        double value = base;
        for (AttributeModifier mod : attackSpeedAttr.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE)) {
            value += base * mod.getAmount();
        }

        for (AttributeModifier mod : attackSpeedAttr.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL)) {
            if (!WORLD_ATTACK_SPEED_UUID.equals(mod.getId())) {
                value *= 1.0D + mod.getAmount();
            }
        }

        return attackSpeedAttr.getAttribute().sanitizeValue(value);
    }

    private static void updateMultiplyTotalModifier(
            ModifiableAttributeInstance attr,
            UUID uuid,
            String name,
            double targetAmount
    ) {
        AttributeModifier existing = attr.getModifier(uuid);
        if (Math.abs(targetAmount) < EPSILON) {
            if (existing != null) {
                attr.removeModifier(uuid);
            }
            return;
        }
        if (existing != null && Math.abs(existing.getAmount() - targetAmount) < EPSILON) {
            return;
        }
        if (existing != null) {
            attr.removeModifier(uuid);
        }
        attr.addTransientModifier(new AttributeModifier(
                uuid,
                name,
                targetAmount,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        ));
    }

    private static void removeModifierIfPresent(ModifiableAttributeInstance attr, UUID uuid) {
        if (attr.getModifier(uuid) != null) {
            attr.removeModifier(uuid);
        }
    }
}