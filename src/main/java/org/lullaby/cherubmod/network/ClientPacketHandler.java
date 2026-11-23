package org.lullaby.cherubmod.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particles.ParticleTypes;
import org.lullaby.cherubmod.capability.RaceCapability;

import java.util.Random;

public class ClientPacketHandler {
    public static void handleSyncRace(SyncRacePacket msg) {
        ClientWorld world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }
        Entity entity = world.getEntity(msg.getEntityId());
        if (!(entity instanceof PlayerEntity)) {
            return;
        }
        entity.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
            data.setRace(msg.getRace());
            data.setArchangel(msg.getArchangel());
            data.setPendingRaceChange(msg.isPendingRaceChange());
            data.setPossession(msg.getPossession());
            data.setHoveringItems(msg.getHoveringItems());
        });
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