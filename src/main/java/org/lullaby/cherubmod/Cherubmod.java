package org.lullaby.cherubmod;

import net.minecraft.block.Block;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lullaby.cherubmod.ability.ArchangelAbilityRegistry;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.client.hud.CherubHudProviders;
import org.lullaby.cherubmod.config.CherubServerConfig;
import org.lullaby.cherubmod.item.ModItems;
import org.lullaby.cherubmod.network.CherubNetwork;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Cherubmod.MOD_ID)
public class Cherubmod {
    public static final String MOD_ID = "cherubmod";

    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();

    public Cherubmod() {
        ForgeMod.enableMilkFluid();
        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        // Register the doClientStuff method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CherubServerConfig.SPEC);
        modBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(CherubHudProviders::registerDefaults);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Greetings from the Heavenly Disc");
    }
    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            RaceCapability.register();
            CherubNetwork.register();
            ArchangelAbilityRegistry.registerDefaults();
        });
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        // do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically subscribe events on the contained class (this is subscribing to the MOD
    // Event bus for receiving Registry Events)
    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {
        @SubscribeEvent
        public static void onBlocksRegistry(final RegistryEvent.Register<Block> blockRegistryEvent) {
            // register a new block here
        }
    }
}
