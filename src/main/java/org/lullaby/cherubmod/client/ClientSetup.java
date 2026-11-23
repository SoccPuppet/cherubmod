package org.lullaby.cherubmod.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import org.lullaby.cherubmod.Cherubmod;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    public static KeyBinding SUMMON_POSSESSION;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SUMMON_POSSESSION = new KeyBinding(
                "key.cherubmod.summon_possession",
                GLFW.GLFW_KEY_X,
                "key.categories.cherubmod"
        );
        ClientRegistry.registerKeyBinding(SUMMON_POSSESSION);
    }
}