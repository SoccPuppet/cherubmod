package org.lullaby.cherubmod.client.hud;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.text.TextFormatting;
import org.lullaby.cherubmod.ability.ArchangelAbilityRegistry;
import org.lullaby.cherubmod.capability.Archangel;
import org.lullaby.cherubmod.capability.IRaceData;
import org.lullaby.cherubmod.tarot.TarotArcana;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CherubHudRegistry {
    private static final Map<Archangel, ICherubHudProvider> ARCHANGEL_PROVIDERS = new EnumMap<>(Archangel.class);
    private static final List<ICherubHudProvider> GLOBAL_MODIFIERS = new CopyOnWriteArrayList<>();

    private CherubHudRegistry() {}

    /** Register (or replace) the HUD provider for a specific Archangel. */
    public static void register(Archangel archangel, ICherubHudProvider provider) {
        if (archangel != null && provider != null) {
            ARCHANGEL_PROVIDERS.put(archangel, provider);
        }
    }

    /** Register an external modifier that runs after the active Archangel's provider. */
    public static void registerModifier(ICherubHudProvider modifier) {
        if (modifier != null) {
            GLOBAL_MODIFIERS.add(modifier);
        }
    }

    public static CherubHudState resolve(PlayerEntity player, IRaceData data) {
        Archangel archangel = data.getArchangel();
        CherubHudState.Builder builder = createBaseState(archangel);

        ICherubHudProvider provider = ARCHANGEL_PROVIDERS.get(archangel);
        if (provider != null) {
            provider.apply(player, data, builder);
        }

        for (ICherubHudProvider modifier : GLOBAL_MODIFIERS) {
            modifier.apply(player, data, builder);
        }

        return builder.build();
    }

    private static CherubHudState.Builder createBaseState(Archangel archangel) {
        if (archangel == null || archangel == Archangel.NONE) {
            // Subtle placeholder when the player is a Cherub without an assigned Archangel
            return CherubHudState.builder("-").style(TextFormatting.GRAY);
        }

        TarotArcana arcana = TarotArcana.fromArchangel(archangel);
        String numeral = arcana != null ? arcana.getRomanNumeral() : "?";
        return CherubHudState.builder(numeral)
                .showActiveKeybind(ArchangelAbilityRegistry.hasActiveAbility(archangel));
    }
}