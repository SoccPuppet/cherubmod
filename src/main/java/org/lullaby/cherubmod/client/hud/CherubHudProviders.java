package org.lullaby.cherubmod.client.hud;

import net.minecraft.util.text.TextFormatting;
import org.lullaby.cherubmod.capability.Archangel;

public final class CherubHudProviders {
    private CherubHudProviders() {}

    public static void registerDefaults() {
        // Strength (VIII): bold gold when main hand is empty (punch active), regular otherwise
        CherubHudRegistry.register(Archangel.STRENGTH, (player, data, state) -> {
            if (player.getMainHandItem().isEmpty()) {
                state.style(TextFormatting.GOLD, TextFormatting.BOLD);
            }

            /*
             * Future example — ramping damage reduction charge bar:
             *
             * float rampProgress = StrengthClientState.getDamageReductionProgress(); // 0.0F..1.0F
             * if (rampProgress > 0.0F) {
             *     // Uses vanilla green->yellow->red durability color:
             *     state.bar(rampProgress);
             *     // Or supply a custom color (e.g. gold/amber):
             *     // state.bar(rampProgress, 0xFFAA00);
             * }
             */
        });
    }
}