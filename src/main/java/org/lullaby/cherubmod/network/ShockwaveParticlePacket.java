package org.lullaby.cherubmod.network;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class ShockwaveParticlePacket {
    private final double x;
    private final double y;
    private final double z;
    private final float radius;

    public ShockwaveParticlePacket(double x, double y, double z, float radius) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getRadius() { return radius; }

    public static void encode(ShockwaveParticlePacket msg, PacketBuffer buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.radius);
    }

    public static ShockwaveParticlePacket decode(PacketBuffer buf) {
        return new ShockwaveParticlePacket(
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat()
        );
    }

    public static void handle(ShockwaveParticlePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleShockwaveParticles(msg))
        );
        ctx.setPacketHandled(true);
    }
}