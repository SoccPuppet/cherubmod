package org.lullaby.cherubmod.network;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class PlushVelocityPacket {
    private final double x;
    private final double y;
    private final double z;

    public PlushVelocityPacket(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }

    public static void encode(PlushVelocityPacket msg, PacketBuffer buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
    }

    public static PlushVelocityPacket decode(PacketBuffer buf) {
        return new PlushVelocityPacket(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(PlushVelocityPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handlePlushVelocity(msg))
        );
        ctx.setPacketHandled(true);
    }
}