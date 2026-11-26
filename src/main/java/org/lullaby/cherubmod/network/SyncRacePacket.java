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
    private final boolean judgementTotemCharged;
    private final int hangedOneActiveTicks;
    private final int worldStasisCooldownTicks;
    private final int worldStasisTicks;

    public SyncRacePacket(int entityId, IRaceData data) {
        this(entityId, data.getRace(), data.getArchangel(), true, 0,
                0, 0, new ArrayList<>(data.getHoveringItems()));
    }

    public SyncRacePacket(int entityId, Race race, Archangel archangel, boolean judgementTotemCharged, int hangedOneActiveTicks,
                          int worldStasisCooldownTicks, int worldStasisTicks, List<ItemStack> hoveringItems) {
        this.entityId = entityId;
        this.race = race;
        this.archangel = archangel;
        this.hoveringItems = hoveringItems;
        this.hangedOneActiveTicks = hangedOneActiveTicks;
        this.judgementTotemCharged = judgementTotemCharged;
        this.worldStasisCooldownTicks = worldStasisCooldownTicks;
        this.worldStasisTicks = worldStasisTicks;
    }

    public int getEntityId() { return entityId; }
    public Race getRace() { return race; }
    public Archangel getArchangel() { return archangel; }
    public List<ItemStack> getHoveringItems() { return hoveringItems; }
    public boolean getJudgementTotemCharged() { return judgementTotemCharged; }
    public int getHangedOneActiveTicks() {return hangedOneActiveTicks; }
    public int getWorldStasisCooldownTicks() { return worldStasisCooldownTicks; }
    public int getWorldStasisTicks() { return worldStasisTicks; }

    public static void encode(SyncRacePacket msg, PacketBuffer buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeEnum(msg.race);
        buf.writeEnum(msg.archangel);
        buf.writeVarInt(msg.hoveringItems.size());
        buf.writeBoolean(msg.judgementTotemCharged);
        buf.writeVarInt(msg.hangedOneActiveTicks);
        buf.writeVarInt(msg.worldStasisCooldownTicks);
        buf.writeVarInt(msg.worldStasisTicks);
        for (ItemStack stack : msg.hoveringItems) {
            buf.writeItem(stack);
        }
    }

    public static SyncRacePacket decode(PacketBuffer buf) {
        int entityId = buf.readVarInt();
        Race race = buf.readEnum(Race.class);
        Archangel archangel = buf.readEnum(Archangel.class);
        int count = buf.readVarInt();
        boolean judgementTotemCharged = buf.readBoolean();
        int hangedOneActiveTicks = buf.readVarInt();
        int worldStasisCooldownTicks = buf.readVarInt();
        int worldStasisTicks = buf.readVarInt();
        List<ItemStack> hovering = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            hovering.add(buf.readItem());
        }
        return new SyncRacePacket(entityId, race, archangel, judgementTotemCharged,
                hangedOneActiveTicks, worldStasisCooldownTicks, worldStasisTicks, hovering);
    }

    public static void handle(SyncRacePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncRace(msg))
        );
        ctx.setPacketHandled(true);
    }
}