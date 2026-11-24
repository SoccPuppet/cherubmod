package org.lullaby.cherubmod.network;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class SpallParticlePacket {
    private final double x;
    private final double y;
    private final double z;
    private final double dirX;
    private final double dirY;
    private final double dirZ;
    private final float entityScale;

    public SpallParticlePacket(
            double x, double y, double z,
            double dirX, double dirY, double dirZ,
            float entityScale
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dirX = dirX;
        this.dirY = dirY;
        this.dirZ = dirZ;
        this.entityScale = entityScale;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getDirX() { return dirX; }
    public double getDirY() { return dirY; }
    public double getDirZ() { return dirZ; }
    public float getEntityScale() { return entityScale; }

    public static void encode(SpallParticlePacket msg, PacketBuffer buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeDouble(msg.dirX);
        buf.writeDouble(msg.dirY);
        buf.writeDouble(msg.dirZ);
        buf.writeFloat(msg.entityScale);
    }

    public static SpallParticlePacket decode(PacketBuffer buf) {
        return new SpallParticlePacket(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat()
        );
    }

    public static void handle(SpallParticlePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSpallParticles(msg))
        );
        ctx.setPacketHandled(true);
    }
}