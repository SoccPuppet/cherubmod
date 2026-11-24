package org.lullaby.cherubmod.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.CombatRules;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.Archangel;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.damage.StrengthPunchDamageSource;
import org.lullaby.cherubmod.network.CherubNetwork;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StrengthAbilityEvents {

    public static final float CROUCH_AOE_RADIUS = 4.0F;
    private static final double CROUCH_AOE_RADIUS_SQ = CROUCH_AOE_RADIUS * CROUCH_AOE_RADIUS;

    private static final Map<UUID, Float> PENDING_CRIT_MULTIPLIERS = new ConcurrentHashMap<>();

    public static boolean isStrengthCherub(PlayerEntity player) {
        return player.getCapability(RaceCapability.INSTANCE)
                .map(data -> data.getRace() == Race.CHERUB && data.getArchangel() == Archangel.STRENGTH)
                .orElse(false);
    }

    public static boolean isStrengthPunch(PlayerEntity attacker) {
        return attacker.getMainHandItem().isEmpty() && isStrengthCherub(attacker);
    }

    public static float consumePendingCritMultiplier(PlayerEntity attacker) {
        if (attacker.level.isClientSide) {
            return 1.0F;
        }
        Float mult = PENDING_CRIT_MULTIPLIERS.remove(attacker.getUUID());
        return mult != null ? mult : 1.0F;
    }

    public static void clearPendingCritMultiplier(PlayerEntity attacker) {
        if (!attacker.level.isClientSide) {
            PENDING_CRIT_MULTIPLIERS.remove(attacker.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_CRIT_MULTIPLIERS.remove(event.getPlayer().getUUID());
    }

    // Capture the final critical hit multiplier right before PlayerEntity#attack constructs the DamageSource
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCriticalHit(CriticalHitEvent event) {
        PlayerEntity player = event.getPlayer();
        if (player == null || player.level.isClientSide || !isStrengthPunch(player)) {
            return;
        }

        boolean isCrit = event.getResult() == Event.Result.ALLOW
                || (event.isVanillaCritical() && event.getResult() == Event.Result.DEFAULT);
        float modifier = isCrit ? Math.max(1.0F, event.getDamageModifier()) : 1.0F;
        PENDING_CRIT_MULTIPLIERS.put(player.getUUID(), modifier);
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntityLiving().level.isClientSide) {
            return;
        }
        if (event.getSource() instanceof StrengthPunchDamageSource
                && event.getEntityLiving().invulnerableTime > 10) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntityLiving();
        if (target.level.isClientSide) {
            return;
        }

        DamageSource source = event.getSource();

        if (source instanceof StrengthPunchDamageSource) {
            StrengthPunchDamageSource strengthSource = (StrengthPunchDamageSource) source;
            float damageScale = strengthSource.getDamageScale();

            float rawPunch = (7.0F + 0.05F * target.getHealth()) * damageScale;
            float armor = (float) target.getArmorValue();
            float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

            float spall = 0.0F;
            if (armor > 0.0F) {
                float afterArmor = CombatRules.getDamageAfterAbsorb(rawPunch, armor, toughness);
                float blocked = Math.max(0.0F, rawPunch - afterArmor);
                spall = 1.25F * blocked + 0.25F * armor * damageScale;
            }

            strengthSource.recordImpact(rawPunch, armor, spall, target.getAbsorptionAmount());
            event.setAmount(rawPunch);
            return;
        }

        // All non-punch damage dealt by a Strength Cherub is capped at 1.0 (excluding self-damage)
        PlayerEntity attacker = resolveAttacker(source);
        if (attacker != null && attacker != target && isStrengthCherub(attacker)) {
            if (event.getAmount() > 1.0F) {
                event.setAmount(1.0F);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntityLiving();
        if (!(target.level instanceof ServerWorld)) {
            return;
        }

        DamageSource source = event.getSource();

        if (source instanceof StrengthPunchDamageSource) {
            StrengthPunchDamageSource strengthSource = (StrengthPunchDamageSource) source;
            if (!strengthSource.isImpactRecorded()) {
                return;
            }

            ServerWorld world = (ServerWorld) target.level;
            float postMitigationPunch = event.getAmount();
            float absorbed = Math.max(0.0F, strengthSource.getPreHitAbsorption() - target.getAbsorptionAmount());
            float effectivePunch = postMitigationPunch + absorbed;
            float spall = strengthSource.getSpallDamage();
            float armor = strengthSource.getTargetArmor();
            boolean spallDominant = spall > effectivePunch;

            strengthSource.setSpallDominant(spallDominant);
            event.setAmount(postMitigationPunch + spall);

            if (armor > 0.0F) {
                if (target instanceof PlayerEntity) {
                    ((PlayerEntity) target).inventory.hurtArmor(strengthSource, strengthSource.getRawPunch());
                }

                double midY = target.getY() + target.getBbHeight() * 0.5D;

                world.playSound(
                        null,
                        target.getX(), midY, target.getZ(),
                        SoundEvents.SHIELD_BREAK,
                        SoundCategory.PLAYERS,
                        0.85F,
                        0.95F + world.random.nextFloat() * 0.15F
                );

                if (spallDominant) {
                    world.playSound(
                            null,
                            target.getX(), midY, target.getZ(),
                            SoundEvents.ANVIL_PLACE,
                            SoundCategory.PLAYERS,
                            0.75F,
                            1.25F + world.random.nextFloat() * 0.2F
                    );

                    ImpactGeometry impact = computePunchImpact(strengthSource, target);
                    float entityScale = computeEntitySizeScale(target);
                    CherubNetwork.sendSpallParticles(
                            target,
                            impact.hitPos.x, impact.hitPos.y, impact.hitPos.z,
                            impact.direction.x, impact.direction.y, impact.direction.z,
                            entityScale
                    );
                }
            }

            // Crouch AoE slam: only triggered by the primary punch
            if (strengthSource.isCrouchSlam() && !strengthSource.isShockwaveSecondary()) {
                triggerCrouchShockwave(world, strengthSource, target);
            }
            return;
        }

        // Final post-mitigation safety clamp for any non-punch Strength Cherub damage
        PlayerEntity attacker = resolveAttacker(source);
        if (attacker != null && attacker != target && isStrengthCherub(attacker)) {
            if (event.getAmount() > 1.0F) {
                event.setAmount(1.0F);
            }
        }
    }

    private static void triggerCrouchShockwave(
            ServerWorld world,
            StrengthPunchDamageSource primarySource,
            LivingEntity primaryTarget
    ) {
        if (!(primarySource.getEntity() instanceof PlayerEntity)) {
            return;
        }
        PlayerEntity attacker = (PlayerEntity) primarySource.getEntity();

        // Ground block-break shockwave particles in a 4-block radius around the punched entity
        CherubNetwork.sendShockwaveParticles(
                primaryTarget,
                primaryTarget.getX(),
                primaryTarget.getY(),
                primaryTarget.getZ(),
                CROUCH_AOE_RADIUS
        );

        Vector3d epicenter = primaryTarget.getBoundingBox().getCenter();
        AxisAlignedBB searchBox = primaryTarget.getBoundingBox().inflate(CROUCH_AOE_RADIUS);
        List<LivingEntity> nearby = world.getEntitiesOfClass(LivingEntity.class, searchBox, candidate ->
                candidate != primaryTarget
                        && candidate != attacker
                        && candidate.isAlive()
                        && !candidate.isSpectator()
                        && !(candidate instanceof ArmorStandEntity && ((ArmorStandEntity) candidate).isMarker())
                        && (!(candidate instanceof PlayerEntity) || attacker.canHarmPlayer((PlayerEntity) candidate))
                        && candidate.distanceToSqr(primaryTarget) <= CROUCH_AOE_RADIUS_SQ
        );

        for (LivingEntity secondary : nearby) {
            StrengthPunchDamageSource secondarySource = StrengthPunchDamageSource.createShockwave(
                    attacker,
                    1,
                    primarySource.getCrouchMultiplier(),
                    epicenter
            );
            secondary.hurt(secondarySource, 1.0F);
        }
    }

    private static ImpactGeometry computePunchImpact(StrengthPunchDamageSource source, LivingEntity target) {
        AxisAlignedBB box = target.getBoundingBox();
        Vector3d targetCenter = box.getCenter();

        // Secondary shockwave hit: radiates outward from the primary punched target's center
        if (source.isShockwaveSecondary() && source.getShockwaveOrigin() != null) {
            Vector3d origin = source.getShockwaveOrigin();
            Optional<Vector3d> hit = box.clip(origin, targetCenter);
            Vector3d hitPos = hit.orElseGet(() -> clampToBox(origin, box));
            Vector3d dir = targetCenter.subtract(origin);
            Vector3d axis = dir.lengthSqr() > 1.0E-4D ? dir.normalize() : new Vector3d(0.0D, 1.0D, 0.0D);
            return new ImpactGeometry(hitPos, axis);
        }

        // Primary punch: raycast from attacker's eye along look vector onto the target's hitbox
        Entity attacker = source.getEntity();
        if (attacker != null) {
            Vector3d eyePos = attacker.getEyePosition(1.0F);
            Vector3d look = attacker.getLookAngle().normalize();
            double rayLength = eyePos.distanceTo(targetCenter) + target.getBbWidth() + target.getBbHeight() + 4.0D;

            Optional<Vector3d> hit = box.clip(eyePos, eyePos.add(look.scale(rayLength)));
            if (hit.isPresent()) {
                return new ImpactGeometry(hit.get(), look);
            }
            if (box.contains(eyePos)) {
                return new ImpactGeometry(eyePos, look);
            }

            // Fallback if target moved slightly between client swing and server tick:
            // project the closest point along the look ray onto the hitbox surface
            double t = Math.max(0.0D, targetCenter.subtract(eyePos).dot(look));
            Vector3d hitPos = clampToBox(eyePos.add(look.scale(t)), box);
            Vector3d dir = targetCenter.subtract(eyePos);
            Vector3d axis = dir.lengthSqr() > 1.0E-4D ? dir.normalize() : look;
            return new ImpactGeometry(hitPos, axis);
        }

        return new ImpactGeometry(targetCenter, target.getLookAngle().scale(-1.0D).normalize());
    }

    private static Vector3d clampToBox(Vector3d point, AxisAlignedBB box) {
        return new Vector3d(
                MathHelper.clamp(point.x, box.minX, box.maxX),
                MathHelper.clamp(point.y, box.minY, box.maxY),
                MathHelper.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    private static float computeEntitySizeScale(LivingEntity target) {
        double volume = Math.max(0.01D, (double) target.getBbWidth() * target.getBbWidth() * target.getBbHeight());
        // Trust me this works out for a normal player
        float scale = (float) (Math.cbrt(volume) / 0.8653D);
        return MathHelper.clamp(scale, 0.5F, 4.0F);
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

    private static final class ImpactGeometry {
        final Vector3d hitPos;
        final Vector3d direction;

        ImpactGeometry(Vector3d hitPos, Vector3d direction) {
            this.hitPos = hitPos;
            this.direction = direction;
        }
    }
}