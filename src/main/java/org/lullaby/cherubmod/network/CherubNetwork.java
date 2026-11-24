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

import java.util.Optional;

public class CherubNetwork {
    private static final String PROTOCOL_VERSION = "8";
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
    }

    public static void sendSpallParticles(
            LivingEntity target,
            double x, double y, double z,
            double dirX, double dirY, double dirZ,
            float entityScale
    ) {
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new SpallParticlePacket(x, y, z, dirX, dirY, dirZ, entityScale)
        );
    }

    public static void sendShockwaveParticles(
            LivingEntity epicenterEntity,
            double x, double y, double z,
            float radius
    ) {
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> epicenterEntity),
                new ShockwaveParticlePacket(x, y, z, radius)
        );
    }

    public static void sendPossessionParticles(ServerPlayerEntity player, boolean summon) {
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new PossessionParticlePacket(player.getId(), summon)
        );
    }

    public static void syncToTrackingAndSelf(ServerPlayerEntity player) {
        player.getCapability(RaceCapability.INSTANCE).ifPresent(data ->
                CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                        new SyncRacePacket(player.getId(), data)
                )
        );
    }

    public static void syncTargetToViewer(ServerPlayerEntity target, ServerPlayerEntity viewer) {
        target.getCapability(RaceCapability.INSTANCE).ifPresent(data ->
                CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> viewer),
                        new SyncRacePacket(target.getId(), data)
                )
        );
    }
}