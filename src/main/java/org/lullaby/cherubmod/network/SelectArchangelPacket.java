package org.lullaby.cherubmod.network;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import org.lullaby.cherubmod.capability.Archangel;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.item.TarotDeckItem;

import java.util.function.Supplier;

public class SelectArchangelPacket {
    private final Archangel archangel;

    public SelectArchangelPacket(Archangel archangel) {
        this.archangel = archangel;
    }

    public static void encode(SelectArchangelPacket msg, PacketBuffer buf) {
        buf.writeEnum(msg.archangel);
    }

    public static SelectArchangelPacket decode(PacketBuffer buf) {
        return new SelectArchangelPacket(buf.readEnum(Archangel.class));
    }

    public static void handle(SelectArchangelPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayerEntity player = ctx.getSender();
            if (player == null || msg.archangel == null || msg.archangel == Archangel.NONE) {
                return;
            }

            // Server-side validation: must be holding the Tarot Deck
            boolean holdingDeck = player.getMainHandItem().getItem() instanceof TarotDeckItem
                    || player.getOffhandItem().getItem() instanceof TarotDeckItem;
            if (!holdingDeck) {
                return;
            }

            player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
                if (data.getRace() != Race.CHERUB) {
                    return;
                }
                data.setArchangel(msg.archangel);
                CherubNetwork.syncToTrackingAndSelf(player);
            });
        });
        ctx.setPacketHandled(true);
    }
}