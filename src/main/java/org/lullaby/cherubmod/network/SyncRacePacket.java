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
    private final boolean pendingRaceChange;
    private final ItemStack possession;
    private final List<ItemStack> hoveringItems;

    public SyncRacePacket(int entityId, IRaceData data) {
        this(entityId, data.getRace(), data.getArchangel(), data.isPendingRaceChange(),
                data.getPossession().copy(), new ArrayList<>(data.getHoveringItems()));
    }

    public SyncRacePacket(int entityId, Race race, Archangel archangel, boolean pendingRaceChange,
                          ItemStack possession, List<ItemStack> hoveringItems) {
        this.entityId = entityId;
        this.race = race;
        this.archangel = archangel;
        this.pendingRaceChange = pendingRaceChange;
        this.possession = possession;
        this.hoveringItems = hoveringItems;
    }

    public int getEntityId() { return entityId; }
    public Race getRace() { return race; }
    public Archangel getArchangel() { return archangel; }
    public boolean isPendingRaceChange() { return pendingRaceChange; }
    public ItemStack getPossession() { return possession; }
    public List<ItemStack> getHoveringItems() { return hoveringItems; }

    public static void encode(SyncRacePacket msg, PacketBuffer buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeEnum(msg.race);
        buf.writeEnum(msg.archangel);
        buf.writeBoolean(msg.pendingRaceChange);
        buf.writeItem(msg.possession);
        buf.writeVarInt(msg.hoveringItems.size());
        for (ItemStack stack : msg.hoveringItems) {
            buf.writeItem(stack);
        }
    }

    public static SyncRacePacket decode(PacketBuffer buf) {
        int entityId = buf.readVarInt();
        Race race = buf.readEnum(Race.class);
        Archangel archangel = buf.readEnum(Archangel.class);
        boolean pending = buf.readBoolean();
        ItemStack possession = buf.readItem();
        int count = buf.readVarInt();
        List<ItemStack> hovering = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            hovering.add(buf.readItem());
        }
        return new SyncRacePacket(entityId, race, archangel, pending, possession, hovering);
    }

    public static void handle(SyncRacePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncRace(msg))
        );
        ctx.setPacketHandled(true);
    }
}