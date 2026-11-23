package org.lullaby.cherubmod.light;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CherubLightManager {
    public static final int FULL_RADIUS = 8;
    public static final int MAX_RADIUS = FULL_RADIUS + 14;
    private static volatile BlockPos[] ACTIVE_CLIENT_LIGHTS = new BlockPos[0];

    // Separate maps prevent singleplayer's integrated server and client from overwriting each other
    private static final Map<RegistryKey<World>, BlockPos[]> SERVER_LIGHTS = new ConcurrentHashMap<>();
    private static final Map<RegistryKey<World>, BlockPos[]> CLIENT_LIGHTS = new ConcurrentHashMap<>();

    public static void updateServerWorld(World world) {
        SERVER_LIGHTS.put(world.dimension(), collectCherubPositions(world));
    }

    public static void updateClientWorld(World world) {
        BlockPos[] positions = collectCherubPositions(world);
        CLIENT_LIGHTS.put(world.dimension(), positions);
        ACTIVE_CLIENT_LIGHTS = positions;
    }

    public static void clearWorld(World world) {
        Map<RegistryKey<World>, BlockPos[]> map = world.isClientSide ? CLIENT_LIGHTS : SERVER_LIGHTS;
        map.remove(world.dimension());
    }

    public static void clearClient() {
        CLIENT_LIGHTS.clear();
        ACTIVE_CLIENT_LIGHTS = new BlockPos[0];
    }

    private static BlockPos[] collectCherubPositions(World world) {
        List<BlockPos> positions = new ArrayList<>();
        for (PlayerEntity player : world.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            boolean isCherub = player.getCapability(RaceCapability.INSTANCE)
                    .map(data -> data.getRace() == Race.CHERUB)
                    .orElse(false);
            if (isCherub) {
                positions.add(player.blockPosition().above());
            }
        }
        return positions.toArray(new BlockPos[0]);
    }

    public static int getClientLightLevel(BlockPos pos) {
        return computeLightLevel(ACTIVE_CLIENT_LIGHTS, pos);
    }
    public static int getLightLevel(World world, BlockPos pos) {
        Map<RegistryKey<World>, BlockPos[]> map = world.isClientSide ? CLIENT_LIGHTS : SERVER_LIGHTS;
        return computeLightLevel(map.get(world.dimension()), pos);
    }

    private static int computeLightLevel(BlockPos[] sources, BlockPos pos) {
        if (sources == null || sources.length == 0) {
            return 0;
        }

        int best = 0;
        int px = pos.getX();
        int py = pos.getY();
        int pz = pos.getZ();

        for (BlockPos source : sources) {
            int dx = px - source.getX();
            int dy = py - source.getY();
            int dz = pz - source.getZ();
            int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
            int level = dist <= FULL_RADIUS ? 15 : Math.max(0, 15 - (dist - FULL_RADIUS));
            if (level > best) {
                best = level;
            }
        }
        return best;
    }
}