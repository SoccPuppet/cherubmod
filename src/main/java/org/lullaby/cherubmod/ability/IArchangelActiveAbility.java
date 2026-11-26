package org.lullaby.cherubmod.ability;

import net.minecraft.entity.player.ServerPlayerEntity;
import org.lullaby.cherubmod.capability.IRaceData;

@FunctionalInterface
public interface IArchangelActiveAbility {
    void activate(ServerPlayerEntity player, IRaceData data);
}