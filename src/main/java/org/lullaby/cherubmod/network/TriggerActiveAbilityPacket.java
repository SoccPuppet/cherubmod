package org.lullaby.cherubmod.network;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import org.lullaby.cherubmod.ability.ArchangelAbilityRegistry;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;

import java.util.function.Supplier;

public class TriggerActiveAbilityPacket {
    public TriggerActiveAbilityPacket() {}

    public static void encode(TriggerActiveAbilityPacket msg, PacketBuffer buf) {}

    public static TriggerActiveAbilityPacket decode(PacketBuffer buf) {
        return new TriggerActiveAbilityPacket();
    }

    public static void handle(TriggerActiveAbilityPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayerEntity player = ctx.getSender();
            if (player == null || !player.isAlive() || player.isSpectator()) {
                return;
            }

            player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
                if (data.getRace() != Race.CHERUB) {
                    return;
                }
                ArchangelAbilityRegistry.trigger(player, data);
            });
        });
        ctx.setPacketHandled(true);
    }
}