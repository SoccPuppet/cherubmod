package org.lullaby.cherubmod.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.event.MoonAbilityEvents;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class MoonClientEvents {

    private static boolean isNotLocalMoonCherub() {
        PlayerEntity player = Minecraft.getInstance().player;
        return player == null || !MoonAbilityEvents.isMoonCherub(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onFogDensity(EntityViewRenderEvent.FogDensity event) {
        if (isNotLocalMoonCherub()) {
            return;
        }
        event.setDensity(0.0F);
        event.setCanceled(true);
    }

    // Thanks vanilla shader rewrite
    @SuppressWarnings("deprecation")
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderFog(EntityViewRenderEvent.RenderFogEvent event) {
        if (isNotLocalMoonCherub()) {
            return;
        }
        // Push linear fog bounds far beyond render distance so even if vanilla/Embeddium
        // re-enables GL_FOG mid-frame, fog factor evaluates to 0.0 everywhere
        RenderSystem.fogMode(GlStateManager.FogMode.LINEAR);
        RenderSystem.fogStart(1_000_000.0F);
        RenderSystem.fogEnd(2_000_000.0F);
        RenderSystem.fogDensity(0.0F);
        RenderSystem.disableFog();
    }
}