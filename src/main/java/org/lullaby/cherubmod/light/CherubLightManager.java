package org.lullaby.cherubmod.light;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CherubLightManager {
    public static final int FULL_RADIUS = 8;
    public static final int MAX_RADIUS = FULL_RADIUS + 15;
    private static volatile int[] ACTIVE_CLIENT_LIGHTS = new int[0];

    // Separate maps prevent singleplayer's integrated server and client from overwriting each other
    private static final Map<RegistryKey<World>, int[]> SERVER_LIGHTS = new ConcurrentHashMap<>();
    private static final Map<RegistryKey<World>, int[]> CLIENT_LIGHTS = new ConcurrentHashMap<>();

    public static void updateServerWorld(World world) {
        SERVER_LIGHTS.put(world.dimension(), collectCherubCoords(world));
    }

    public static void updateClientWorld(World world) {
        int[] positions = collectCherubCoords(world);
        CLIENT_LIGHTS.put(world.dimension(), positions);
        ACTIVE_CLIENT_LIGHTS = positions;
    }

    public static void clearWorld(World world) {
        Map<RegistryKey<World>, int[]> map = world.isClientSide ? CLIENT_LIGHTS : SERVER_LIGHTS;
        map.remove(world.dimension());
    }

    public static void clearClient() {
        CLIENT_LIGHTS.clear();
        ACTIVE_CLIENT_LIGHTS = new int[0];
    }

    private static int[] collectCherubCoords(World world) {
        List<? extends PlayerEntity> players = world.players();
        int[] buffer = new int[players.size() * 3];
        int count = 0;

        for (PlayerEntity player : players) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            boolean isCherub = player.getCapability(RaceCapability.INSTANCE)
                    .map(data -> data.getRace() == Race.CHERUB)
                    .orElse(false);
            if (isCherub) {
                BlockPos pos = player.blockPosition();
                buffer[count++] = pos.getX();
                buffer[count++] = pos.getY() + 1;
                buffer[count++] = pos.getZ();
            }
        }
        return count == buffer.length ? buffer : Arrays.copyOf(buffer, count);
    }

    public static int getClientLightLevel(BlockPos pos) {
        return computeLightLevel(ACTIVE_CLIENT_LIGHTS, pos);
    }
    public static int getLightLevel(World world, BlockPos pos) {
        Map<RegistryKey<World>, int[]> map = world.isClientSide ? CLIENT_LIGHTS : SERVER_LIGHTS;
        return computeLightLevel(map.get(world.dimension()), pos);
    }

    private static int computeLightLevel(int[] coords, BlockPos pos) {
        if (coords == null || coords.length == 0) {
            return 0;
        }

        int best = 0;
        int px = pos.getX();
        int py = pos.getY();
        int pz = pos.getZ();

        for (int i = 0; i < coords.length; i += 3) {
            int dx = Math.abs(px - coords[i]);
            if (dx >= MAX_RADIUS) continue;
            int dz = Math.abs(pz - coords[i + 2]);
            int distXZ = dx + dz;
            if (distXZ >= MAX_RADIUS) continue;
            int dy = Math.abs(py - coords[i + 1]);

            int dist = distXZ + dy;
            if (dist <= FULL_RADIUS) {
                return 15;
            }
            int level = 15 - (dist - FULL_RADIUS);
            if (level > best) {
                best = level;
            }
        }
        return best;
    }
}