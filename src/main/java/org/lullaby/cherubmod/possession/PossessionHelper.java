package org.lullaby.cherubmod.possession;

import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.text.StringTextComponent;
import org.lullaby.cherubmod.capability.IRaceData;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.network.CherubNetwork;

public class PossessionHelper {
    public static final String TAG_POSSESSION = "cherubmod_possession";

    public static boolean isPossessionStack(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) return false;
        if (stack.getTag() == null) return false;
        return stack.getTag().getBoolean(TAG_POSSESSION);
    }

    public static ItemStack createSummonedCopy(ItemStack stored) {
        ItemStack copy = stored.copy();
        copy.getOrCreateTag().putBoolean(TAG_POSSESSION, true);
        return copy;
    }

    public static ItemStack stripPossessionTag(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.removeTagKey(TAG_POSSESSION);
        if (copy.hasTag()) {
            assert copy.getTag() != null;
            if (copy.getTag().isEmpty()) {
                copy.setTag(null);
            }
        }
        return copy;
    }

    public static void ejectPossession(ServerPlayerEntity player, IRaceData data) {
        if (data.getPossession().isEmpty()) {
            return;
        }

        syncLivePossessionFromInventory(player, data);
        removeAllPossessionStacks(player);

        ItemStack oldItem = stripPossessionTag(data.getPossession());
        ItemEntity dropped = new ItemEntity(
                player.level,
                player.getX(),
                player.getY() + 0.1D,
                player.getZ(),
                oldItem
        );
        dropped.setDeltaMovement(0.0D, 0.0D, 0.0D);
        dropped.setPickUpDelay(20);
        player.level.addFreshEntity(dropped);

        data.setPossession(ItemStack.EMPTY);
    }

    public static boolean isValidForDesignation(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() == 1 && !isPossessionStack(stack);
    }

    public static boolean designateMainHand(ServerPlayerEntity player) {
        ItemStack mainHand = player.getMainHandItem();

        if (!isValidForDesignation(mainHand)) {
            player.displayClientMessage(
                    new StringTextComponent("You must designate your possession, an unstackable item."),
                    true
            );
            return false;
        }

        player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
            // 1. Eject existing possession beneath the player if one exists
            ejectPossession(player, data);

            // 2. Bind the new main-hand item
            ItemStack cleanNew = stripPossessionTag(mainHand);
            data.setPossession(cleanNew);
            player.setItemInHand(Hand.MAIN_HAND, createSummonedCopy(cleanNew));

            CherubNetwork.syncToTrackingAndSelf(player);
            player.displayClientMessage(
                    new StringTextComponent("Designated " + cleanNew.getHoverName().getString() + " as your possession."),
                    true
            );
        });
        return true;
    }

    /**
     * Handles pressing the summon/dismiss keybind (default X).
     */
    public static void toggleSummon(ServerPlayerEntity player) {
        player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
            if (data.getRace() != Race.CHERUB || data.getPossession().isEmpty()) {
                return;
            }

            ItemStack mainHand = player.getMainHandItem();

            // Pressing keybind while holding the possession dismisses it
            if (isPossessionStack(mainHand)) {
                data.setPossession(stripPossessionTag(mainHand));
                player.setItemInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                CherubNetwork.syncToTrackingAndSelf(player);
                CherubNetwork.sendPossessionParticles(player, false);
                return;
            }

            // If the possession is already elsewhere in the inventory, sync and remove it first
            syncLivePossessionFromInventory(player, data);
            removeAllPossessionStacks(player);

            // Displace current main-hand item
            ItemStack displaced = mainHand.copy();
            player.setItemInHand(Hand.MAIN_HAND, createSummonedCopy(data.getPossession()));

            if (!displaced.isEmpty()) {
                if (!player.inventory.add(displaced)) {
                    // Inventory full: store as hovering player data
                    data.getHoveringItems().add(displaced);
                }
            }

            CherubNetwork.syncToTrackingAndSelf(player);
            CherubNetwork.sendPossessionParticles(player, true);
        });
    }

    public static void syncLivePossessionFromInventory(PlayerEntity player, IRaceData data) {
        for (int i = 0; i < player.inventory.getContainerSize(); i++) {
            ItemStack stack = player.inventory.getItem(i);
            if (isPossessionStack(stack)) {
                data.setPossession(stripPossessionTag(stack));
                return;
            }
        }
        ItemStack carried = player.inventory.getCarried();
        if (isPossessionStack(carried)) {
            data.setPossession(stripPossessionTag(carried));
        }
    }

    public static void removeAllPossessionStacks(PlayerEntity player) {
        for (int i = 0; i < player.inventory.getContainerSize(); i++) {
            if (isPossessionStack(player.inventory.getItem(i))) {
                player.inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        if (isPossessionStack(player.inventory.getCarried())) {
            player.inventory.setCarried(ItemStack.EMPTY);
        }
    }
}