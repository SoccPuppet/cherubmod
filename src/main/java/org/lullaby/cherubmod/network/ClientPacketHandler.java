package org.lullaby.cherubmod.network;

import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.client.ShockwaveParticleManager;

import java.util.Random;

public class ClientPacketHandler {
    public static void handleSyncRace(SyncRacePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        Entity target = null;

        if (mc.player != null && mc.player.getId() == msg.getEntityId()) {
            target = mc.player;
        } else if (mc.level != null) {
            target = mc.level.getEntity(msg.getEntityId());
        }

        if (!(target instanceof PlayerEntity)) {
            return;
        }

        target.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
            data.setRace(msg.getRace());
            data.setArchangel(msg.getArchangel());
            data.setHoveringItems(msg.getHoveringItems());
            data.setJudgementTotemCharged(msg.getJudgementTotemCharged());
            data.setHangedOneActiveTicks(msg.getHangedOneActiveTicks());
        });
    }

    public static void handleSpallParticles(SpallParticlePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        ClientWorld world = mc.level;
        if (world == null) {
            return;
        }

        Vector3d axis = new Vector3d(msg.getDirX(), msg.getDirY(), msg.getDirZ());
        if (axis.lengthSqr() < 1.0E-6D) {
            return;
        }
        axis = axis.normalize();

        Vector3d ref = Math.abs(axis.y) < 0.99D ? new Vector3d(0.0D, 1.0D, 0.0D) : new Vector3d(1.0D, 0.0D, 0.0D);
        Vector3d right = axis.cross(ref).normalize();
        Vector3d up = right.cross(axis).normalize();

        BlockParticleData ironSparks = new BlockParticleData(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState());
        ParticleManager particleEngine = mc.particleEngine;
        Random rand = world.random;

        float scale = MathHelper.clamp(msg.getEntityScale(), 0.5F, 4.0F);
        int count = MathHelper.clamp(Math.round(28.0F * scale), 14, 110);
        double cosMaxAngle = Math.cos(Math.toRadians(32.0D));

        float velocityScale = 0.65F + 0.35F * scale;
        float lifetimeScale = 0.70F + 0.30F * scale;
        float quadScale = MathHelper.clamp(0.75F + 0.25F * scale, 0.7F, 2.0F);
        double originSpread = 0.08D * scale;

        for (int i = 0; i < count; i++) {
            double cosAlpha = cosMaxAngle + rand.nextDouble() * (1.0D - cosMaxAngle);
            double sinAlpha = Math.sqrt(Math.max(0.0D, 1.0D - cosAlpha * cosAlpha));
            double phi = rand.nextDouble() * Math.PI * 2.0D;

            Vector3d lateral = right.scale(Math.cos(phi)).add(up.scale(Math.sin(phi)));
            Vector3d dir = axis.scale(cosAlpha).add(lateral.scale(sinAlpha));

            double px = msg.getX() + lateral.x * originSpread;
            double py = msg.getY() + lateral.y * originSpread;
            double pz = msg.getZ() + lateral.z * originSpread;

            Particle particle = particleEngine.createParticle(
                    ironSparks,
                    px, py, pz,
                    dir.x * 10.0D, dir.y * 10.0D, dir.z * 10.0D
            );
            if (particle != null) {
                particle.scale(quadScale);
                particle.setPower((1.8F + rand.nextFloat() * 1.2F) * velocityScale);
                particle.setLifetime(Math.round((10 + rand.nextInt(8)) * lifetimeScale));
            }
        }
    }

    public static void handleShockwaveParticles(ShockwaveParticlePacket msg) {
        ShockwaveParticleManager.enqueue(msg.getX(), msg.getY(), msg.getZ(), msg.getRadius());
    }

    public static void handlePossessionParticles(PossessionParticlePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        ClientWorld world = mc.level;
        if (world == null) {
            return;
        }
        Entity entity = world.getEntity(msg.getEntityId());
        if (entity == null) {
            return;
        }

        ParticleManager particleEngine = mc.particleEngine;
        Random rand = world.random;
        double centerX = entity.getX();
        double centerY = entity.getY() + entity.getBbHeight() * 0.55D;
        double centerZ = entity.getZ();

        final double friction = 0.92D; // EndRodParticle's baseAirFriction
        int count = 25;

        for (int i = 0; i < count; i++) {
            double theta = rand.nextDouble() * Math.PI * 2.0D;
            double dirY = rand.nextDouble() * 2.0D - 1.0D;
            double sinPhi = Math.sqrt(1.0D - dirY * dirY);

            double dirX = sinPhi * Math.cos(theta);
            double dirZ = sinPhi * Math.sin(theta);

            if (msg.isSummon()) {
                double radius = 1.5D + rand.nextDouble() * 0.45D;
                int lifetime = 8 + rand.nextInt(4);
                double speed = radius * (1.0D - friction) / (1.0D - Math.pow(friction, lifetime));

                Particle particle = particleEngine.createParticle(
                        ParticleTypes.END_ROD,
                        centerX + dirX * radius,
                        centerY + dirY * radius,
                        centerZ + dirZ * radius,
                        -dirX * speed, -dirY * speed, -dirZ * speed
                );
                if (particle != null) {
                    particle.setLifetime(lifetime);
                }
            } else {
                double startRadius = 0.2D + rand.nextDouble() * 0.15D;
                double travelDistance = 0.9D + rand.nextDouble() * 0.5D;
                int lifetime = 10 + rand.nextInt(5);
                double speed = travelDistance * (1.0D - friction) / (1.0D - Math.pow(friction, lifetime));

                Particle particle = particleEngine.createParticle(
                        ParticleTypes.END_ROD,
                        centerX + dirX * startRadius,
                        centerY + dirY * startRadius,
                        centerZ + dirZ * startRadius,
                        dirX * speed, dirY * speed, dirZ * speed
                );
                if (particle != null) {
                    particle.setLifetime(lifetime);
                }
            }
        }
    }
}