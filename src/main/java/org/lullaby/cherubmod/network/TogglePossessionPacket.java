package org.lullaby.cherubmod.network;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import org.lullaby.cherubmod.possession.PossessionHelper;

import java.util.function.Supplier;

public class TogglePossessionPacket {
    public static void encode(TogglePossessionPacket msg, PacketBuffer buf) { }

    public static TogglePossessionPacket decode(PacketBuffer buf) {
        return new TogglePossessionPacket();
    }

    public static void handle(TogglePossessionPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayerEntity sender = ctx.getSender();
            if (sender != null) {
                PossessionHelper.toggleSummon(sender);
            }
        });
        ctx.setPacketHandled(true);
    }
}