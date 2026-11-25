package org.lullaby.cherubmod.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
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
import org.lullaby.cherubmod.item.ModItems;
import org.lullaby.cherubmod.network.CherubNetwork;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RaceEvent {

    private static final String TAG_RECEIVED_STARTER_VAPE = "cherubmod_received_starter_vape";
    private static final String TAG_PENDING_TAROT_DECK = "cherubmod_pending_tarot_deck";
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
                            if (clone instanceof ServerPlayerEntity) {
                                getPersistedTag(clone).putBoolean(TAG_PENDING_TAROT_DECK, true);
                            }
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
            ServerPlayerEntity player = (ServerPlayerEntity)event.getPlayer();
            CherubNetwork.syncToTrackingAndSelf(player);
            CompoundNBT persisted = getPersistedTag(player);
            if (!persisted.getBoolean(TAG_RECEIVED_STARTER_VAPE)) {
                persisted.putBoolean(TAG_RECEIVED_STARTER_VAPE, true);
                giveOrDrop(player, new ItemStack(ModItems.VAPE.get()));
            }
            deliverPendingTarotDeck(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
            CherubNetwork.syncToTrackingAndSelf(player);
            deliverPendingTarotDeck(player);
        }
    }

    private static void deliverPendingTarotDeck(ServerPlayerEntity player) {
        CompoundNBT persisted = getPersistedTag(player);
        if (persisted.getBoolean(TAG_PENDING_TAROT_DECK)) {
            persisted.remove(TAG_PENDING_TAROT_DECK);
            giveOrDrop(player, new ItemStack(ModItems.TAROT_DECK.get()));
        }
    }

    private static CompoundNBT getPersistedTag(PlayerEntity player) {
        CompoundNBT entityData = player.getPersistentData();
        CompoundNBT persisted = entityData.getCompound(PlayerEntity.PERSISTED_NBT_TAG);
        if (!entityData.contains(PlayerEntity.PERSISTED_NBT_TAG)) {
            entityData.put(PlayerEntity.PERSISTED_NBT_TAG, persisted);
        }
        return persisted;
    }

    public static void giveOrDrop(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack toGive = stack.copy();
        boolean added = player.inventory.add(toGive);
        if (!added || !toGive.isEmpty()) {
            ItemEntity dropped = new ItemEntity(
                    player.level,
                    player.getX(),
                    player.getY() + 0.25D,
                    player.getZ(),
                    toGive
            );
            dropped.setDeltaMovement(0.0D, 0.0D, 0.0D);
            dropped.setNoPickUpDelay();
            player.level.addFreshEntity(dropped);
        }
        player.inventoryMenu.broadcastChanges();
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
