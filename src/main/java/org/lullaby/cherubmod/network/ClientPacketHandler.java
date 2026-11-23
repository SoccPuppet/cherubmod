package org.lullaby.cherubmod.network;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import org.lullaby.cherubmod.capability.RaceCapability;

public class ClientPacketHandler {
    public static void handleSyncRace(SyncRacePacket msg) {
        PlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
            data.setRace(msg.getRace());
            data.setArchangel(msg.getArchangel());
            data.setPendingRaceChange(msg.isPendingRaceChange());
        });
    }
}
