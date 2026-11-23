package org.lullaby.cherubmod.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.lullaby.cherubmod.capability.RaceCapability;

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
}