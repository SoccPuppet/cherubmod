package org.lullaby.cherubmod.client.hud;

import net.minecraft.util.text.TextFormatting;
import org.lullaby.cherubmod.capability.Archangel;
import org.lullaby.cherubmod.event.MoonAbilityEvents;

public final class CherubHudProviders {
    private CherubHudProviders() {}

    public static void registerDefaults() {
        // Strength: bold gold when main hand is empty (punch active), regular otherwise
        CherubHudRegistry.register(Archangel.STRENGTH, (player, data, state) -> {
            if (player.getMainHandItem().isEmpty()) {
                state.style(TextFormatting.GOLD, TextFormatting.BOLD);
            }
        });
        // The Moon: Gray when under the sky, regular otherwise
        CherubHudRegistry.register(Archangel.THE_MOON, (player, data, state) -> {
            if (MoonAbilityEvents.isExposedToDaytimeSky(player)) {
                state.style(TextFormatting.DARK_GRAY);
            } else {
                state.style(TextFormatting.GOLD, TextFormatting.BOLD);
            }
        });
    }
}