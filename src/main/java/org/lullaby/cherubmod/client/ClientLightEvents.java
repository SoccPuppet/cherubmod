package org.lullaby.cherubmod.client;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.light.CherubLightManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientLightEvents {
    private static final Map<Integer, BlockPos> LAST_POSITIONS = new HashMap<>();
    private static final LongOpenHashSet DIRTY_SECTIONS = new LongOpenHashSet();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientWorld world = mc.level;
        if (world == null) {
            if (!LAST_POSITIONS.isEmpty() || !DIRTY_SECTIONS.isEmpty()) {
                onClientLogout(null);
            }
            return;
        }

        // Run every 4 ticks
        if ((world.getGameTime() & 3L) != 0L) {
            return;
        }

        CherubLightManager.updateClientWorld(world);

        Set<Integer> activeIds = new HashSet<>();
        int r = CherubLightManager.MAX_RADIUS;

        for (PlayerEntity player : world.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            boolean isCherub = player.getCapability(RaceCapability.INSTANCE)
                    .map(data -> data.getRace() == Race.CHERUB)
                    .orElse(false);
            if (!isCherub) {
                continue;
            }

            int id = player.getId();
            activeIds.add(id);
            BlockPos current = player.blockPosition().above();
            BlockPos previous = LAST_POSITIONS.get(id);

            if (previous == null) {
                LAST_POSITIONS.put(id, current);
                collectDirtySections(world, current, r);
            } else if (!previous.equals(current)) {
                LAST_POSITIONS.put(id, current);
                collectDirtySections(world, current, r);
            }
        }

        Iterator<Map.Entry<Integer, BlockPos>> it = LAST_POSITIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, BlockPos> entry = it.next();
            if (!activeIds.contains(entry.getKey())) {
                BlockPos oldPos = entry.getValue();
                collectDirtySections(world, oldPos, r);
                it.remove();
            }
        }

        flushDirtySections(mc);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        LAST_POSITIONS.clear();
        DIRTY_SECTIONS.clear();
        CherubLightManager.clearClient();
    }

    private static void collectDirtySections(ClientWorld world, BlockPos center, int radius) {
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        int minSecX = (cx - radius - 1) >> 4;
        int minSecY = Math.max(0, (cy - radius - 1) >> 4);
        int minSecZ = (cz - radius - 1) >> 4;
        int maxSecX = (cx + radius + 1) >> 4;
        int maxSecY = Math.min(15, (cy + radius + 1) >> 4);
        int maxSecZ = (cz + radius + 1) >> 4;

        for (int sx = minSecX; sx <= maxSecX; sx++) {
            int minX = (sx << 4) - 1;
            int maxX = (sx << 4) + 16;
            int dx = cx < minX ? (minX - cx) : (cx > maxX ? (cx - maxX) : 0);
            if (dx > radius) continue;

            for (int sz = minSecZ; sz <= maxSecZ; sz++) {
                if (!world.hasChunk(sx, sz)) continue;
                int minZ = (sz << 4) - 1;
                int maxZ = (sz << 4) + 16;
                int dz = cz < minZ ? (minZ - cz) : (cz > maxZ ? (cz - maxZ) : 0);
                int distXZ = dx + dz;
                if (distXZ > radius) continue;

                for (int sy = minSecY; sy <= maxSecY; sy++) {
                    int minY = (sy << 4) - 1;
                    int maxY = (sy << 4) + 16;
                    int dy = cy < minY ? (minY - cy) : (cy > maxY ? (cy - maxY) : 0);
                    if (distXZ + dy <= radius) {
                        DIRTY_SECTIONS.add(SectionPos.asLong(sx, sy, sz));
                    }
                }
            }
        }
    }

    private static void flushDirtySections(Minecraft mc) {
        if (DIRTY_SECTIONS.isEmpty()) {
            return;
        }
        LongIterator it = DIRTY_SECTIONS.iterator();
        while (it.hasNext()) {
            long packed = it.nextLong();
            mc.levelRenderer.setSectionDirty(
                    SectionPos.x(packed),
                    SectionPos.y(packed),
                    SectionPos.z(packed)
            );
        }
        DIRTY_SECTIONS.clear();
    }
}