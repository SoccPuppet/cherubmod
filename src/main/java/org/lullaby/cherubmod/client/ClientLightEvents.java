package org.lullaby.cherubmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
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

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientWorld world = mc.level;
        if (world == null) {
            if (!LAST_POSITIONS.isEmpty()) {
                LAST_POSITIONS.clear();
                CherubLightManager.clearClient();
            }
            return;
        }

        // Update every 4 ticks to keep chunk rebuilds smooth while sprinting
        if ((world.getGameTime() & 2L) != 0L) {
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
                markLightBoxDirty(mc, current, current, r);
            } else if (!previous.equals(current)) {
                LAST_POSITIONS.put(id, current);
                markLightBoxDirty(mc, previous, current, r);
            }
        }

        Iterator<Map.Entry<Integer, BlockPos>> it = LAST_POSITIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, BlockPos> entry = it.next();
            if (!activeIds.contains(entry.getKey())) {
                BlockPos oldPos = entry.getValue();
                markLightBoxDirty(mc, oldPos, oldPos, r);
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        LAST_POSITIONS.clear();
        CherubLightManager.clearClient();
    }

    private static void markLightBoxDirty(Minecraft mc, BlockPos a, BlockPos b, int radius) {
        int minSecX = (Math.min(a.getX(), b.getX()) - radius - 1) >> 4;
        int minSecY = Math.max(0, (Math.min(a.getY(), b.getY()) - radius - 1) >> 4);
        int minSecZ = (Math.min(a.getZ(), b.getZ()) - radius - 1) >> 4;
        int maxSecX = (Math.max(a.getX(), b.getX()) + radius + 1) >> 4;
        int maxSecY = Math.min(15, (Math.max(a.getY(), b.getY()) + radius + 1) >> 4);
        int maxSecZ = (Math.max(a.getZ(), b.getZ()) + radius + 1) >> 4;

        for (int sx = minSecX; sx <= maxSecX; sx++) {
            for (int sy = minSecY; sy <= maxSecY; sy++) {
                for (int sz = minSecZ; sz <= maxSecZ; sz++) {
                    mc.levelRenderer.setSectionDirty(sx, sy, sz);
                }
            }
        }
    }
}