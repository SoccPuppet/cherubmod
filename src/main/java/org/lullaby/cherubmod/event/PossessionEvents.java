package org.lullaby.cherubmod.event;

import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.item.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.item.VapeItem;
import org.lullaby.cherubmod.network.CherubNetwork;
import org.lullaby.cherubmod.possession.PossessionHelper;

import java.util.Iterator;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PossessionEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level.isClientSide) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) event.player;

        // 1. Delete possession if moved into any non-player-inventory slot (crafting, furnace, chest, etc.)
        if (player.containerMenu != null) {
            for (Slot slot : player.containerMenu.slots) {
                if (slot.container != player.inventory && PossessionHelper.isPossessionStack(slot.getItem())) {
                    ItemStack moved = slot.getItem();
                    player.getCapability(RaceCapability.INSTANCE).ifPresent(data ->
                            data.setPossession(PossessionHelper.stripPossessionTag(moved))
                    );
                    slot.set(ItemStack.EMPTY);
                    player.containerMenu.broadcastChanges();
                    CherubNetwork.sendPossessionParticles(player, false);
                }
            }
        }

        // 2. Sync live durability/NBT and try returning hovering items when a slot opens
        player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
            PossessionHelper.syncLivePossessionFromInventory(player, data);

            if (!data.getHoveringItems().isEmpty()) {
                boolean changed = false;
                Iterator<ItemStack> it = data.getHoveringItems().iterator();
                while (it.hasNext()) {
                    ItemStack hovering = it.next();
                    if (player.inventory.add(hovering)) {
                        it.remove();
                        changed = true;
                    } else {
                        break; // Keep order; wait until another slot opens
                    }
                }
                if (changed) {
                    CherubNetwork.syncToTrackingAndSelf(player);
                }
            }
        });
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ItemStack stack = event.getEntityItem().getItem();
        if (PossessionHelper.isPossessionStack(stack)) {
            PlayerEntity player = event.getPlayer();
            if (player instanceof ServerPlayerEntity) {
                ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
                serverPlayer.getCapability(RaceCapability.INSTANCE).ifPresent(data ->
                        data.setPossession(PossessionHelper.stripPossessionTag(stack))
                );
                CherubNetwork.sendPossessionParticles(serverPlayer, false);
            }
            event.getEntityItem().remove();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        // Catch-all: prevent a possession stack from ever existing as a dropped ItemEntity
        if (!event.getWorld().isClientSide && event.getEntity() instanceof ItemEntity) {
            ItemEntity itemEntity = (ItemEntity) event.getEntity();
            if (PossessionHelper.isPossessionStack(itemEntity.getItem())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        boolean dismissed = false;

        for (Slot slot : event.getContainer().slots) {
            if (slot.container == player.inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (PossessionHelper.isPossessionStack(stack)) {
                player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
                    if (PossessionHelper.isCurrentValidCopy(stack, player, data)) {
                        data.setPossession(PossessionHelper.stripPossessionTag(stack));
                    }
                });
                slot.set(ItemStack.EMPTY);
                dismissed = true;
            }
        }

        if (dismissed) {
            CherubNetwork.syncToTrackingAndSelf(player);
            CherubNetwork.sendPossessionParticles(player, false);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // Delete possession if placed onto an item frame or armor stand
        if (event.getTarget() instanceof ItemFrameEntity || event.getTarget() instanceof ArmorStandEntity) {
            ItemStack held = event.getItemStack();
            if (PossessionHelper.isPossessionStack(held)) {
                if (!event.getWorld().isClientSide) {
                    event.getPlayer().getCapability(RaceCapability.INSTANCE).ifPresent(data ->
                            data.setPossession(PossessionHelper.stripPossessionTag(held))
                    );
                    event.getPlayer().setItemInHand(event.getHand(), ItemStack.EMPTY);
                    ServerPlayerEntity serverPlayer =  (ServerPlayerEntity) event.getPlayer();
                    CherubNetwork.sendPossessionParticles(serverPlayer, false);
                }
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayerEntity && event.getPlayer() instanceof ServerPlayerEntity) {
            CherubNetwork.syncTargetToViewer(
                    (ServerPlayerEntity) event.getTarget(),
                    (ServerPlayerEntity) event.getPlayer()
            );
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() == Hand.MAIN_HAND
                && event.getPlayer().getOffhandItem().getItem() instanceof VapeItem) {
            event.setCanceled(true);
            event.setCancellationResult(ActionResultType.PASS);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == Hand.MAIN_HAND
                && event.getPlayer().getOffhandItem().getItem() instanceof VapeItem) {
            // Prevents main-hand tools/blocks (shovels, axes, flint & steel, etc.) from intercepting
            // while still allow opening chests, doors, and buttons normally.
            event.setUseItem(Event.Result.DENY);
        }
    }
}