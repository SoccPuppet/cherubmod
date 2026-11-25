package org.lullaby.cherubmod.possession;

import com.google.common.collect.Multimap;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Hand;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import org.lullaby.cherubmod.capability.IRaceData;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.config.CherubServerConfig;
import org.lullaby.cherubmod.network.CherubNetwork;

public class PossessionHelper {
    public static final String TAG_POSSESSION = "cherubmod_possession";
    public static final String TAG_OWNER = "cherubmod_possession_owner";
    public static final String TAG_GEN = "cherubmod_possession_gen";

    public static boolean isPossessionStack(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) return false;
        if (stack.getTag() == null) return false;
        return stack.getTag().getBoolean(TAG_POSSESSION);
    }

    public static boolean isCurrentValidCopy(ItemStack stack, PlayerEntity player, IRaceData data) {
        if (!isPossessionStack(stack)) {
            return false;
        }
        CompoundNBT tag = stack.getTag();
        return tag != null
                && tag.hasUUID(TAG_OWNER)
                && tag.getUUID(TAG_OWNER).equals(player.getUUID())
                && tag.getLong(TAG_GEN) == data.getPossessionGeneration();
    }

    public static ItemStack createSummonedCopy(ItemStack stored, PlayerEntity owner, long generation) {
        ItemStack copy = stored.copy();
        CompoundNBT tag = copy.getOrCreateTag();
        tag.putBoolean(TAG_POSSESSION, true);
        tag.putUUID(TAG_OWNER, owner.getUUID());
        tag.putLong(TAG_GEN, generation);
        return copy;
    }

    public static ItemStack stripPossessionTag(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.removeTagKey(TAG_POSSESSION);
        copy.removeTagKey(TAG_OWNER);
        copy.removeTagKey(TAG_GEN);
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
        // 1. Broad rules
        if (stack.isEmpty() || stack.getCount() != 1 || isPossessionStack(stack) || stack.getMaxStackSize() != 1) {
            return false;
        }

        Item item = stack.getItem();
        // 2. Blacklist pass
        if (CherubServerConfig.isInvalidPossession(item)) {
            return false;
        }
        // 3. Whitelist pass
        if (CherubServerConfig.isValidPossession(item)) {
            return true;
        }
        // 4. Problematic items (storage, consumable)
        if (item instanceof BlockItem
                || item instanceof ArmorItem
                || item instanceof BucketItem
                || item instanceof PotionItem
                || item instanceof MilkBucketItem
                || item instanceof SpawnEggItem
                || item instanceof BoatItem
                || item instanceof MinecartItem
                || item instanceof ArmorStandItem
                || item instanceof HangingEntityItem
                || item.isEdible()) {
            return false;
        }
        if (stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).isPresent()
                || stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY).isPresent()) {
            return false;
        }

        // 5. They look like they're good
        if (item instanceof TieredItem
                || item instanceof ShootableItem
                || item instanceof TridentItem
                || item instanceof ShieldItem
                || item instanceof ShearsItem
                || item instanceof FishingRodItem
                || item instanceof FlintAndSteelItem
                || item instanceof OnAStickItem
                || item instanceof ElytraItem) {
            return true;
        }
        if (!stack.getToolTypes().isEmpty()) {
            return true;
        }
        Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlotType.MAINHAND);
        if (modifiers.containsKey(Attributes.ATTACK_DAMAGE)
                || modifiers.containsKey(ForgeMod.REACH_DISTANCE.get())) {
            return true;
        }
        if (stack.getDestroySpeed(Blocks.STONE.defaultBlockState()) > 1.0F
                || stack.getDestroySpeed(Blocks.DIRT.defaultBlockState()) > 1.0F
                || stack.getDestroySpeed(Blocks.OAK_PLANKS.defaultBlockState()) > 1.0F) {
            return true;
        }
        if (stack.isDamageableItem()) {
            return true;
        }
        if (stack.getCapability(CapabilityEnergy.ENERGY).isPresent()) {
            return true;
        }

        return EnchantmentType.WEAPON.canEnchant(item)
                || EnchantmentType.DIGGER.canEnchant(item)
                || EnchantmentType.BOW.canEnchant(item)
                || EnchantmentType.CROSSBOW.canEnchant(item)
                || EnchantmentType.TRIDENT.canEnchant(item)
                || EnchantmentType.FISHING_ROD.canEnchant(item);
    }

    public static boolean designateMainHand(ServerPlayerEntity player) {
        ItemStack mainHand = player.getMainHandItem();

        if (!isValidForDesignation(mainHand)) {
            player.displayClientMessage(
                    new TranslationTextComponent("cherubmod.invalidpossession"),
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
            long nextGen = data.incrementPossessionGeneration();
            player.setItemInHand(Hand.MAIN_HAND, createSummonedCopy(cleanNew, player, nextGen));

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
        // Prevent summoning/dismissing while a workstation GUI is open
        if (player.containerMenu != player.inventoryMenu) {
            return;
        }
        player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
            if (data.getRace() != Race.CHERUB || data.getPossession().isEmpty()) {
                return;
            }

            ItemStack mainHand = player.getMainHandItem();

            // Pressing keybind while holding the possession dismisses it
            if (isPossessionStack(mainHand)) {
                if (isCurrentValidCopy(mainHand, player, data)) {
                    data.setPossession(stripPossessionTag(mainHand));
                }
                removeAllPossessionStacks(player);
                CherubNetwork.syncToTrackingAndSelf(player);
                CherubNetwork.sendPossessionParticles(player, false);
                return;
            }

            syncLivePossessionFromInventory(player, data);
            removeAllPossessionStacks(player);

            long nextGen = data.incrementPossessionGeneration();

            ItemStack displaced = mainHand.copy();
            player.setItemInHand(Hand.MAIN_HAND, createSummonedCopy(data.getPossession(), player, nextGen));

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
            if (isCurrentValidCopy(stack, player, data)) {
                data.setPossession(stripPossessionTag(stack));
                return;
            }
        }
        ItemStack carried = player.inventory.getCarried();
        if (isCurrentValidCopy(carried, player, data)) {
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