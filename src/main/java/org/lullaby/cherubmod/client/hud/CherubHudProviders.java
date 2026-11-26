package org.lullaby.cherubmod.client.hud;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.text.TextFormatting;
import org.lullaby.cherubmod.capability.Archangel;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.event.MoonAbilityEvents;
import org.lullaby.cherubmod.event.WorldAbilityEvents;

public final class CherubHudProviders {
    private CherubHudProviders() {}
    private static final ItemStack PLUSH_DEFAULT_ICON = new ItemStack(Items.WHITE_WOOL);

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
        // Judgement: Golden when totem is charged
        CherubHudRegistry.register(Archangel.JUDGEMENT, (player, data, state) -> {
            if (data.isJudgementTotemCharged()) {
                state.style(TextFormatting.GOLD);
            }
        });
        // The Hanged One: Golden when ability is active
        CherubHudRegistry.register(Archangel.THE_HANGED_ONE, (player, data, state) -> {
            if (data.isHangedOneActive()) {
                state.style(TextFormatting.GOLD);
            }
        });
        // The World: Golden while stasis, bar indicates cooldown
        CherubHudRegistry.register(Archangel.THE_WORLD, (player, data, state) -> {
            int stasisTicks = data.getWorldStasisTicks();
            if (stasisTicks > 0) {
                state.style(TextFormatting.GOLD, TextFormatting.BOLD);
                state.bar(
                        stasisTicks / (float) WorldAbilityEvents.STASIS_MAX_TICKS,
                        0xFFAA00
                );
                return;
            }

            int cooldownTicks = data.getWorldStasisCooldownTicks();
            if (cooldownTicks > 0) {
                state.style(TextFormatting.DARK_GRAY);
                float progress = 1.0F - (cooldownTicks / (float) WorldAbilityEvents.STASIS_COOLDOWN_TICKS);
                state.bar(progress, 0x7A7A7A);
            }
        });
        CherubHudRegistry.register(Race.PLUSH, (player, data, state) -> state.icon(PLUSH_DEFAULT_ICON));
    }
}