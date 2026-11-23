package org.lullaby.cherubmod.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.GameRules;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
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
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if(!(event.getEntity() instanceof PlayerEntity)) return;
        PlayerEntity player = (PlayerEntity)event.getEntity();
        player.getCapability(RaceCapability.INSTANCE).ifPresent(cap -> {
            if(cap.isPendingRaceChange() || cap.getRace() == Race.CHERUB){
                event.setDroppedExperience(0);
                event.setCanceled(true);
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        PlayerEntity original = event.getOriginal();
        PlayerEntity clone = event.getPlayer();

        original.revive();
        original.getCapability(RaceCapability.INSTANCE).ifPresent(oldData ->
                clone.getCapability(RaceCapability.INSTANCE).ifPresent(newData -> {
                    newData.copyFrom(oldData);

                    if (event.isWasDeath()) {
                        if(newData.isPendingRaceChange()) {
                            newData.setRace(Race.CHERUB);
                            newData.setPendingRaceChange(false);
                            applyCherubDeathKeepInventory(original, clone);
                        }
                        if(oldData.getRace() == Race.CHERUB){
                            applyCherubDeathKeepInventory(original, clone);
                        }
                    }
                })
        );
        original.remove(false);
    }
    private static void applyCherubDeathKeepInventory(PlayerEntity original, PlayerEntity clone){
        boolean isKeepInventory = clone.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        if (!isKeepInventory && !original.isSpectator()) {
            clone.inventory.replaceWith(original.inventory);
            clone.experienceLevel = original.experienceLevel;
            clone.totalExperience = original.totalExperience;
            clone.experienceProgress = original.experienceProgress;
            clone.setScore(original.getScore());
        }

        if(clone.experienceLevel > 0){
            clone.giveExperienceLevels(-1);
        } else {
            clone.experienceProgress = 0;
            clone.totalExperience = 0;
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            CherubNetwork.syncToTrackingAndSelf((ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
            CherubNetwork.syncToTrackingAndSelf(player);

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
            CherubNetwork.syncToTrackingAndSelf((ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SwitchedCommand.register(event.getDispatcher());
    }
}
