package org.lullaby.cherubmod.client.hud;

import net.minecraft.entity.player.PlayerEntity;
import org.lullaby.cherubmod.capability.IRaceData;

@FunctionalInterface
public interface ICherubHudProvider {
    void apply(PlayerEntity player, IRaceData data, CherubHudState.Builder state);
}