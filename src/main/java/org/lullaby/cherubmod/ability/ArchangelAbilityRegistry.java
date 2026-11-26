package org.lullaby.cherubmod.ability;

import net.minecraft.entity.player.ServerPlayerEntity;
import org.lullaby.cherubmod.capability.Archangel;
import org.lullaby.cherubmod.capability.IRaceData;
import org.lullaby.cherubmod.event.HangedOneAbilityEvents;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

public final class ArchangelAbilityRegistry {
    private static final Map<Archangel, IArchangelActiveAbility> ABILITIES = new EnumMap<>(Archangel.class);

    private ArchangelAbilityRegistry() {}

    public static void registerDefaults() {
        register(Archangel.THE_HANGED_ONE, HangedOneAbilityEvents::activateAbility);
    }

    public static void register(Archangel archangel, IArchangelActiveAbility ability) {
        if (archangel != null && ability != null) {
            ABILITIES.put(archangel, ability);
        }
    }

    public static boolean hasActiveAbility(@Nullable Archangel archangel) {
        return archangel != null && ABILITIES.containsKey(archangel);
    }

    public static void trigger(ServerPlayerEntity player, IRaceData data) {
        IArchangelActiveAbility ability = ABILITIES.get(data.getArchangel());
        if (ability != null) {
            ability.activate(player, data);
        }
    }
}