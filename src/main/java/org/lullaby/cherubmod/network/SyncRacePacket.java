package org.lullaby.cherubmod.network;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import org.lullaby.cherubmod.capability.Archangel;
import org.lullaby.cherubmod.capability.Race;

import java.util.function.Supplier;

public class SyncRacePacket {
    private final Race race;
    private final Archangel archangel;
    private final boolean pendingRaceChange;

    public SyncRacePacket(Race race, Archangel archangel, boolean pendingRaceChange) {
        this.race = race;
        this.archangel = archangel;
        this.pendingRaceChange = pendingRaceChange;
    }

    public Race getRace() {
        return race;
    }

    public Archangel getArchangel() {
        return archangel;
    }

    public boolean isPendingRaceChange() {
        return pendingRaceChange;
    }

    public static void encode(SyncRacePacket msg, PacketBuffer buf) {
        buf.writeEnum(msg.race);
        buf.writeEnum(msg.archangel);
        buf.writeBoolean(msg.pendingRaceChange);
    }

    public static SyncRacePacket decode(PacketBuffer buf) {
        Race race = buf.readEnum(Race.class);
        Archangel archangel = buf.readEnum(Archangel.class);
        boolean pending = buf.readBoolean();
        return new SyncRacePacket(race, archangel, pending);
    }

    public static void handle(SyncRacePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncRace(msg))
        );
        ctx.setPacketHandled(true);
    }
}
