package org.lullaby.cherubmod.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import org.lullaby.cherubmod.capability.Archangel;
import org.lullaby.cherubmod.capability.IRaceData;
import org.lullaby.cherubmod.capability.Race;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncRacePacket {
    private final int entityId;
    private final Race race;
    private final Archangel archangel;
    private final List<ItemStack> hoveringItems;

    public SyncRacePacket(int entityId, IRaceData data) {
        this(entityId, data.getRace(), data.getArchangel(),
                new ArrayList<>(data.getHoveringItems()));
    }

    public SyncRacePacket(int entityId, Race race, Archangel archangel,
                          List<ItemStack> hoveringItems) {
        this.entityId = entityId;
        this.race = race;
        this.archangel = archangel;
        this.hoveringItems = hoveringItems;
    }

    public int getEntityId() { return entityId; }
    public Race getRace() { return race; }
    public Archangel getArchangel() { return archangel; }
    public List<ItemStack> getHoveringItems() { return hoveringItems; }

    public static void encode(SyncRacePacket msg, PacketBuffer buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeEnum(msg.race);
        buf.writeEnum(msg.archangel);
        buf.writeVarInt(msg.hoveringItems.size());
        for (ItemStack stack : msg.hoveringItems) {
            buf.writeItem(stack);
        }
    }

    public static SyncRacePacket decode(PacketBuffer buf) {
        int entityId = buf.readVarInt();
        Race race = buf.readEnum(Race.class);
        Archangel archangel = buf.readEnum(Archangel.class);
        int count = buf.readVarInt();
        List<ItemStack> hovering = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            hovering.add(buf.readItem());
        }
        return new SyncRacePacket(entityId, race, archangel, hovering);
    }

    public static void handle(SyncRacePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncRace(msg))
        );
        ctx.setPacketHandled(true);
    }
}