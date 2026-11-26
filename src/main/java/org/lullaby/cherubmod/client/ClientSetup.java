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
    public static final KeyBinding SUMMON_POSSESSION = new KeyBinding(
            "key.cherubmod.summon_possession",
            GLFW.GLFW_KEY_X,
            "key.categories.cherubmod"
    );
    public static final KeyBinding ACTIVE_ABILITY = new KeyBinding(
            "key.cherubmod.active_ability",
            GLFW.GLFW_KEY_C,
            "key.categories.cherubmod"
    );

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientRegistry.registerKeyBinding(SUMMON_POSSESSION);
        ClientRegistry.registerKeyBinding(ACTIVE_ABILITY);
    }
}