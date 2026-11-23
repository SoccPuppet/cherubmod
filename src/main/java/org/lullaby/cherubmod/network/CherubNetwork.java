package org.lullaby.cherubmod.network;

import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.RaceCapability;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import java.util.Optional;

public class CherubNetwork {
    private static final String PROTOCOL_VERSION = "1";
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
    }

    public static void syncToPlayer(ServerPlayerEntity player) {
        player.getCapability(RaceCapability.INSTANCE).ifPresent(data ->
                CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new SyncRacePacket(data.getRace(), data.getArchangel(), data.isPendingRaceChange())
                )
        );
    }
}