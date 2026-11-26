package org.lullaby.cherubmod.network;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.RaceCapability;

import java.util.ArrayList;
import java.util.Optional;

public class CherubNetwork {
    private static final String PROTOCOL_VERSION = "19";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Cherubmod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(
                id++,
                SyncRacePacket.class,
                SyncRacePacket::encode,
                SyncRacePacket::decode,
                SyncRacePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(
                id++,
                TogglePossessionPacket.class,
                TogglePossessionPacket::encode,
                TogglePossessionPacket::decode,
                TogglePossessionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                id++,
                PossessionParticlePacket.class,
                PossessionParticlePacket::encode,
                PossessionParticlePacket::decode,
                PossessionParticlePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(
                id++,
                SelectArchangelPacket.class,
                SelectArchangelPacket::encode,
                SelectArchangelPacket::decode,
                SelectArchangelPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                id++,
                SpallParticlePacket.class,
                SpallParticlePacket::encode,
                SpallParticlePacket::decode,
                SpallParticlePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(
                id++,
                ShockwaveParticlePacket.class,
                ShockwaveParticlePacket::encode,
                ShockwaveParticlePacket::decode,
                ShockwaveParticlePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(
                id++,
                TriggerActiveAbilityPacket.class,
                TriggerActiveAbilityPacket::encode,
                TriggerActiveAbilityPacket::decode,
                TriggerActiveAbilityPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                id++,
                PlushVelocityPacket.class,
                PlushVelocityPacket::encode,
                PlushVelocityPacket::decode,
                PlushVelocityPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public static void sendPlushVelocity(ServerPlayerEntity player, double x, double y, double z) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PlushVelocityPacket(x, y, z)
        );
    }

    public static void sendSpallParticles(
            LivingEntity target,
            double x, double y, double z,
            double dirX, double dirY, double dirZ,
            float entityScale
    ) {
        SpallParticlePacket packet = new SpallParticlePacket(x, y, z, dirX, dirY, dirZ, entityScale);
        CHANNEL.send(
                PacketDistributor.NEAR.with(PacketDistributor.TargetPoint
                        .p(target.getX(), target.getY(), target.getZ(), 36.0D, target.level.dimension())),
                packet
        );
    }

    public static void sendShockwaveParticles(
            LivingEntity epicenterEntity,
            double x, double y, double z,
            float radius
    ) {
        ShockwaveParticlePacket packet = new ShockwaveParticlePacket(x, y, z, radius);
        CHANNEL.send(
                PacketDistributor.NEAR.with(PacketDistributor.TargetPoint
                        .p(epicenterEntity.getX(), epicenterEntity.getY(), epicenterEntity.getZ(),
                                36.0D, epicenterEntity.level.dimension())),
                packet
        );
    }

    public static void sendPossessionParticles(ServerPlayerEntity player, boolean summon) {
        PossessionParticlePacket packet = new PossessionParticlePacket(player.getId(), summon);
        CHANNEL.send(
                PacketDistributor.NEAR.with(PacketDistributor.TargetPoint
                        .p(player.getX(), player.getY(), player.getZ(), 36.0D, player.level.dimension())),
                packet
        );
    }

    public static void syncToTrackingAndSelf(ServerPlayerEntity player) {
        player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
            SyncRacePacket trackingPacket = new SyncRacePacket(
                    player.getId(),
                    data.getRace(),
                    data.getArchangel(),
                    data.isJudgementTotemCharged(),
                    data.getHangedOneActiveTicks(),
                    data.getWorldStasisCooldownTicks(),
                    data.getWorldStasisTicks(),
                    new ArrayList<>(data.getHoveringItems())
            );
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), trackingPacket);
            CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), trackingPacket);
        });
    }

    public static void syncTargetToViewer(ServerPlayerEntity target, ServerPlayerEntity viewer) {

        target.getCapability(RaceCapability.INSTANCE).ifPresent(data ->{
            SyncRacePacket trackingPacket = new SyncRacePacket(
                    target.getId(),
                    data.getRace(),
                    data.getArchangel(),
                    data.isJudgementTotemCharged(),
                    data.getHangedOneActiveTicks(),
                    data.getWorldStasisCooldownTicks(),
                    data.getWorldStasisTicks(),
                    new ArrayList<>(data.getHoveringItems())
            );
            CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> viewer),
                    trackingPacket
            );}
        );
    }
}