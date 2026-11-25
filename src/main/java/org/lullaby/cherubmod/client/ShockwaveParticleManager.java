package org.lullaby.cherubmod.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.DiggingParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lullaby.cherubmod.Cherubmod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ShockwaveParticleManager {
    private static final int WAVE_TICKS = 4;
    private static final List<ActiveShockwave> ACTIVE_WAVES = new ArrayList<>();

    public static void enqueue(double x, double y, double z, float radius) {
        Minecraft mc = Minecraft.getInstance();
        ClientWorld world = mc.level;
        if (world == null) {
            return;
        }

        ActiveShockwave wave = new ActiveShockwave(x, y, z, radius);
        // Spawn the innermost ring immediately on the impact tick so there is zero perceived delay
        spawnWaveStep(mc, world, wave);
        wave.step++;
        if (wave.step < WAVE_TICKS) {
            ACTIVE_WAVES.add(wave);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE_WAVES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientWorld world = mc.level;
        if (world == null) {
            ACTIVE_WAVES.clear();
            return;
        }

        Iterator<ActiveShockwave> it = ACTIVE_WAVES.iterator();
        while (it.hasNext()) {
            ActiveShockwave wave = it.next();
            spawnWaveStep(mc, world, wave);
            wave.step++;
            if (wave.step >= WAVE_TICKS) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        ACTIVE_WAVES.clear();
    }

    private static void spawnWaveStep(Minecraft mc, ClientWorld world, ActiveShockwave wave) {
        float ringRadius = wave.radius * ((wave.step + 1) / (float) WAVE_TICKS);
        int points = Math.max(12, Math.round(ringRadius * 14.0F));
        spawnGroundRing(mc.particleEngine, world, wave.x, wave.y, wave.z, ringRadius, points);

        if (wave.step == 0) {
            spawnGroundRing(mc.particleEngine, world, wave.x, wave.y, wave.z, 0.4D, 8);
        }
    }

    private static void spawnGroundRing(
            ParticleManager particleEngine,
            ClientWorld world,
            double centerX,
            double centerY,
            double centerZ,
            double radius,
            int points
    ) {
        Random rand = world.random;
        double angleOffset = rand.nextDouble() * Math.PI * 2.0D;

        for (int i = 0; i < points; i++) {
            double angle = angleOffset + (Math.PI * 2.0D * i) / points + (rand.nextDouble() - 0.5D) * 0.15D;
            double r = Math.max(0.1D, radius + (rand.nextDouble() - 0.5D) * 0.35D);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double px = centerX + cos * r;
            double pz = centerZ + sin * r;

            BlockPos floorPos = findFloorPos(world, px, centerY, pz);
            if (floorPos == null) {
                continue;
            }

            BlockState floorState = world.getBlockState(floorPos);
            VoxelShape shape = floorState.getCollisionShape(world, floorPos);
            double surfaceY = floorPos.getY() + (shape.isEmpty() ? 1.0D : shape.max(Direction.Axis.Y));

            BlockParticleData blockData = new BlockParticleData(ParticleTypes.BLOCK, floorState);
            // Pop predominantly upward with a subtle radial push outward
            double dirX = cos * 0.32D;
            double dirY = 0.94D;
            double dirZ = sin * 0.32D;

            Particle particle = particleEngine.createParticle(
                    blockData,
                    px, surfaceY + 0.05D, pz,
                    dirX * 10.0D, dirY * 10.0D, dirZ * 10.0D
            );
            if (particle != null) {
                if (particle instanceof DiggingParticle) {
                    ((DiggingParticle) particle).init(floorPos);
                }
                particle.scale(1.15F + rand.nextFloat() * 0.45F);
                particle.setPower(1.6F + rand.nextFloat() * 0.9F);
                particle.setLifetime(8 + rand.nextInt(6));
            }
        }
    }

    @Nullable
    private static BlockPos findFloorPos(ClientWorld world, double x, double baseY, double z) {
        int bx = MathHelper.floor(x);
        int startY = MathHelper.floor(baseY + 0.25D);
        int bz = MathHelper.floor(z);
        BlockPos.Mutable cursor = new BlockPos.Mutable(bx, startY, bz);

        if (hasSolidSurface(world, cursor)) {
            // Feet level is inside a slab/stair/step: climb up at most 2 blocks to find the top surface
            for (int dy = 0; dy <= 2; dy++) {
                cursor.set(bx, startY + dy, bz);
                BlockPos above = cursor.above();
                if (hasSolidSurface(world, cursor) && !isFullBlocker(world, above)) {
                    return cursor.immutable();
                }
            }
            return null;
        }

        // Feet level is open air: scan downward up to 4 blocks to find the floor
        for (int dy = 1; dy <= 4; dy++) {
            cursor.set(bx, startY - dy, bz);
            if (hasSolidSurface(world, cursor)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    private static boolean hasSolidSurface(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.getCollisionShape(world, pos).isEmpty();
    }

    private static boolean isFullBlocker(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(world, pos);
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) >= 0.95D;
    }

    private static final class ActiveShockwave {
        final double x;
        final double y;
        final double z;
        final float radius;
        int step = 0;

        ActiveShockwave(double x, double y, double z, float radius) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }
    }
}
//immunity to slow, blind, hitting gives glowing, killing gives absorptoin
//less health, more knockback