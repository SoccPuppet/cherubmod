package org.lullaby.cherubmod.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.command.SwitchedCommand;
import org.lullaby.cherubmod.network.CherubNetwork;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RaceEvent {
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof PlayerEntity) {
            RaceCapability.Provider provider = new RaceCapability.Provider();
            event.addCapability(RaceCapability.ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        PlayerEntity original = event.getOriginal();
        PlayerEntity clone = event.getPlayer();

        original.revive();
        original.getCapability(RaceCapability.INSTANCE).ifPresent(oldData ->
                clone.getCapability(RaceCapability.INSTANCE).ifPresent(newData -> {
                    newData.copyFrom(oldData);

                    if (event.isWasDeath() && newData.isPendingRaceChange()) {
                        newData.setRace(Race.CHERUB);
                        newData.setPendingRaceChange(false);
                    }
                })
        );
        original.remove(false);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            CherubNetwork.syncToPlayer((ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
            CherubNetwork.syncToPlayer(player);

            // Helpful verification for testing /switched on
            player.getCapability(RaceCapability.INSTANCE).ifPresent(data ->
                    player.sendMessage(
                            new StringTextComponent("Respawned as race: " + data.getRace().name()),
                            Util.NIL_UUID
                    )
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            CherubNetwork.syncToPlayer((ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SwitchedCommand.register(event.getDispatcher());
    }
}
