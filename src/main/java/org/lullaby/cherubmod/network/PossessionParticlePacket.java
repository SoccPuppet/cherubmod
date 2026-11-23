package org.lullaby.cherubmod.network;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class PossessionParticlePacket {
    private final int entityId;
    private final boolean summon;

    public PossessionParticlePacket(int entityId, boolean summon) {
        this.entityId = entityId;
        this.summon = summon;
    }

    public int getEntityId() {
        return entityId;
    }

    public boolean isSummon() {
        return summon;
    }

    public static void encode(PossessionParticlePacket msg, PacketBuffer buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.summon);
    }

    public static PossessionParticlePacket decode(PacketBuffer buf) {
        return new PossessionParticlePacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(PossessionParticlePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handlePossessionParticles(msg))
        );
        ctx.setPacketHandled(true);
    }
}